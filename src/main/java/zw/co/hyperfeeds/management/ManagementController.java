package zw.co.hyperfeeds.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.co.hyperfeeds.identity.CurrentUser;

@RestController
@RequestMapping("/management")
public class ManagementController {
    private final JdbcClient jdbc;
    private final PasswordEncoder passwords;
    public ManagementController(JdbcClient jdbc, PasswordEncoder passwords) { this.jdbc = jdbc; this.passwords = passwords; }

    @PostMapping("/employees")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    UUID createEmployee(@Valid @RequestBody EmployeeRequest r) {
        if (!Set.of("MAIN_MANAGER", "BRANCH_MANAGER", "ANIMAL_HEALTH_EXPERT", "CUSTOMER_SERVICE").contains(r.role))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported employee role");
        if (r.role.equals("BRANCH_MANAGER") && r.branchId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A branch manager must have a branch");
        UUID id = UUID.randomUUID();
        String phone = normalizePhone(r.phoneNumber);
        jdbc.sql("insert into users(id,phone_number,first_name,last_name,password_hash,phone_verified,employee,active,preferred_branch_id) values(:id,:phone,:first,:last,:password,true,true,true,:branch)")
                .param("id",id).param("phone",phone).param("first",r.firstName.trim()).param("last",r.lastName.trim())
                .param("password",passwords.encode(r.password)).param("branch",r.branchId).update();
        jdbc.sql("insert into user_roles(user_id,role_id) select :user,id from roles where code=:role")
                .param("user",id).param("role",r.role).update();
        if (r.branchId != null) jdbc.sql("insert into employee_branches(user_id,branch_id) values(:user,:branch)")
                .param("user",id).param("branch",r.branchId).update();
        return id;
    }

    @PutMapping("/prices/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void setCompanyPrice(@PathVariable UUID productId, @Valid @RequestBody PriceRequest r) {
        jdbc.sql("update branch_prices set effective_to=now() where product_id=:product and effective_to is null")
                .param("product",productId).update();
        jdbc.sql("insert into branch_prices(branch_id,product_id,amount,currency,effective_from) select id,:product,:amount,:currency,now() from branches where active")
                .param("product",productId).param("amount",r.amount).param("currency",r.currency.toUpperCase()).update();
    }

    @GetMapping("/prices")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    List<Map<String,Object>> companyPrices() {
        return jdbc.sql("""
            select p.id,p.sku,p.name,min(bp.amount) amount,
                   min(trim(bp.currency)) currency,count(bp.branch_id) priced_branches
            from products p
            left join branch_prices bp on bp.product_id=p.id and bp.effective_to is null
            where p.active and p.published
            group by p.id,p.sku,p.name
            order by p.name
            """).query().listOfRows();
    }

    @GetMapping("/chicks/demand")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    List<Map<String,Object>> chickDemand() {
        return jdbc.sql("""
            select b.id branch_id,b.name branch_name,cb.chick_type,cb.breed,cb.delivery_date,
                   coalesce(sum(case when bk.status in ('ORDERED','CONFIRMED') then bk.quantity else 0 end),0) total_chicks,
                   count(bk.id) filter(where bk.status in ('ORDERED','CONFIRMED')) order_count
            from chick_batches cb join branches b on b.id=cb.branch_id
            left join chick_bookings bk on bk.batch_id=cb.id
            group by b.id,b.name,cb.chick_type,cb.breed,cb.delivery_date
            order by cb.delivery_date,b.name,cb.chick_type,cb.breed
            """).query().listOfRows();
    }

    @PostMapping("/notifications")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER','BRANCH_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    int notify(Authentication auth, @Valid @RequestBody NotificationRequest r) {
        boolean main = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MAIN_MANAGER"));
        UUID actor = CurrentUser.id(auth);
        if (!main && (r.branchId == null || !canAccess(actor, r.branchId)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch access denied");
        if (!main && !r.audience.equals("CUSTOMERS"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch managers may notify branch customers only");
        String condition = switch (r.audience) {
            case "BRANCH_MANAGERS" -> "u.employee and exists(select 1 from user_roles ur join roles ro on ro.id=ur.role_id where ur.user_id=u.id and ro.code='BRANCH_MANAGER')";
            case "CUSTOMERS" -> "not u.employee";
            case "ALL" -> "true";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported audience");
        };
        return jdbc.sql("insert into notifications(user_id,type,title,body,data) select u.id,'MANAGEMENT',:title,:body,jsonb_build_object('branchId',cast(:branch as text)) from users u where u.active and " + condition + " and (cast(:branch as uuid) is null or u.preferred_branch_id=:branch or exists(select 1 from employee_branches eb where eb.user_id=u.id and eb.branch_id=:branch))")
                .param("title",r.title).param("body",r.body).param("branch",r.branchId).update();
    }

    private boolean canAccess(UUID user, UUID branch) { return jdbc.sql("select count(*) from employee_branches where user_id=:u and branch_id=:b").param("u",user).param("b",branch).query(Integer.class).single()>0; }
    private String normalizePhone(String p) { String v=p.replaceAll("[\\s()-]",""); if(v.startsWith("0"))v="+263"+v.substring(1); if(!v.startsWith("+"))v="+"+v; return v; }

    record EmployeeRequest(@NotBlank String phoneNumber,@NotBlank String firstName,@NotBlank String lastName,@NotBlank @Size(min=10) String password,@NotBlank String role,UUID branchId) {}
    record PriceRequest(@NotNull @DecimalMin("0.00") BigDecimal amount,@NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency) {}
    record NotificationRequest(@NotBlank String audience,UUID branchId,@NotBlank @Size(max=200) String title,@NotBlank @Size(max=5000) String body) {}
}
