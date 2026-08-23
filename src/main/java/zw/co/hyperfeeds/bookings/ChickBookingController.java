package zw.co.hyperfeeds.bookings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.co.hyperfeeds.identity.CurrentUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/chicks")
public class ChickBookingController {
    private static final String ACTIVE_ORDER_STATUSES = "('ORDERED','CONFIRMED')";
    private final JdbcClient jdbc;

    public ChickBookingController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Open ordering windows for the selected pickup branch. A window remains
     * visible only until its cutoff. Once it closes, the next matching window
     * automatically becomes the option used by new customer orders.
     */
    @GetMapping("/availability")
    public List<OrderingOption> availability(@RequestParam UUID branchId) {
        return jdbc.sql("""
                select id, branch_id, chick_type, breed, cutoff_at, delivery_date,
                       price_per_chick, currency
                from chick_batches
                where branch_id = :branch
                  and active
                  and status = 'OPEN'
                  and cutoff_at > now()
                order by chick_type, breed, cutoff_at
                """)
                .param("branch", branchId)
                .query(OrderingOption.class)
                .list();
    }

    @PostMapping("/batches")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('BRANCH_MANAGER') and @branchAccess.canAccess(authentication,#request.branchId))")
    @ResponseStatus(HttpStatus.CREATED)
    public UUID createBatch(@Valid @RequestBody BatchRequest request) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into chick_batches(
                    id, branch_id, chick_type, breed, hatch_date, cutoff_at,
                    delivery_date, available_quantity, reserved_quantity,
                    price_per_chick, currency, active, status
                ) values (
                    :id, :branch, :type, :breed, :delivery, :cutoff,
                    :delivery, 0, 0, :price, :currency, true, 'OPEN'
                )
                """)
                .param("id", id)
                .param("branch", request.branchId)
                .param("type", request.chickType.toUpperCase())
                .param("breed", request.breed.trim())
                .param("delivery", request.deliveryDate)
                .param("cutoff", request.cutoffAt)
                .param("price", request.pricePerChick)
                .param("currency", request.currency.toUpperCase())
                .update();
        return id;
    }

    @PostMapping("/bookings")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public BookingReceipt order(Authentication authentication, @Valid @RequestBody OrderRequest request) {
        String type = request.chickType.toUpperCase();
        Map<String, Object> batch = jdbc.sql("""
                select id, cutoff_at, delivery_date, price_per_chick, currency
                from chick_batches
                where branch_id = :branch
                  and chick_type = :type
                  and lower(breed) = lower(:breed)
                  and active
                  and status = 'OPEN'
                  and cutoff_at > now()
                order by cutoff_at
                limit 1
                for update
                """)
                .param("branch", request.branchId)
                .param("type", type)
                .param("breed", request.breed.trim())
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "No open ordering batch is available for this chick type, breed and pickup branch"));

        UUID userId = CurrentUser.id(authentication);
        UUID batchId = (UUID) batch.get("id");
        BigDecimal unitPrice = (BigDecimal) batch.get("price_per_chick");
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(request.quantity));
        String currency = (String) batch.get("currency");
        LocalDate deliveryDate = (LocalDate) batch.get("delivery_date");
        OffsetDateTime cutoffAt = (OffsetDateTime) batch.get("cutoff_at");
        UUID id = UUID.randomUUID();
        String reference = "CHK-" + id.toString().substring(0, 8).toUpperCase();

        jdbc.sql("""
                insert into chick_bookings(
                    id, reference, user_id, batch_id, quantity, status,
                    unit_price, total_amount, currency, delivery_date_snapshot
                ) values (
                    :id, :reference, :user, :batch, :quantity, 'ORDERED',
                    :unitPrice, :total, :currency, :deliveryDate
                )
                """)
                .param("id", id)
                .param("reference", reference)
                .param("user", userId)
                .param("batch", batchId)
                .param("quantity", request.quantity)
                .param("unitPrice", unitPrice)
                .param("total", total)
                .param("currency", currency)
                .param("deliveryDate", deliveryDate)
                .update();

        return new BookingReceipt(id, reference, "ORDERED", batchId, type,
                request.breed.trim(), request.branchId, request.quantity,
                unitPrice, total, currency, cutoffAt, deliveryDate);
    }

    @GetMapping("/bookings")
    public List<CustomerOrder> mine(Authentication authentication) {
        return jdbc.sql("""
                select booking.id, booking.reference, booking.batch_id,
                       batch.branch_id, batch.chick_type, batch.breed,
                       booking.quantity, booking.status, booking.unit_price,
                       booking.total_amount, booking.currency, batch.cutoff_at,
                       batch.delivery_date, booking.created_at
                from chick_bookings booking
                join chick_batches batch on batch.id = booking.batch_id
                where booking.user_id = :user
                order by booking.created_at desc
                """)
                .param("user", CurrentUser.id(authentication))
                .query(CustomerOrder.class)
                .list();
    }

    @DeleteMapping("/bookings/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(Authentication authentication, @PathVariable UUID id) {
        int changed = jdbc.sql("""
                update chick_bookings booking
                set status = 'CANCELLED', updated_at = now()
                from chick_batches batch
                where booking.id = :id
                  and booking.user_id = :user
                  and booking.batch_id = batch.id
                  and booking.status = 'ORDERED'
                  and batch.cutoff_at > now()
                """)
                .param("id", id)
                .param("user", CurrentUser.id(authentication))
                .update();
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The order cannot be cancelled after its batch cutoff");
        }
    }

    @GetMapping("/batches/{id}/summary")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER')")
    public BatchSummary summary(@PathVariable UUID id) {
        Map<String, Object> batch = jdbc.sql("""
                select id, branch_id, chick_type, breed, cutoff_at,
                       delivery_date, status, price_per_chick, currency
                from chick_batches where id = :id
                """)
                .param("id", id)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chick batch not found"));

        Map<String, Object> totals = jdbc.sql("""
                select count(*) order_count, coalesce(sum(quantity), 0) total_chicks,
                       coalesce(sum(total_amount), 0) total_value
                from chick_bookings
                where batch_id = :id and status in ('ORDERED','CONFIRMED')
                """)
                .param("id", id)
                .query()
                .singleRow();

        return new BatchSummary(
                (UUID) batch.get("id"), (UUID) batch.get("branch_id"),
                (String) batch.get("chick_type"), (String) batch.get("breed"),
                (OffsetDateTime) batch.get("cutoff_at"), (LocalDate) batch.get("delivery_date"),
                (String) batch.get("status"), ((Number) totals.get("order_count")).longValue(),
                ((Number) totals.get("total_chicks")).longValue(),
                (BigDecimal) totals.get("total_value"), (String) batch.get("currency"));
    }

    @PatchMapping("/batches/{id}/delivery-date")
    @PreAuthorize("hasAnyRole('ADMIN','BRANCH_MANAGER')")
    @Transactional
    public DeliveryDateChange changeDeliveryDate(@PathVariable UUID id,
            @Valid @RequestBody DeliveryDateRequest request) {
        Map<String, Object> batch = jdbc.sql("""
                select delivery_date, breed, branch_id
                from chick_batches where id = :id for update
                """)
                .param("id", id)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chick batch not found"));
        LocalDate oldDate = (LocalDate) batch.get("delivery_date");
        if (oldDate.equals(request.deliveryDate)) {
            return new DeliveryDateChange(id, oldDate, request.deliveryDate, 0);
        }

        jdbc.sql("update chick_batches set delivery_date = :date where id = :id")
                .param("date", request.deliveryDate)
                .param("id", id)
                .update();

        String breed = (String) batch.get("breed");
        String body = request.message == null || request.message.isBlank()
                ? "The pickup date for your " + breed + " chick order has changed from "
                    + oldDate + " to " + request.deliveryDate + "."
                : request.message.trim();
        int notified = jdbc.sql("""
                insert into notifications(user_id, type, title, body, data)
                select distinct booking.user_id, 'CHICK_DELIVERY_DATE_CHANGED',
                       'Chick delivery date changed', :body,
                       jsonb_build_object('batchId', cast(:batchId as text),
                                          'oldDeliveryDate', cast(:oldDate as text),
                                          'deliveryDate', cast(:newDate as text))
                from chick_bookings booking
                where booking.batch_id = :batchId
                  and booking.status in ('ORDERED','CONFIRMED')
                """)
                .param("body", body)
                .param("batchId", id)
                .param("oldDate", oldDate)
                .param("newDate", request.deliveryDate)
                .update();

        return new DeliveryDateChange(id, oldDate, request.deliveryDate, notified);
    }

    public record OrderingOption(UUID id, UUID branchId, String chickType,
            String breed, OffsetDateTime cutoffAt, LocalDate deliveryDate,
            BigDecimal pricePerChick, String currency) {}

    public record BatchRequest(@NotNull UUID branchId,
            @Pattern(regexp = "(?i)BROILER|LAYER") String chickType,
            @NotBlank String breed, @NotNull @Future OffsetDateTime cutoffAt,
            @NotNull @FutureOrPresent LocalDate deliveryDate,
            @NotNull @DecimalMin("0.00") BigDecimal pricePerChick,
            @Pattern(regexp = "[A-Za-z]{3}") String currency) {}

    public record OrderRequest(@NotNull UUID branchId,
            @Pattern(regexp = "(?i)BROILER|LAYER") String chickType,
            @NotBlank String breed, @Min(1) int quantity) {}

    public record BookingReceipt(UUID id, String reference, String status,
            UUID batchId, String chickType, String breed, UUID branchId,
            int quantity, BigDecimal unitPrice, BigDecimal totalAmount,
            String currency, OffsetDateTime cutoffAt, LocalDate deliveryDate) {}

    public record CustomerOrder(UUID id, String reference, UUID batchId,
            UUID branchId, String chickType, String breed, int quantity,
            String status, BigDecimal unitPrice, BigDecimal totalAmount,
            String currency, OffsetDateTime cutoffAt, LocalDate deliveryDate,
            Instant createdAt) {}

    public record BatchSummary(UUID id, UUID branchId, String chickType,
            String breed, OffsetDateTime cutoffAt, LocalDate deliveryDate,
            String status, long orderCount, long totalChicks,
            BigDecimal totalValue, String currency) {}

    public record DeliveryDateRequest(@NotNull @FutureOrPresent LocalDate deliveryDate,
            String message) {}

    public record DeliveryDateChange(UUID batchId, LocalDate oldDeliveryDate,
            LocalDate deliveryDate, int notifiedCustomers) {}
}
