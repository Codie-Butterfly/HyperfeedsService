package zw.co.hyperfeeds.commerce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
class PaymentStateService {
    private static final Logger log = LoggerFactory.getLogger(PaymentStateService.class);

    private final JdbcClient jdbc;

    PaymentStateService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    void complete(UUID paymentId, boolean paid, String providerStatus, String providerReference, String failure) {
        log.info("PAYMENT_STATE_UPDATE_START paymentId={} paid={} providerStatus={} providerReference={} failure={}",
                paymentId, paid, providerStatus, providerReference, failure);

        Map<String, Object> payment = jdbc.sql("""
                select p.id,p.order_id,p.chick_booking_id,p.status,
                       coalesce(o.user_id,cb.user_id) user_id,
                       coalesce(o.reference,cb.reference) reference,
                       o.branch_id
                from payments p left join orders o on o.id=p.order_id
                left join chick_bookings cb on cb.id=p.chick_booking_id
                where p.id=:id
                """)
                .param("id", paymentId).query().singleRow();
        String currentStatus = (String) payment.get("status");
        String orderReference = (String) payment.get("reference");

        if (Set.of("PAID", "FAILED", "TIMED_OUT").contains(currentStatus)) {
            log.info("PAYMENT_STATE_UPDATE_SKIPPED paymentId={} orderReference={} currentStatus={} reason=terminal_state",
                    paymentId, orderReference, currentStatus);
            return;
        }

        String paymentStatus = paid ? "PAID" : failure == null ? "SENT_TO_SUBSCRIBER" : failure;
        jdbc.sql("update payments set status=:s,provider_reference=coalesce(:ref,provider_reference),failure_reason=:failure,last_polled_at=now(),updated_at=now() where id=:id")
                .param("s", paymentStatus).param("ref", providerReference).param("failure", failure).param("id", paymentId).update();
        log.info("PAYMENT_STATUS_CHANGED paymentId={} orderReference={} from={} to={} providerStatus={}",
                paymentId, orderReference, currentStatus, paymentStatus, providerStatus);

        if (!paid && failure == null) {
            log.debug("PAYMENT_REMAINS_PENDING paymentId={} orderReference={} providerStatus={}",
                    paymentId, orderReference, providerStatus);
            return;
        }

        UUID chickBookingId = (UUID) payment.get("chick_booking_id");
        if (chickBookingId != null) {
            String bookingStatus = paid ? "CONFIRMED" : "DEPOSIT_PAYMENT_FAILED";
            jdbc.sql("update chick_bookings set status=:status,deposit_paid_at=case when :paid then now() else deposit_paid_at end,updated_at=now() where id=:id")
                    .param("status", bookingStatus).param("paid", paid).param("id", chickBookingId).update();
            String title = paid ? "Chick order guaranteed" : "Chick deposit unsuccessful";
            String body = paid
                    ? "Your deposit for chick order " + orderReference + " has been paid. Your order is now guaranteed."
                    : "The deposit for chick order " + orderReference + " was not completed.";
            jdbc.sql("insert into notifications(user_id,type,title,body,data) values(:u,:type,:title,:body,jsonb_build_object('chickBookingId',:booking))")
                    .param("u", payment.get("user_id"))
                    .param("type", paid ? "CHICK_DEPOSIT_PAID" : "CHICK_DEPOSIT_FAILED")
                    .param("title", title).param("body", body).param("booking", chickBookingId.toString()).update();
            return;
        }

        String orderStatus = paid ? "PAID" : "PAYMENT_FAILED";
        jdbc.sql("update orders set status=:s,updated_at=now() where id=:id")
                .param("s", orderStatus).param("id", payment.get("order_id")).update();
        log.info("ORDER_PAYMENT_STATUS_CHANGED orderId={} orderReference={} to={}",
                payment.get("order_id"), orderReference, orderStatus);

        String inventoryOperation = paid
                ? "on_hand=on_hand-oi.quantity,reserved=reserved-oi.quantity"
                : "reserved=reserved-oi.quantity";
        int inventoryRows = jdbc.sql("update branch_inventory bi set " + inventoryOperation + ",version=version+1,updated_at=now() from order_items oi where oi.order_id=:o and bi.branch_id=:b and bi.product_id=oi.product_id")
                .param("o", payment.get("order_id")).param("b", payment.get("branch_id")).update();
        log.info("PAYMENT_INVENTORY_FINALIZED paymentId={} orderReference={} action={} affectedProducts={}",
                paymentId, orderReference, paid ? "deduct_and_release" : "release_reservation", inventoryRows);

        String title = paid ? "Payment received" : "Payment unsuccessful";
        String body = paid ? "Order " + orderReference + " has been paid."
                : "Payment for order " + orderReference + " was not completed.";
        jdbc.sql("insert into notifications(user_id,type,title,body,data) values(:u,:type,:title,:body,jsonb_build_object('orderId',:order))")
                .param("u", payment.get("user_id")).param("type", paid ? "ORDER_PAID" : "PAYMENT_FAILED")
                .param("title", title).param("body", body).param("order", payment.get("order_id").toString()).update();
        log.info("PAYMENT_NOTIFICATION_CREATED paymentId={} orderReference={} type={}",
                paymentId, orderReference, paid ? "ORDER_PAID" : "PAYMENT_FAILED");
        log.info("PAYMENT_STATE_UPDATE_COMPLETE paymentId={} orderReference={} paymentStatus={} orderStatus={}",
                paymentId, orderReference, paymentStatus, orderStatus);
    }
}
