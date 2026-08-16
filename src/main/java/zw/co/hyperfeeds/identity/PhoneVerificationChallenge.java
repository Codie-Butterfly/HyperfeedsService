package zw.co.hyperfeeds.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "phone_verification_challenges")
class PhoneVerificationChallenge {
    @Id @GeneratedValue(strategy = GenerationType.UUID) UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id") User user;
    @Column(name = "code_hash", nullable = false, length = 64) String codeHash;
    @Column(name = "expires_at", nullable = false) Instant expiresAt;
    @Column(name = "attempts_remaining", nullable = false) int attemptsRemaining;
    @Column(name = "verified_at") Instant verifiedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) Instant createdAt;

    protected PhoneVerificationChallenge() {}
    PhoneVerificationChallenge(User user, String codeHash, Instant expiresAt, int attemptsRemaining) {
        this.user = user; this.codeHash = codeHash; this.expiresAt = expiresAt; this.attemptsRemaining = attemptsRemaining;
    }
}
