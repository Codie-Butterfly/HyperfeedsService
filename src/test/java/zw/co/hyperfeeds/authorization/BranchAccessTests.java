package zw.co.hyperfeeds.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class BranchAccessTests {
    private final BranchAccess policy = new BranchAccess();

    @Test
    void permitsAnAssignedBranch() {
        UUID branchId = UUID.randomUUID();
        assertThat(policy.canAccess(authentication(List.of(branchId.toString()), "ROLE_EMPLOYEE"), branchId)).isTrue();
    }

    @Test
    void deniesAnUnassignedBranch() {
        assertThat(policy.canAccess(authentication(List.of(UUID.randomUUID().toString()), "ROLE_EMPLOYEE"), UUID.randomUUID())).isFalse();
    }

    @Test
    void administratorsCanAccessEveryBranch() {
        assertThat(policy.canAccess(authentication(List.of(), "ROLE_ADMIN"), UUID.randomUUID())).isTrue();
    }

    private JwtAuthenticationToken authentication(List<String> branches, String authority) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                java.util.Map.of("alg", "HS256"), java.util.Map.of("sub", "user", "branch_ids", branches));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)));
    }
}
