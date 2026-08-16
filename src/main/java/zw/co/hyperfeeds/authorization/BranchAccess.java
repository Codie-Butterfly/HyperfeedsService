package zw.co.hyperfeeds.authorization;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

/** Reusable policy for APIs whose data belongs to a particular branch. */
@Component("branchAccess")
public class BranchAccess {
    public boolean canAccess(Authentication authentication, UUID branchId) {
        if (authentication == null || !authentication.isAuthenticated() || branchId == null) return false;
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) return true;
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) return false;
        List<String> assignedBranches = jwtAuthentication.getToken().getClaimAsStringList("branch_ids");
        return assignedBranches != null && assignedBranches.contains(branchId.toString());
    }
}
