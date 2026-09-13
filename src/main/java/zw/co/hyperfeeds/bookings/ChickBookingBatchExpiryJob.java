package zw.co.hyperfeeds.bookings;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ChickBookingBatchExpiryJob {
    private final JdbcClient jdbc;

    ChickBookingBatchExpiryJob(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelayString = "${hyperfeeds.bookings.batch-expiry-scan-delay:60000}")
    @Transactional
    void closeExpiredBatches() {
        jdbc.sql("""
                insert into notifications(user_id,type,title,body,data)
                select distinct booking.user_id,'CHICK_PICKUP_REMINDER','Chicks ready for collection',
                       'Your chick order is due for collection today. Please anticipate picking up your chicks.',
                       jsonb_build_object('bookingBatchId',cast(batch.id as text),'deliveryDate',cast(batch.end_date as text))
                from chick_booking_batches batch
                join chick_bookings booking on booking.booking_batch_id=batch.id
                where batch.end_date=current_date
                  and batch.delivery_notification_sent_at is null
                  and booking.status in ('ORDERED','CONFIRMED')
                """).update();
        jdbc.sql("""
                update chick_booking_batches set delivery_notification_sent_at=now(),updated_at=now()
                where end_date=current_date and delivery_notification_sent_at is null
                """).update();
        jdbc.sql("update chick_booking_batches set status='CLOSED',updated_at=now() where status='OPEN' and end_date < current_date")
                .update();
    }
}
