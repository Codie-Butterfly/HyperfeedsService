package zw.co.hyperfeeds.commerce;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.co.hyperfeeds.identity.CurrentUser;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/chicks/bookings")
class ChickBookingCheckoutController {
    private final JdbcClient jdbc;
    private final PaymentGateway gateway;

    ChickBookingCheckoutController(JdbcClient jdbc, PaymentGateway gateway) {
        this.jdbc = jdbc;
        this.gateway = gateway;
    }

    @PostMapping("/{id}/checkout")
    @Transactional
    Checkout checkout(Authentication authentication, @PathVariable UUID id,
                      @Valid @RequestBody CheckoutRequest request) {
        Map<String, Object> booking = jdbc.sql("""
                select cb.id,cb.reference,cb.status,cb.deposit_required,cb.deposit_amount,
                       cb.currency,u.phone_number,u.email
                from chick_bookings cb join users u on u.id=cb.user_id
                where cb.id=:id and cb.user_id=:user for update
                """).param("id", id).param("user", CurrentUser.id(authentication))
                .query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chick order not found"));
        if (!Boolean.TRUE.equals(booking.get("deposit_required")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This chick order does not require a deposit");
        if ("CONFIRMED".equals(booking.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The chick order deposit is already paid");

        String reference = (String) booking.get("reference");
        BigDecimal amount = (BigDecimal) booking.get("deposit_amount");
        String currency = ((String) booking.get("currency")).trim();
        if (request.paymentMethod.equals("PAY_AT_BRANCH")) {
            jdbc.sql("update chick_bookings set status='AWAITING_DEPOSIT_AT_BRANCH',deposit_payment_method='PAY_AT_BRANCH',updated_at=now() where id=:id")
                    .param("id", id).update();
            return new Checkout(id, reference, amount, currency, "PAY_AT_BRANCH", null,
                    "Quote chick order number " + reference + " when paying the deposit at the pickup branch.");
        }

        String email = (String) booking.get("email");
        if (email == null || email.isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Add an email address to your profile before paying online");
        PaymentGateway.Payment payment = gateway.start(reference, amount, currency,
                (String) booking.get("phone_number"), email);
        jdbc.sql("insert into payments(chick_booking_id,provider,provider_reference,status,poll_url,instructions,amount,currency) values(:booking,'PAYNOW',:ref,'SENT_TO_SUBSCRIBER',:poll,:instructions,:amount,:currency)")
                .param("booking", id).param("ref", payment.reference()).param("poll", payment.pollUrl())
                .param("instructions", payment.instructions()).param("amount", amount).param("currency", currency).update();
        jdbc.sql("update chick_bookings set status='DEPOSIT_PAYMENT_PENDING',deposit_payment_method='PAY_ON_APP',updated_at=now() where id=:id")
                .param("id", id).update();
        return new Checkout(id, reference, amount, currency, "PAY_ON_APP",
                payment.reference(), payment.instructions());
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER','CUSTOMER_SERVICE')")
    Map<String, Object> lookup(@RequestParam String reference) {
        Map<String, Object> booking = jdbc.sql("""
                select cb.id,cb.reference,cb.status,cb.quantity,cb.chick_type,cb.breed,
                       cb.unit_price,cb.total_amount,cb.currency,cb.delivery_date_snapshot delivery_date,
                       cb.deposit_required,cb.deposit_percentage,cb.deposit_amount,
                       cb.deposit_payment_method,cb.deposit_paid_at,
                       cb.collected_at,
                       (cb.total_amount-case when cb.deposit_paid_at is null then 0 else cb.deposit_amount end) amount_owed,
                       b.name branch_name,u.phone_number,
                       coalesce(p.status,case when cb.deposit_paid_at is not null then 'PAID' else 'NOT_PAID' end) deposit_status
                from chick_bookings cb join branches b on b.id=cb.pickup_branch_id
                join users u on u.id=cb.user_id
                left join lateral(select status from payments where chick_booking_id=cb.id order by created_at desc limit 1)p on true
                where upper(cb.reference)=upper(:reference)
                """).param("reference", reference.trim()).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chick order number not found"));
        return new LinkedHashMap<>(booking);
    }

    record CheckoutRequest(@Pattern(regexp = "PAY_ON_APP|PAY_AT_BRANCH") String paymentMethod) {}
    record Checkout(UUID bookingId, String reference, BigDecimal depositAmount,
                    String currency, String paymentMethod, String paynowReference,
                    String instructions) {}
}
