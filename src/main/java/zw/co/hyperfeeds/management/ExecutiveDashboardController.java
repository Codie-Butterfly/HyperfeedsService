package zw.co.hyperfeeds.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.co.hyperfeeds.identity.CurrentUser;
import java.math.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/management/executive")
@PreAuthorize("hasAnyRole('CEO','ADMIN','MAIN_MANAGER')")
public class ExecutiveDashboardController {
    private final JdbcClient jdbc;
    public ExecutiveDashboardController(JdbcClient jdbc){this.jdbc=jdbc;}
    private static final String SALES="""
        with sales as (
          select id,branch_id,total net,discount_amount discount,created_at,'PRODUCT' kind
          from orders where status in ('PAID','COLLECTED') and trim(currency)=:currency
          union all
          select cb.id,coalesce(cb.pickup_branch_id,b.branch_id),cb.total_amount,0,cb.created_at,'CHICK'
          from chick_bookings cb left join chick_batches b on b.id=cb.batch_id where cb.status in ('CONFIRMED','COLLECTED') and trim(cb.currency)=:currency
        ), selected as (
          select * from sales where created_at>=:start and created_at<:end
          and (cast(:branch as uuid) is null or branch_id=:branch)
        )
        """;
    private JdbcClient.StatementSpec query(String sql,YearMonth month,String currency,UUID branch){
        var zone=ZoneId.of("Africa/Harare");
        return jdbc.sql(sql).param("start",month.atDay(1).atStartOfDay(zone).toOffsetDateTime())
            .param("end",month.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime())
            .param("month",month.atDay(1)).param("currency",currency).param("branch",branch);
    }
    private YearMonth month(String input){
        try{return YearMonth.parse(input);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use a month in YYYY-MM format");}
    }
    @GetMapping("/options")
    public Map<String,Object> options(){
        return Map.of("branches",jdbc.sql("select id,name from branches order by name").query().listOfRows(),
            "currencies",jdbc.sql("select distinct trim(currency) currency from branch_prices union select distinct trim(currency) from chick_breed_configs union select distinct trim(currency) from orders union select distinct trim(currency) from sales_targets order by currency").query(String.class).list());
    }
    @GetMapping
    public Map<String,Object> dashboard(@RequestParam String month,@RequestParam String currency,@RequestParam(required=false) UUID branchId){
        YearMonth period=month(month);
        if(!currency.matches("[A-Z]{3}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select a currency");
        var summary=new LinkedHashMap<>(query(SALES+"""
            select coalesce(sum(net),0) net_sales,coalesce(sum(discount),0) discounts,count(*) sale_count,
            coalesce(sum(net) filter(where kind='PRODUCT'),0) product_sales,
            coalesce(sum(net) filter(where kind='CHICK'),0) chick_sales from selected
            """,period,currency,branchId).query().singleRow());
        var target=query("select amount from sales_targets where target_month=:month and currency=:currency and scope_key=coalesce(cast(:branch as text),'COMPANY')",period,currency,branchId).query(BigDecimal.class).optional().orElse(null);
        LocalDate today=LocalDate.now(ZoneId.of("Africa/Harare"));
        int elapsed=today.isBefore(period.atDay(1))?0:today.isAfter(period.atEndOfMonth())?period.lengthOfMonth():today.getDayOfMonth();
        BigDecimal actual=(BigDecimal)summary.get("net_sales");
        BigDecimal expected=target==null?null:target.multiply(BigDecimal.valueOf(elapsed)).divide(BigDecimal.valueOf(period.lengthOfMonth()),2,RoundingMode.HALF_UP);
        summary.put("target",target);summary.put("expected_to_date",expected);
        summary.put("pace_gap",expected==null?null:actual.subtract(expected));
        summary.put("remaining_to_target",target==null?null:target.subtract(actual).max(BigDecimal.ZERO));
        summary.put("achievement_percent",target==null?null:actual.multiply(BigDecimal.valueOf(100)).divide(target,1,RoundingMode.HALF_UP));
        summary.put("days_elapsed",elapsed);summary.put("days_in_month",period.lengthOfMonth());
        summary.put("cash_received",query("""
            select coalesce(sum(p.amount),0) from payments p
            left join orders o on o.id=p.order_id left join chick_bookings cb on cb.id=p.chick_booking_id
            where p.status='PAID' and trim(p.currency)=:currency and p.created_at>=:start and p.created_at<:end
            and (cast(:branch as uuid) is null or coalesce(o.branch_id,cb.pickup_branch_id)=:branch)
            """,period,currency,branchId).query(BigDecimal.class).single());
        var daily=query(SALES+"""
            select (created_at at time zone 'Africa/Harare')::date as "day",sum(net) amount from selected group by 1 order by 1
            """,period,currency,branchId).query().listOfRows();
        var branches=query(SALES+"""
            select b.id,b.name,coalesce(sum(s.net),0) actual,t.amount target
            from branches b left join selected s on s.branch_id=b.id
            left join sales_targets t on t.branch_id=b.id and t.target_month=:month and t.currency=:currency
            where cast(:branch as uuid) is null or b.id=:branch
            group by b.id,b.name,t.amount order by actual desc,b.name
            """,period,currency,branchId).query().listOfRows();
        var products=query("""
            with sold as (
              select i.product_id,sum(i.quantity) quantity,
              sum(case when coalesce(o.subtotal,o.total)>0 then i.line_total*o.total/coalesce(o.subtotal,o.total) else 0 end) net
              from order_items i join orders o on o.id=i.order_id
              where o.status in ('PAID','COLLECTED') and trim(o.currency)=:currency and o.created_at>=:start and o.created_at<:end
              and (cast(:branch as uuid) is null or o.branch_id=:branch) group by i.product_id
            )
            select p.id,p.name,p.sku,p.pack_size,coalesce(s.quantity,0) quantity,round(coalesce(s.net,0),2) net_sales
            from products p left join sold s on s.product_id=p.id
            where s.product_id is not null or (p.published and exists(select 1 from branch_prices bp where bp.product_id=p.id and trim(bp.currency)=:currency and (cast(:branch as uuid) is null or bp.branch_id=:branch)))
            order by quantity desc,p.name
            """,period,currency,branchId).query().listOfRows();
        var inventory=query("""
            select b.name branch_name,p.name product_name,p.sku,p.pack_size,i.on_hand,i.reserved,
            i.on_hand-i.reserved available,i.low_stock_threshold,
            (i.on_hand-i.reserved<=i.low_stock_threshold) low_stock
            from branch_inventory i join branches b on b.id=i.branch_id join products p on p.id=i.product_id
            where cast(:branch as uuid) is null or i.branch_id=:branch
            order by low_stock desc,available,b.name,p.name
            """,period,currency,branchId).query().listOfRows();
        var feedStock=query("""
            with sold as (
              select oi.product_id,sum(oi.quantity) sold
              from order_items oi join orders o on o.id=oi.order_id
              where o.status in ('PAID','COLLECTED') and o.created_at>=:start and o.created_at<:end
              and (cast(:branch as uuid) is null or o.branch_id=:branch)
              group by oi.product_id
            ), stock as (
              select product_id,sum(on_hand) remaining from branch_inventory
              where cast(:branch as uuid) is null or branch_id=:branch group by product_id
            )
            select p.id,p.name,p.pack_size,c.name category,
              coalesce(s.sold,0) sold,coalesce(i.remaining,0) remaining
            from products p join product_categories c on c.id=p.category_id
            left join sold s on s.product_id=p.id left join stock i on i.product_id=p.id
            where (p.active or s.product_id is not null or i.product_id is not null)
              and (s.product_id is not null or i.product_id is not null)
              and lower(c.name) like '%feed%'
            order by c.name,p.name,p.pack_size
            """,period,currency,branchId).query().listOfRows();
        var chicks=query("""
            select cb.status,cb.chick_type,cb.breed,count(*) bookings,sum(cb.quantity) chicks,sum(cb.total_amount) value
            from chick_bookings cb where trim(cb.currency)=:currency and cb.created_at>=:start and cb.created_at<:end
            and (cast(:branch as uuid) is null or cb.pickup_branch_id=:branch)
            group by cb.status,cb.chick_type,cb.breed order by cb.status,cb.breed
            """,period,currency,branchId).query().listOfRows();
        return Map.of("feed_stock",feedStock,"summary",summary,"daily",daily,"branches",branches,"products",products,"inventory",inventory,"chicks",chicks,
            "month",month,"currency",currency,"as_of",OffsetDateTime.now().toString());
    }
    @PutMapping("/targets")
    @Transactional
    public void target(Authentication auth,@Valid @RequestBody TargetRequest r){
        LocalDate first=month(r.month()).atDay(1);
        if(r.branchId()!=null && !jdbc.sql("select exists(select 1 from branches where id=:id)").param("id",r.branchId()).query(Boolean.class).single())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Branch not found");
        UUID actor=CurrentUser.id(auth);
        var id=jdbc.sql("""
            insert into sales_targets(branch_id,target_month,currency,amount,updated_by)
            values(:branch,:month,:currency,:amount,:actor)
            on conflict(target_month,currency,scope_key) do update set amount=excluded.amount,updated_by=excluded.updated_by,updated_at=now() returning id
            """).param("branch",r.branchId()).param("month",first).param("currency",r.currency()).param("amount",r.amount()).param("actor",actor).query(UUID.class).single();
        jdbc.sql("insert into audit_events(actor_user_id,action,entity_type,entity_id,details) values(:actor,'SALES_TARGET_SET','SALES_TARGET',:id,jsonb_build_object('amount',cast(:amount as numeric),'currency',cast(:currency as text),'month',cast(:month as text)))")
            .param("actor",actor).param("id",id.toString()).param("amount",r.amount()).param("currency",r.currency()).param("month",first.toString()).update();
    }
    public record TargetRequest(@NotBlank String month,UUID branchId,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
        @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount){}
}
