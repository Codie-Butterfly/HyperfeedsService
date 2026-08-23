package zw.co.hyperfeeds.commerce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import zw.co.paynow.constants.MobileMoneyMethod;
import zw.co.paynow.core.Paynow;
import zw.co.paynow.responses.MobileInitResponse;

import java.math.BigDecimal;

@Component
class PaynowPaymentGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(PaynowPaymentGateway.class);

    private final String id;
    private final String key;
    private final String resultUrl;

    PaynowPaymentGateway(
            @Value("${hyperfeeds.paynow.integration-id}") String id,
            @Value("${hyperfeeds.paynow.integration-key}") String key,
            @Value("${hyperfeeds.paynow.result-url}") String resultUrl) {
        this.id = id;
        this.key = key;
        this.resultUrl = resultUrl;
        log.info("Paynow gateway initialized: credentialsConfigured={}, callbackConfigured={}",
                !id.isBlank() && !key.isBlank(), !resultUrl.isBlank());
    }

    public Payment start(String reference, BigDecimal amount, String currency, String phone) {
        log.info("PAYNOW_INIT_START reference={} amount={} currency={} customerPhone={}",
                reference, amount, currency, maskPhone(phone));

        if (id.isBlank() || key.isBlank()) {
            log.error("PAYNOW_INIT_CONFIG_ERROR reference={} credentialsConfigured=false", reference);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Paynow is not configured");
        }
        if (!"USD".equalsIgnoreCase(currency)) {
            log.warn("PAYNOW_INIT_REJECTED reference={} reason=unsupported_currency currency={}", reference, currency);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Paynow checkout currently supports USD");
        }

        String local = localNumber(phone);
        MobileMoneyMethod mobileMethod = method(local);
        log.info("PAYNOW_INIT_PREPARED reference={} localPhone={} method={} callbackConfigured={}",
                reference, maskPhone(local), mobileMethod, !resultUrl.isBlank());

        var paynow = new Paynow(id, key, resultUrl);
        var payment = paynow.createPayment(reference);
        payment.add("Hyperfeeds order " + reference, amount.doubleValue());

        MobileInitResponse response;
        try {
            log.info("PAYNOW_SDK_SEND_START reference={} method={}", reference, mobileMethod);
            response = paynow.sendMobile(payment, local, mobileMethod);
        } catch (Exception e) {
            log.error("PAYNOW_SDK_SEND_ERROR reference={} exceptionType={} message={}",
                    reference, e.getClass().getSimpleName(), e.getMessage());
            log.debug("Paynow SDK initiation stack trace for reference={}", reference, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Paynow could not initiate payment", e);
        }

        log.info("PAYNOW_INIT_RESPONSE reference={} success={} status={} providerReference={} pollUrlReceived={} instructionsReceived={}",
                reference, response.success(), response.getStatus(), response.getPaynowReference(),
                response.pollUrl() != null && !response.pollUrl().isBlank(),
                response.instructions() != null && !response.instructions().isBlank());

        if (!response.success()) {
            log.error("PAYNOW_INIT_REJECTED reference={} status={} errors={}",
                    reference, response.getStatus(), response.getErrors());
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "Paynow rejected payment: " + response.getErrors());
        }

        log.info("PAYNOW_INIT_SUCCESS reference={} providerReference={}", reference, response.getPaynowReference());
        return new Payment(response.getPaynowReference(), response.pollUrl(), response.instructions());
    }

    public PaymentStatus poll(String pollUrl) {
        log.debug("PAYNOW_POLL_START pollUrlPresent={}", pollUrl != null && !pollUrl.isBlank());
        try {
            var status = new Paynow(id, key).pollTransaction(pollUrl);
            log.info("PAYNOW_POLL_RESPONSE paid={} status={} providerReference={}",
                    status.isPaid(), status.getStatus(), status.getPaynowReference());
            return new PaymentStatus(status.isPaid(), status.getStatus().name(), status.getPaynowReference());
        } catch (Exception e) {
            log.error("PAYNOW_POLL_ERROR exceptionType={} message={}", e.getClass().getSimpleName(), e.getMessage());
            log.debug("Paynow polling stack trace", e);
            throw e;
        }
    }

    private static String localNumber(String phone) {
        if (phone == null) {
            log.warn("PAYNOW_PHONE_REJECTED reason=missing_phone");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paynow mobile payments require a Zimbabwe number");
        }
        if (phone.startsWith("+263")) return "0" + phone.substring(4);
        if (phone.startsWith("263")) return "0" + phone.substring(3);
        if (phone.matches("0\\d{9}")) return phone;
        log.warn("PAYNOW_PHONE_REJECTED phone={} reason=invalid_zimbabwe_number", maskPhone(phone));
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paynow mobile payments require a Zimbabwe number");
    }

    private static MobileMoneyMethod method(String phone) {
        String prefix = phone.substring(1, 3);
        return switch (prefix) {
            case "77", "78" -> MobileMoneyMethod.ECOCASH;
            case "71" -> MobileMoneyMethod.ONEMONEY;
            case "73" -> MobileMoneyMethod.TELECASH;
            default -> {
                log.warn("PAYNOW_PHONE_REJECTED phone={} prefix={} reason=unsupported_mobile_money_operator",
                        maskPhone(phone), prefix);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported mobile-money number");
            }
        };
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "<missing>";
        String compact = phone.replaceAll("\\s+", "");
        if (compact.length() <= 4) return "****";
        return "*".repeat(compact.length() - 4) + compact.substring(compact.length() - 4);
    }
}
