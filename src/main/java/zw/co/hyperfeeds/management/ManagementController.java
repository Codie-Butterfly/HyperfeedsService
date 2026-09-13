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

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    UUID createProduct(@Valid @RequestBody ProductRequest r) {
        UUID category = jdbc.sql("select id from product_categories where lower(name)=lower(:name)")
                .param("name", r.category.trim()).query(UUID.class).optional()
                .orElseGet(() -> {
                    UUID id = UUID.randomUUID();
                    jdbc.sql("insert into product_categories(id,name,active) values(:id,:name,true)")
                            .param("id", id).param("name", r.category.trim()).update();
                    return id;
                });
        UUID product = UUID.randomUUID();
        jdbc.sql("insert into products(id,sku,category_id,name,description,pack_size,published,active) values(:id,:sku,:category,:name,:description,:pack,true,true)")
                .param("id", product).param("sku", r.sku.trim().toUpperCase()).param("category", category)
                .param("name", r.name.trim()).param("description", r.description == null ? null : r.description.trim())
                .param("pack", r.packSize.trim()).update();
        jdbc.sql("insert into branch_prices(branch_id,product_id,amount,currency,effective_from) select id,:product,:amount,:currency,now() from branches where active")
                .param("product", product).param("amount", r.amount).param("currency", r.currency.toUpperCase()).update();
        return product;
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
            select booking_batch.id booking_batch_id,booking_batch.name booking_batch_name,
                   booking_batch.start_date,booking_batch.end_date delivery_date,
                   branch.id branch_id,branch.name branch_name,
                   coalesce(booking.chick_type,offering.chick_type) chick_type,
                   coalesce(booking.breed,offering.breed) breed,
                   sum(booking.quantity) total_chicks,count(booking.id) order_count
            from chick_booking_batches booking_batch
            join chick_bookings booking on booking.booking_batch_id=booking_batch.id
            left join chick_batches offering on offering.id=booking.batch_id
            join branches branch on branch.id=coalesce(booking.pickup_branch_id,offering.branch_id)
            where booking_batch.status='OPEN'
              and current_date between booking_batch.start_date and booking_batch.end_date
              and booking.status in ('ORDERED','CONFIRMED')
            group by booking_batch.id,booking_batch.name,booking_batch.start_date,booking_batch.end_date,
                     branch.id,branch.name,coalesce(booking.chick_type,offering.chick_type),
                     coalesce(booking.breed,offering.breed)
            order by branch.name,chick_type,breed
            """).query().listOfRows();
    }

    @GetMapping("/chicks/current-batch")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    Map<String,Object> currentBookingBatch() {
        return jdbc.sql("select id,name,start_date,end_date delivery_date from chick_booking_batches where status='OPEN' and current_date between start_date and end_date")
                .query().listOfRows().stream().findFirst().orElseGet(Map::of);
    }

    @GetMapping("/chicks/breeds")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    List<Map<String,Object>> chickBreeds() {
        return jdbc.sql("select id,chick_type,breed,price_per_chick,currency,available from chick_breed_configs order by chick_type,breed")
                .query().listOfRows();
    }

    @PostMapping("/chicks/breeds")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    UUID createChickBreed(@Valid @RequestBody ChickBreedRequest r) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into chick_breed_configs(id,chick_type,breed,price_per_chick,currency,available) values(:id,:type,:breed,:price,:currency,:available)")
                .param("id",id).param("type",r.chickType.toUpperCase()).param("breed",r.breed.trim())
                .param("price",r.pricePerChick).param("currency",r.currency.toUpperCase()).param("available",r.available).update();
        return id;
    }

    @PutMapping("/chicks/breeds/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void updateChickBreed(@PathVariable UUID id,@Valid @RequestBody ChickBreedRequest r) {
        int changed = jdbc.sql("update chick_breed_configs set chick_type=:type,breed=:breed,price_per_chick=:price,currency=:currency,available=:available,updated_at=now() where id=:id")
                .param("id",id).param("type",r.chickType.toUpperCase()).param("breed",r.breed.trim())
                .param("price",r.pricePerChick).param("currency",r.currency.toUpperCase()).param("available",r.available).update();
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Chick breed not found");
        jdbc.sql("update chick_batches set price_per_chick=:price,currency=:currency where chick_type=:type and lower(breed)=lower(:breed) and status='OPEN'")
                .param("type",r.chickType.toUpperCase()).param("breed",r.breed.trim())
                .param("price",r.pricePerChick).param("currency",r.currency.toUpperCase()).update();
    }

    @GetMapping("/chicks/booking-batches")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    List<Map<String,Object>> bookingBatches() {
        jdbc.sql("update chick_booking_batches set status='CLOSED',updated_at=now() where status='OPEN' and end_date < current_date").update();
        return jdbc.sql("select id,name,start_date,end_date,status from chick_booking_batches order by created_at desc")
                .query().listOfRows();
    }

    @PostMapping("/chicks/booking-batches")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    UUID createBookingBatch(@Valid @RequestBody BookingBatchRequest r) {
        if (r.endDate.isBefore(r.startDate)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"End date must be on or after start date");
        UUID id=UUID.randomUUID();
        jdbc.sql("insert into chick_booking_batches(id,name,start_date,end_date,status) values(:id,:name,:start,:end,'DRAFT')")
                .param("id",id).param("name",r.name.trim()).param("start",r.startDate).param("end",r.endDate).update();
        return id;
    }

    @PutMapping("/chicks/booking-batches/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateBookingBatch(@PathVariable UUID id,@Valid @RequestBody BookingBatchRequest r) {
        if (r.endDate.isBefore(r.startDate)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"End date must be on or after start date");
        int changed=jdbc.sql("""
                update chick_booking_batches set name=:name,start_date=:start,end_date=:end,updated_at=now()
                where id=:id and status in ('DRAFT','OPEN')
                  and (status='DRAFT' or (:start<=current_date and :end>=current_date))
                """).param("id",id).param("name",r.name.trim()).param("start",r.startDate).param("end",r.endDate).update();
        if(changed==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only draft or active open batches can be edited");
        jdbc.sql("update chick_bookings set delivery_date_snapshot=:date where booking_batch_id=:id and status in ('ORDERED','CONFIRMED')")
                .param("date",r.endDate).param("id",id).update();
    }

    @PostMapping("/chicks/booking-batches/{id}/open")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void openBookingBatch(@PathVariable UUID id) {
        jdbc.sql("update chick_booking_batches set status='CLOSED',updated_at=now() where status='OPEN' and end_date < current_date").update();
        int other=jdbc.sql("select count(*) from chick_booking_batches where status='OPEN' and id<>:id").param("id",id).query(Integer.class).single();
        if(other>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Close the current open batch first");
        int changed=jdbc.sql("update chick_booking_batches set status='OPEN',updated_at=now() where id=:id and status='DRAFT' and start_date<=current_date and end_date>=current_date")
                .param("id",id).update();
        if(changed==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a draft batch within its start and end dates can be opened");
        Map<String,Object> batch=jdbc.sql("select name,start_date,end_date from chick_booking_batches where id=:id").param("id",id).query().singleRow();
        String body="We are taking orders for chicks from "+batch.get("start_date")+" to "+batch.get("end_date")+". Delivery date will be "+batch.get("end_date")+".";
        jdbc.sql("insert into notifications(user_id,type,title,body,data) select id,'CHICK_BOOKING_BATCH_OPEN','Chick bookings are open',:body,jsonb_build_object('bookingBatchId',cast(:batch as text),'deliveryDate',cast(:delivery as text)) from users where active and not employee")
                .param("body",body).param("batch",id).param("delivery",batch.get("end_date")).update();
    }

    @PostMapping("/chicks/booking-batches/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void closeBookingBatch(@PathVariable UUID id) {
        int changed=jdbc.sql("update chick_booking_batches set status='CLOSED',updated_at=now() where id=:id and status='OPEN'").param("id",id).update();
        if(changed==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"The batch is not open");
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
    record ProductRequest(@NotBlank @Size(max=80) String sku,@NotBlank @Size(max=200) String name,
                          @NotBlank @Size(max=120) String category,@NotBlank @Size(max=80) String packSize,
                          @Size(max=2000) String description,@NotNull @DecimalMin("0.00") BigDecimal amount,
                          @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency) {}
    record ChickBreedRequest(@NotBlank @Pattern(regexp="(?i)BROILER|LAYER") String chickType,
                             @NotBlank @Size(max=120) String breed,
                             @NotNull @DecimalMin("0.00") BigDecimal pricePerChick,
                             @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
                             boolean available) {}
    record BookingBatchRequest(@NotBlank @Size(max=120) String name,@NotNull LocalDate startDate,@NotNull LocalDate endDate) {}
    record PriceRequest(@NotNull @DecimalMin("0.00") BigDecimal amount,@NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency) {}
    record NotificationRequest(@NotBlank String audience,UUID branchId,@NotBlank @Size(max=200) String title,@NotBlank @Size(max=5000) String body) {}
}
