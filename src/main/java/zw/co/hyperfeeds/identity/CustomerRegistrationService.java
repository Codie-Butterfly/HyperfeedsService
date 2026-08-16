package zw.co.hyperfeeds.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
class CustomerRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(CustomerRegistrationService.class);
    private final UserRepository users;
    private final PhoneVerificationChallengeRepository challenges;
    private final OtpSender otpSender;
    private final JdbcTemplate jdbc;
    private final EmployeeAuthenticationService authentication;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;
    private final Duration resendCooldown;
    private final int maxAttempts;
    private final String pepper;
    private final String fixedCode;

    CustomerRegistrationService(UserRepository users, PhoneVerificationChallengeRepository challenges,
            OtpSender otpSender, JdbcTemplate jdbc, EmployeeAuthenticationService authentication,
            @Value("${hyperfeeds.otp.ttl}") Duration ttl,
            @Value("${hyperfeeds.otp.resend-cooldown}") Duration resendCooldown,
            @Value("${hyperfeeds.otp.max-attempts}") int maxAttempts,
            @Value("${hyperfeeds.otp.pepper}") String pepper,
            @Value("${hyperfeeds.otp.fixed-code:}") String fixedCode) {
        this.users = users; this.challenges = challenges; this.otpSender = otpSender; this.jdbc = jdbc; this.authentication = authentication;
        this.ttl = ttl; this.resendCooldown = resendCooldown; this.maxAttempts = maxAttempts; this.pepper = pepper;
        this.fixedCode = fixedCode.trim();
        if (!this.fixedCode.isEmpty() && !this.fixedCode.matches("\\d{6}"))
            throw new IllegalArgumentException("OTP_FIXED_CODE must contain exactly six digits");
    }

    @Transactional
    SignupResult signup(String rawPhone, String firstName, String lastName) {
        String phone = normalizePhone(rawPhone);
        log.info("customer.signup.requested destination={}", mask(phone));
        User user = users.findByPhoneNumber(phone).orElse(null);
        if (user != null && user.phoneVerified) {
            throw new IdentityException(HttpStatus.CONFLICT, "PHONE_ALREADY_REGISTERED", "That phone number is already registered");
        }
        if (user == null) {
            user = users.saveAndFlush(new User(phone, firstName.trim(), lastName.trim()));
            jdbc.update("insert into user_roles(user_id, role_id) select ?, id from roles where code = 'CUSTOMER' on conflict do nothing", user.id);
        } else {
            enforceCooldown(user);
            user.firstName = firstName.trim(); user.lastName = lastName.trim(); user.updatedAt = Instant.now();
        }
        String code = fixedCode.isEmpty() ? "%06d".formatted(random.nextInt(1_000_000)) : fixedCode;
        Instant expiresAt = Instant.now().plus(ttl);
        var challenge = challenges.save(new PhoneVerificationChallenge(user, hash(code), expiresAt, maxAttempts));
        if (fixedCode.isEmpty()) {
            otpSender.send(phone, code);
        } else {
            log.warn("customer.signup.fixed_otp_test_mode challengeId={} destination={}", challenge.id, mask(phone));
        }
        log.info("customer.signup.otp_dispatched challengeId={} destination={} expiresAt={}",
                challenge.id, mask(phone), expiresAt);
        return new SignupResult(challenge.id, mask(phone), expiresAt, resendCooldown.toSeconds());
    }

    @Transactional(noRollbackFor = IdentityException.class)
    EmployeeAuthenticationService.TokenResult verify(UUID challengeId, String code) {
        var challenge = challenges.findById(challengeId).orElseThrow(() ->
                new IdentityException(HttpStatus.NOT_FOUND, "CHALLENGE_NOT_FOUND", "Verification challenge was not found"));
        if (challenge.verifiedAt != null) return authentication.issueTokenPair(challenge.user);
        if (challenge.expiresAt.isBefore(Instant.now()))
            throw new IdentityException(HttpStatus.GONE, "OTP_EXPIRED", "The verification code has expired");
        if (challenge.attemptsRemaining == 0)
            throw new IdentityException(HttpStatus.TOO_MANY_REQUESTS, "OTP_ATTEMPTS_EXHAUSTED", "No verification attempts remain");
        if (!MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.US_ASCII), challenge.codeHash.getBytes(StandardCharsets.US_ASCII))) {
            challenge.attemptsRemaining--;
            throw new IdentityException(HttpStatus.UNPROCESSABLE_ENTITY, "OTP_INVALID", "The verification code is incorrect");
        }
        challenge.verifiedAt = Instant.now();
        challenge.user.phoneVerified = true;
        challenge.user.updatedAt = Instant.now();
        jdbc.update("insert into notifications(user_id,type,title,body) values (?,'WELCOME','Welcome to Hyperfeeds','Your Hyperfeeds account is ready to use.')", challenge.user.id);
        log.info("customer.signup.verified challengeId={} userId={}", challenge.id, challenge.user.id);
        return authentication.issueTokenPair(challenge.user);
    }

    private void enforceCooldown(User user) {
        challenges.findFirstByUserOrderByCreatedAtDesc(user).ifPresent(latest -> {
            if (latest.createdAt != null && latest.createdAt.plus(resendCooldown).isAfter(Instant.now()))
                throw new IdentityException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RESEND_TOO_SOON", "Wait before requesting another code");
        });
    }

    private String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((code + pepper).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static String normalizePhone(String value) {
        String phone = value.replaceAll("[\\s()-]", "");
        if (phone.startsWith("00")) phone = "+" + phone.substring(2);
        if (phone.startsWith("0")) phone = "+263" + phone.substring(1);
        if (!phone.matches("\\+[1-9]\\d{7,14}"))
            throw new IdentityException(HttpStatus.BAD_REQUEST, "INVALID_PHONE_NUMBER", "Use a valid phone number, for example +263771234567");
        return phone;
    }
    EmployeeAuthenticationService.TokenResult refresh(String token) { return authentication.refresh(token); }

    private static String mask(String phone) {
        return phone.substring(0, Math.min(4, phone.length())) + "*".repeat(Math.max(0, phone.length() - 7)) + phone.substring(phone.length() - 3);
    }

    record SignupResult(UUID challengeId, String destination, Instant expiresAt, long resendAfterSeconds) {}
}
