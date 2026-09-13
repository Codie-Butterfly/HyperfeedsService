package zw.co.hyperfeeds.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
@RequestMapping("/management/order-config")
@PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')")
class OrderConfigurationController {
    private final JdbcClient jdbc;
    OrderConfigurationController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping
    Config get() {
        int hours = Integer.parseInt(jdbc.sql("select config_value from system_configs where config_key='UNPAID_ORDER_EXPIRY_HOURS'")
                .query(String.class).optional().orElse("24"));
        boolean depositEnabled = Boolean.parseBoolean(jdbc.sql("select config_value from system_configs where config_key='CHICK_ORDER_DEPOSIT_ENABLED'")
                .query(String.class).optional().orElse("false"));
        BigDecimal depositPercentage = new BigDecimal(jdbc.sql("select config_value from system_configs where config_key='CHICK_ORDER_DEPOSIT_PERCENTAGE'")
                .query(String.class).optional().orElse("0"));
        return new Config(hours, depositEnabled, depositPercentage);
    }

    @PutMapping
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@Valid @RequestBody Config request) {
        if (request.chickOrderDepositEnabled && request.chickOrderDepositPercentage.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chick order deposit percentage must be greater than zero when deposits are enabled");
        }
        jdbc.sql("insert into system_configs(config_key,config_value) values('UNPAID_ORDER_EXPIRY_HOURS',:value) on conflict(config_key) do update set config_value=excluded.config_value,updated_at=now()")
                .param("value", Integer.toString(request.unpaidOrderExpiryHours)).update();
        jdbc.sql("insert into system_configs(config_key,config_value) values('CHICK_ORDER_DEPOSIT_ENABLED',:value) on conflict(config_key) do update set config_value=excluded.config_value,updated_at=now()")
                .param("value", Boolean.toString(request.chickOrderDepositEnabled)).update();
        jdbc.sql("insert into system_configs(config_key,config_value) values('CHICK_ORDER_DEPOSIT_PERCENTAGE',:value) on conflict(config_key) do update set config_value=excluded.config_value,updated_at=now()")
                .param("value", request.chickOrderDepositPercentage.stripTrailingZeros().toPlainString()).update();
    }

    record Config(@Min(1) @Max(720) int unpaidOrderExpiryHours,
                  boolean chickOrderDepositEnabled,
                  @DecimalMin("0") @DecimalMax("100") BigDecimal chickOrderDepositPercentage) {}
}
