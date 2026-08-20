package zw.co.hyperfeeds.identity;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
class CustomerProfileService {
    private final JdbcClient jdbc;

    CustomerProfileService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    CustomerProfile get(UUID userId) {
        CustomerRow user = jdbc.sql("""
                select id,phone_number,first_name,last_name,phone_verified,preferred_branch_id
                from users where id=:id and active and not employee
                """).param("id", userId).query(CustomerRow.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found"));
        List<String> roles = jdbc.sql("""
                select r.code from roles r join user_roles ur on ur.role_id=r.id
                where ur.user_id=:id order by r.code
                """).param("id", userId).query(String.class).list();
        return new CustomerProfile(user.id(), user.phoneNumber(), user.firstName(), user.lastName(),
                user.phoneVerified(), user.preferredBranchId(), roles);
    }

    private record CustomerRow(UUID id, String phoneNumber, String firstName, String lastName,
                               boolean phoneVerified, UUID preferredBranchId) {}
    record CustomerProfile(UUID id, String phoneNumber, String firstName, String lastName,
                           boolean phoneVerified, UUID preferredBranchId, List<String> roles) {}
}
