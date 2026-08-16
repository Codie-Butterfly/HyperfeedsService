package zw.co.hyperfeeds.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.Optional;

interface PhoneVerificationChallengeRepository extends JpaRepository<PhoneVerificationChallenge, UUID> {
    Optional<PhoneVerificationChallenge> findFirstByUserOrderByCreatedAtDesc(User user);

    @Query("select challenge from PhoneVerificationChallenge challenge join fetch challenge.user where challenge.id = :id")
    Optional<PhoneVerificationChallenge> findByIdWithUser(@Param("id") UUID id);
}
