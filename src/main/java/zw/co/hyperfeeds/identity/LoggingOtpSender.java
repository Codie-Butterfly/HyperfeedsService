package zw.co.hyperfeeds.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hyperfeeds.otp.provider", havingValue = "logging", matchIfMissing = true)
class LoggingOtpSender implements OtpSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);
    public void send(String phoneNumber, String code) {
        log.info("Development OTP for {} is {}", phoneNumber, code);
    }
}
