package zw.co.hyperfeeds.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.UUID) UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id") User user;
    @Column(name = "token_hash", nullable = false, unique = true) String tokenHash;
    @Column(name = "expires_at", nullable = false) Instant expiresAt;
    @Column(name = "revoked_at") Instant revokedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) Instant createdAt;

    protected RefreshToken() {}
    RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user; this.tokenHash = tokenHash; this.expiresAt = expiresAt;
    }
}
