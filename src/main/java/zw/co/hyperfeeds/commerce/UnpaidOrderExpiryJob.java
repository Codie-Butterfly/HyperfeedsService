package zw.co.hyperfeeds.commerce;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
class UnpaidOrderExpiryJob {
    private final JdbcClient jdbc;
    UnpaidOrderExpiryJob(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelayString = "${hyperfeeds.orders.expiry-scan-delay:60000}")
    @Transactional
    void removeExpired() {
        List<Map<String, Object>> orders = jdbc.sql("select id,branch_id from orders where status='AWAITING_PAYMENT_AT_SHOP' and expires_at<=now() for update skip locked")
                .query().listOfRows();
        for (var order : orders) {
            jdbc.sql("update branch_inventory bi set reserved=reserved-oi.quantity,version=version+1,updated_at=now() from order_items oi where oi.order_id=:order and bi.branch_id=:branch and bi.product_id=oi.product_id")
                    .param("order", order.get("id")).param("branch", order.get("branch_id")).update();
            jdbc.sql("delete from orders where id=:id").param("id", order.get("id")).update();
        }
    }
}
