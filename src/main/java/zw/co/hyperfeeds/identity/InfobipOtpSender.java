package zw.co.hyperfeeds.identity;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "hyperfeeds.otp.provider", havingValue = "infobip")
class InfobipOtpSender implements OtpSender {
    private static final Logger log = LoggerFactory.getLogger(InfobipOtpSender.class);
    private final RestClient client;
    private final String sender;

    InfobipOtpSender(@Value("${hyperfeeds.otp.infobip.base-url}") String baseUrl,
            @Value("${hyperfeeds.otp.infobip.api-key}") String apiKey,
            @Value("${hyperfeeds.otp.infobip.sender}") String sender) {
        this.client = RestClient.builder().baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, authorizationValue(apiKey))
                .build();
        this.sender = sender;
    }

    @Override
    public void send(String phoneNumber, String code) {
        String destination = phoneNumber.startsWith("+") ? phoneNumber.substring(1) : phoneNumber;
        var body = Map.of("messages", List.of(Map.of(
                "destinations", List.of(Map.of("to", destination)),
                "sender", sender,
                "content", Map.of("text", "Your Hyperfeeds verification code is " + code
                        + ". It expires in 5 minutes."))));
        try {
            client.post()
                    .uri("/sms/3/messages")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Verification SMS accepted for delivery to {}", mask(phoneNumber));
        } catch (RestClientResponseException exception) {
            log.error("infobip.sms.rejected destination={} status={}",
                    mask(phoneNumber), exception.getStatusCode().value());
            throw deliveryFailure();
        } catch (RestClientException exception) {
            log.error("infobip.sms.failed destination={} reason={}",
                    mask(phoneNumber), exception.getClass().getSimpleName());
            throw deliveryFailure();
        }
    }

    static String authorizationValue(String configuredKey) {
        String key = configuredKey == null ? "" : configuredKey.trim();
        if (key.length() >= 2 && ((key.startsWith("\"") && key.endsWith("\""))
                || (key.startsWith("'") && key.endsWith("'")))) {
            key = key.substring(1, key.length() - 1).trim();
        }
        if (key.regionMatches(true, 0, "App ", 0, 4)) key = key.substring(4).trim();
        if (key.isEmpty()) throw new IllegalStateException("INFOBIP_API_KEY must not be empty");
        return "App " + key;
    }

    private static IdentityException deliveryFailure() {
        return new IdentityException(HttpStatus.BAD_GATEWAY, "OTP_DELIVERY_FAILED",
                "The verification code could not be sent. Please try again.");
    }

    private static String mask(String phoneNumber) {
        if (phoneNumber.length() <= 7) return "***";
        return phoneNumber.substring(0, 4) + "***" + phoneNumber.substring(phoneNumber.length() - 3);
    }
}
