package zw.co.hyperfeeds.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
class EmployeeAuthenticationService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwtEncoder;
    private final JdbcTemplate jdbc;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final String issuer;
    private final String tokenPepper;
    private final SecureRandom random = new SecureRandom();

    EmployeeAuthenticationService(UserRepository users, RefreshTokenRepository refreshTokens,
            PasswordEncoder passwords, JwtEncoder jwtEncoder, JdbcTemplate jdbc,
            @Value("${hyperfeeds.auth.access-token-ttl}") Duration accessTtl,
            @Value("${hyperfeeds.auth.refresh-token-ttl}") Duration refreshTtl,
            @Value("${hyperfeeds.auth.issuer}") String issuer,
            @Value("${hyperfeeds.auth.token-pepper}") String tokenPepper) {
        this.users = users; this.refreshTokens = refreshTokens; this.passwords = passwords;
        this.jwtEncoder = jwtEncoder; this.jdbc = jdbc; this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl; this.issuer = issuer; this.tokenPepper = tokenPepper;
    }

    @Transactional
    TokenResult login(String rawPhone, String password) {
        User user = users.findByPhoneNumber(CustomerRegistrationService.normalizePhone(rawPhone)).orElse(null);
        if (user == null || !user.employee || !user.active || user.passwordHash == null || !passwords.matches(password, user.passwordHash))
            throw invalidCredentials();
        return issueTokenPair(user);
    }

    @Transactional
    TokenResult refresh(String rawToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(rawToken)).orElseThrow(this::invalidRefreshToken);
        if (stored.revokedAt != null || stored.expiresAt.isBefore(Instant.now()) || !stored.user.active)
            throw invalidRefreshToken();
        stored.revokedAt = Instant.now();
        return issueTokenPair(stored.user);
    }

    @Transactional
    void logout(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(token -> token.revokedAt = Instant.now());
    }

    TokenResult issueTokenPair(User user) {
        Instant now = Instant.now();
        Instant accessExpires = now.plus(accessTtl);
        List<String> roles = jdbc.queryForList("select r.code from roles r join user_roles ur on ur.role_id=r.id where ur.user_id=? order by r.code", String.class, user.id);
        List<UUID> branchIds = jdbc.queryForList("select branch_id from employee_branches where user_id=? order by branch_id", UUID.class, user.id);
        var claims = JwtClaimsSet.builder().issuer(issuer).issuedAt(now).expiresAt(accessExpires)
                .subject(user.id.toString()).claim("phone_number", user.phoneNumber)
                .claim("roles", roles).claim("branch_ids", branchIds.stream().map(UUID::toString).toList()).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        byte[] bytes = new byte[48]; random.nextBytes(bytes);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user, hash(rawRefresh), now.plus(refreshTtl)));
        return new TokenResult(accessToken, rawRefresh, "Bearer", accessExpires, refreshTtl.toSeconds());
    }

    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((token + tokenPepper).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private IdentityException invalidCredentials() {
        return new IdentityException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Phone number or password is incorrect");
    }
    private IdentityException invalidRefreshToken() {
        return new IdentityException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
    }

    record TokenResult(String accessToken, String refreshToken, String tokenType, Instant accessTokenExpiresAt, long refreshTokenExpiresInSeconds) {}
}
