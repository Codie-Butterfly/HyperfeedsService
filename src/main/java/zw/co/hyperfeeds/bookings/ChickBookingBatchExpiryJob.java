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
        jdbc.sql("update chick_booking_batches set status='CLOSED',updated_at=now() where status='OPEN' and end_date < current_date")
                .update();
    }
}
