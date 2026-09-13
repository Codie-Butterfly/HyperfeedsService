package zw.co.hyperfeeds.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/management/order-config")
@PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
class OrderConfigurationController {
    private final JdbcClient jdbc;
    OrderConfigurationController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping
    Map<String, Integer> get() {
        int hours = Integer.parseInt(jdbc.sql("select config_value from system_configs where config_key='UNPAID_ORDER_EXPIRY_HOURS'")
                .query(String.class).optional().orElse("24"));
        return Map.of("unpaidOrderExpiryHours", hours);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@Valid @RequestBody Config request) {
        jdbc.sql("insert into system_configs(config_key,config_value) values('UNPAID_ORDER_EXPIRY_HOURS',:value) on conflict(config_key) do update set config_value=excluded.config_value,updated_at=now()")
                .param("value", Integer.toString(request.unpaidOrderExpiryHours)).update();
    }

    record Config(@Min(1) @Max(720) int unpaidOrderExpiryHours) {}
}
