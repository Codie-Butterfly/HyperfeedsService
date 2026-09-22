package zw.co.hyperfeeds.commerce;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.co.hyperfeeds.bookings.ChickBookingController;
import zw.co.hyperfeeds.identity.CurrentUser;
import java.math.*;
import java.util.*;

@RestController
@RequestMapping("/commerce/walk-in-chick-bookings")
@PreAuthorize("hasAnyRole('BRANCH_MANAGER','CUSTOMER_SERVICE')")
public class WalkInChickBookingController {
    private final JdbcClient jdbc;
    private final ChickBookingController bookings;
    public WalkInChickBookingController(JdbcClient jdbc, ChickBookingController bookings) {
        this.jdbc=jdbc; this.bookings=bookings;
    }
    private boolean restricted(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_BRANCH_MANAGER"));
    }
    private void checkBranch(Authentication auth, UUID branch) {
        boolean allowed=jdbc.sql("""
            select exists(select 1 from branches b where id=:branch and active and collection_enabled
            and (not :restricted or exists(select 1 from employee_branches eb where eb.branch_id=b.id and eb.user_id=:employee)))
            """).param("branch",branch).param("restricted",restricted(auth)).param("employee",CurrentUser.id(auth)).query(Boolean.class).single();
        if(!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch is not available to you");
    }
    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(Authentication auth,@Valid @RequestBody Request r) {
        UUID employee=CurrentUser.id(auth);
        checkBranch(auth,r.branchId());
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key,0))").param("key",r.requestId().toString()).query().singleRow();
        String fingerprint=r.toString();
        var prior=jdbc.sql("select id,created_by,request_fingerprint from chick_bookings where request_id=:request")
            .param("request",r.requestId()).query().listOfRows();
        if(!prior.isEmpty()) {
            var old=prior.get(0);
            if(!employee.equals(old.get("created_by")) || !fingerprint.equals(old.get("request_fingerprint")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"This booking request was already used with different details");
            return invoice(auth,(UUID)old.get("id"));
        }
        if(r.amountPaid().signum()>0 && !r.paymentMethod().equals("CASH") && clean(r.paymentReference()).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter the external payment reference");
        // Reuse the customer booking rules, pricing and open-window checks in this transaction.
        var receipt=bookings.order(auth,new ChickBookingController.OrderRequest(r.branchId(),r.chickType(),r.breed(),r.quantity()));
        BigDecimal minimum=receipt.depositAmount().setScale(2,RoundingMode.HALF_UP);
        if(!receipt.currency().trim().equals(r.currency()) || receipt.totalAmount().compareTo(r.totalAmount())!=0
            || r.amountPaid().compareTo(minimum)<0 || r.amountPaid().compareTo(receipt.totalAmount())>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Check current prices and currency. Payment must cover the configured deposit and must not exceed the total.");
        UUID customer=r.customerId();
        String name,phone;
        if(customer!=null) {
            var found=jdbc.sql("select name,phone_number from walk_in_customers where id=:id").param("id",customer).query().listOfRows();
            if(found.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Customer not found; search again");
            name=(String)found.get(0).get("name"); phone=clean((String)found.get(0).get("phone_number"));
        } else {
            customer=UUID.randomUUID(); name=r.customerName().trim(); phone=clean(r.customerPhone()).replaceAll("\\s+","");
            int inserted=jdbc.sql("insert into walk_in_customers(id,name,phone_number,created_by) values(:id,:name,nullif(:phone,''),:employee) on conflict do nothing")
                .param("id",customer).param("name",name).param("phone",phone).param("employee",employee).update();
            if(inserted==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"A customer with this phone already exists. Search and select them.");
        }
        jdbc.sql("""
            update chick_bookings set user_id=null,sales_channel='WALK_IN',walk_in_customer_id=:customer,
            customer_name=:name,customer_phone=:phone,created_by=:employee,request_id=:request,request_fingerprint=:fingerprint,
            status='CONFIRMED',deposit_amount=:deposit,deposit_payment_method=:method,
            deposit_paid_at=case when :paid>0 then now() else null end where id=:id
            """).param("customer",customer).param("name",name).param("phone",phone).param("employee",employee)
            .param("request",r.requestId()).param("fingerprint",fingerprint).param("deposit",minimum)
            .param("method",r.paymentMethod()).param("paid",r.amountPaid()).param("id",receipt.id()).update();
        if(r.amountPaid().signum()>0) recordPayment(auth,receipt.id(),r.amountPaid(),r.currency(),r.paymentMethod(),r.paymentReference());
        audit(auth,receipt.id(),"WALK_IN_CHICK_BOOKING_CREATED");
        return invoice(auth,receipt.id());
    }
    private void audit(Authentication auth,UUID id,String action) {
        jdbc.sql("insert into audit_events(actor_user_id,action,entity_type,entity_id) values(:user,:action,'CHICK_BOOKING',:id)")
            .param("user",CurrentUser.id(auth)).param("action",action).param("id",id.toString()).update();
    }
    private void recordPayment(Authentication auth,UUID id,BigDecimal amount,String currency,String method,String reference) {
        jdbc.sql("""
            insert into payments(chick_booking_id,provider,status,amount,currency,external_method,external_reference,recorded_by)
            values(:id,'BRANCH','PAID',:amount,:currency,:method,:reference,:user)
            """).param("id",id).param("amount",amount).param("currency",currency).param("method",method)
            .param("reference",clean(reference)).param("user",CurrentUser.id(auth)).update();
    }
    @GetMapping("/{id}/invoice")
    public Map<String,Object> invoice(Authentication auth,@PathVariable UUID id) {
        var rows=jdbc.sql("""
            select cb.id,cb.reference,cb.reference invoice_number,cb.status,cb.created_at,cb.customer_name,cb.customer_phone,
            cb.pickup_branch_id,cb.quantity,cb.breed,cb.chick_type,cb.unit_price,cb.total_amount total,trim(cb.currency) currency,
            cb.delivery_date_snapshot delivery_date,cb.deposit_amount,cb.sales_channel,
            b.name branch_name,b.address branch_address,b.phone_number branch_phone,
            concat(u.first_name,' ',u.last_name) recorded_by,
            coalesce((select sum(p.amount) from payments p where p.chick_booking_id=cb.id and p.status='PAID'),0) amount_paid,
            (select p.external_method from payments p where p.chick_booking_id=cb.id and p.status='PAID' order by p.created_at desc limit 1) payment_method,
            (select p.external_reference from payments p where p.chick_booking_id=cb.id and p.status='PAID' order by p.created_at desc limit 1) payment_reference,
            (select max(p.created_at) from payments p where p.chick_booking_id=cb.id and p.status='PAID') paid_at
            from chick_bookings cb join branches b on b.id=cb.pickup_branch_id join users u on u.id=cb.created_by
            where cb.id=:id and cb.sales_channel='WALK_IN'
            and (not :restricted or exists(select 1 from employee_branches eb where eb.branch_id=b.id and eb.user_id=:user))
            """).param("id",id).param("restricted",restricted(auth)).param("user",CurrentUser.id(auth)).query().listOfRows();
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Walk-in chick booking not found");
        var result=new LinkedHashMap<>(rows.get(0));
        result.put("amount_owed",((BigDecimal)result.get("total")).subtract((BigDecimal)result.get("amount_paid")));
        result.put("items",List.of(Map.of("product_name",result.get("chick_type")+" / "+result.get("breed")+" chicks — collection "+result.get("delivery_date"),
            "quantity",result.get("quantity"),"unit_price",result.get("unit_price"),"line_total",result.get("total"))));
        return result;
    }
    @PostMapping("/{id}/invoice-copies")
    @Transactional
    public Map<String,Object> issueCopy(Authentication auth,@PathVariable UUID id) {
        var result=new LinkedHashMap<>(invoice(auth,id));
        int copy=jdbc.sql("update chick_bookings set invoice_copy_count=invoice_copy_count+1 where id=:id returning invoice_copy_count").param("id",id).query(Integer.class).single();
        result.put("copy_number",copy); result.put("is_reprint",copy>1); result.put("copy_issued_at",java.time.OffsetDateTime.now().toString());
        audit(auth,id,"CHICK_INVOICE_COPY_ISSUED"); return result;
    }
    @PostMapping("/{id}/balance-payment")
    @Transactional
    public Map<String,Object> balancePayment(Authentication auth,@PathVariable UUID id,@Valid @RequestBody BalanceRequest r) {
        jdbc.sql("select id from chick_bookings where id=:id for update").param("id",id).query().listOfRows();
        var booking=invoice(auth,id);
        if(!"CONFIRMED".equals(booking.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only confirmed bookings can receive a balance payment");
        BigDecimal owed=(BigDecimal)booking.get("amount_owed");
        // A repeated full-balance submission cannot record a second payment.
        if(owed.signum()==0) return booking;
        if(owed.compareTo(r.amount())!=0 || !booking.get("currency").equals(r.currency()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment must match the outstanding balance and currency");
        if(!r.paymentMethod().equals("CASH") && clean(r.paymentReference()).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter the external payment reference");
        recordPayment(auth,id,owed,r.currency(),r.paymentMethod(),r.paymentReference());
        audit(auth,id,"CHICK_BALANCE_PAYMENT_RECORDED"); return invoice(auth,id);
    }
    private static String clean(String s) { return s==null?"":s.trim(); }
    public record Request(@NotNull UUID requestId,@NotNull UUID branchId,UUID customerId,
        @NotBlank @Size(max=200) String customerName,@Size(max=32) String customerPhone,
        @NotBlank @Pattern(regexp="BROILER|LAYER") String chickType,@NotBlank @Size(max=120) String breed,@Min(1) int quantity,
        @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal totalAmount,
        @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal amountPaid,
        @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
        @NotBlank @Pattern(regexp="CASH|CARD|MOBILE_MONEY|BANK_TRANSFER") String paymentMethod,@Size(max=120) String paymentReference) {}
    public record BalanceRequest(@NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,
        @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
        @NotBlank @Pattern(regexp="CASH|CARD|MOBILE_MONEY|BANK_TRANSFER") String paymentMethod,@Size(max=120) String paymentReference) {}
}
