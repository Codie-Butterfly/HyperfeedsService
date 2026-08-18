package zw.co.hyperfeeds.branches;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Service
public class BranchService {
    private final JdbcClient jdbc;
    public BranchService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<BranchView> activeBranches() {
        return jdbc.sql("select id,code,name,address,phone_number,whatsapp_number,email_address,opening_hours,collection_enabled,active from branches where active order by name")
                .query(BranchView.class).list();
    }

    public BranchView branch(UUID id) {
        return jdbc.sql("select id,code,name,address,phone_number,whatsapp_number,email_address,opening_hours,collection_enabled,active from branches where id=:id and active")
                .param("id", id).query(BranchView.class).optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public BranchView create(BranchInput input) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into branches(id,code,name,address,phone_number,whatsapp_number,email_address,opening_hours,collection_enabled,active) values(:id,:code,:name,:address,:phone,:whatsapp,:email,:hours,:collection,:active)")
                .param("id", id).param("code", input.code().trim().toUpperCase()).param("name", input.name().trim())
                .param("address", input.address().trim()).param("phone", input.phoneNumber())
                .param("whatsapp", input.whatsappNumber()).param("email", input.emailAddress()).param("hours", input.openingHours())
                .param("collection", input.collectionEnabled()).param("active", input.active()).update();
        return branchForManagement(id);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') or (hasRole('BRANCH_MANAGER') and @branchAccess.canAccess(authentication, #id))")
    public BranchView update(UUID id, BranchInput input) {
        int changed = jdbc.sql("update branches set code=:code,name=:name,address=:address,phone_number=:phone,whatsapp_number=:whatsapp,email_address=:email,opening_hours=:hours,collection_enabled=:collection,active=:active,updated_at=now() where id=:id")
                .param("id", id).param("code", input.code().trim().toUpperCase()).param("name", input.name().trim())
                .param("address", input.address().trim()).param("phone", input.phoneNumber())
                .param("whatsapp", input.whatsappNumber()).param("email", input.emailAddress()).param("hours", input.openingHours())
                .param("collection", input.collectionEnabled()).param("active", input.active()).update();
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
        return branchForManagement(id);
    }

    private BranchView branchForManagement(UUID id) {
        return jdbc.sql("select id,code,name,address,phone_number,whatsapp_number,email_address,opening_hours,collection_enabled,active from branches where id=:id")
                .param("id", id).query(BranchView.class).single();
    }

    public record BranchView(UUID id, String code, String name, String address, String phoneNumber,
            String whatsappNumber, String emailAddress, String openingHours, boolean collectionEnabled, boolean active) {}
    public record BranchInput(String code, String name, String address, String phoneNumber,
            String whatsappNumber, String emailAddress, String openingHours, boolean collectionEnabled, boolean active) {}
}
