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
                .defaultHeader(HttpHeaders.AUTHORIZATION, "App " + apiKey)
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
        } catch (RestClientException exception) {
            log.error("Infobip rejected verification SMS delivery to {}", mask(phoneNumber), exception);
            throw new IdentityException(HttpStatus.BAD_GATEWAY, "OTP_DELIVERY_FAILED",
                    "The verification code could not be sent. Please try again.");
        }
    }

    private static String mask(String phoneNumber) {
        if (phoneNumber.length() <= 7) return "***";
        return phoneNumber.substring(0, 4) + "***" + phoneNumber.substring(phoneNumber.length() - 3);
    }
}
