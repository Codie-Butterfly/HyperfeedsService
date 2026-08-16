package zw.co.hyperfeeds.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;

interface PhoneVerificationChallengeRepository extends JpaRepository<PhoneVerificationChallenge, UUID> {
    Optional<PhoneVerificationChallenge> findFirstByUserOrderByCreatedAtDesc(User user);
}
