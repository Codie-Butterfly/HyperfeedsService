package zw.co.hyperfeeds.commerce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/payments/paynow")
class PaynowCallbackController {
    private static final Logger log = LoggerFactory.getLogger(PaynowCallbackController.class);

    private final JdbcClient jdbc;
    private final PaymentGateway gateway;
    private final PaymentStateService states;

    PaynowCallbackController(JdbcClient jdbc, PaymentGateway gateway, PaymentStateService states) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.states = states;
    }

    @PostMapping("/callback")
    String callback(@RequestParam Map<String, String> form) {
        String reference = form.get("reference");
        log.info("PAYNOW_CALLBACK_RECEIVED reference={} providerReference={} reportedStatus={} fields={}",
                reference, form.get("paynowreference"), form.get("status"), form.keySet());

        if (reference == null || reference.isBlank()) {
            log.warn("PAYNOW_CALLBACK_IGNORED reason=missing_reference fields={}", form.keySet());
            return "OK";
        }

        var payment = jdbc.sql("select p.id,p.poll_url from payments p join orders o on o.id=p.order_id where o.reference=:r and p.status='SENT_TO_SUBSCRIBER'")
                .param("r", reference)
                .query()
                .listOfRows()
                .stream()
                .findFirst();

        if (payment.isEmpty()) {
            log.warn("PAYNOW_CALLBACK_IGNORED reference={} reason=no_pending_payment", reference);
            return "OK";
        }

        UUID paymentId = (UUID) payment.get().get("id");
        try {
            log.info("PAYNOW_CALLBACK_VERIFY_START reference={} paymentId={}", reference, paymentId);
            var verified = gateway.poll((String) payment.get().get("poll_url"));
            log.info("PAYNOW_CALLBACK_VERIFIED reference={} paymentId={} paid={} status={} providerReference={}",
                    reference, paymentId, verified.paid(), verified.status(), verified.paynowReference());
            states.complete(paymentId, verified.paid(), verified.status(), verified.paynowReference(), null);
        } catch (Exception e) {
            log.error("PAYNOW_CALLBACK_VERIFY_ERROR reference={} paymentId={} exceptionType={} message={}",
                    reference, paymentId, e.getClass().getSimpleName(), e.getMessage());
            log.debug("Paynow callback verification stack trace for paymentId={}", paymentId, e);
            throw e;
        }
        return "OK";
    }
}
