package zw.co.hyperfeeds.commerce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Component
class PaymentReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final JdbcClient jdbc;
    private final PaymentGateway gateway;
    private final PaymentStateService states;
    private final Duration timeout;

    PaymentReconciliationJob(JdbcClient jdbc, PaymentGateway gateway, PaymentStateService states,
                             @Value("${hyperfeeds.paynow.payment-timeout}") Duration timeout) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.states = states;
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${hyperfeeds.paynow.poll-delay}", initialDelayString = "10s")
    void poll() {
        var pending = jdbc.sql("select p.id,p.order_id,o.reference,p.provider_reference,p.poll_url,p.created_at from payments p join orders o on o.id=p.order_id where p.status='SENT_TO_SUBSCRIBER' and p.poll_url is not null order by p.created_at limit 100")
                .query().listOfRows();
        if (pending.isEmpty()) {
            log.debug("PAYNOW_RECONCILIATION_IDLE pendingCount=0");
            return;
        }

        log.info("PAYNOW_RECONCILIATION_START pendingCount={} timeout={}", pending.size(), timeout);
        for (var payment : pending) {
            UUID paymentId = (UUID) payment.get("id");
            String orderReference = (String) payment.get("reference");
            try {
                Instant createdAt = ((OffsetDateTime) payment.get("created_at")).toInstant();
                Duration age = Duration.between(createdAt, Instant.now());
                log.debug("PAYNOW_RECONCILE_PAYMENT paymentId={} orderReference={} providerReference={} ageSeconds={}",
                        paymentId, orderReference, payment.get("provider_reference"), age.toSeconds());

                if (createdAt.plus(timeout).isBefore(Instant.now())) {
                    log.warn("PAYNOW_PAYMENT_TIMEOUT paymentId={} orderReference={} ageSeconds={} timeoutSeconds={}",
                            paymentId, orderReference, age.toSeconds(), timeout.toSeconds());
                    states.complete(paymentId, false, "TIMEOUT", null, "TIMED_OUT");
                    continue;
                }

                var status = gateway.poll((String) payment.get("poll_url"));
                String failure = terminalFailure(status.status());
                log.info("PAYNOW_RECONCILE_RESULT paymentId={} orderReference={} paid={} status={} providerReference={} terminalFailure={}",
                        paymentId, orderReference, status.paid(), status.status(), status.paynowReference(), failure);
                states.complete(paymentId, status.paid(), status.status(), status.paynowReference(), failure);
            } catch (Exception e) {
                log.warn("PAYNOW_RECONCILE_ERROR paymentId={} orderReference={} exceptionType={} message={}",
                        paymentId, orderReference, e.getClass().getSimpleName(), e.getMessage());
                log.debug("Paynow reconciliation stack trace for paymentId={}", paymentId, e);
            }
        }
        log.info("PAYNOW_RECONCILIATION_COMPLETE processedCount={}", pending.size());
    }

    private String terminalFailure(String status) {
        return status != null && Set.of("CANCELLED", "FAILED", "ERROR").contains(status.toUpperCase()) ? "FAILED" : null;
    }
}
