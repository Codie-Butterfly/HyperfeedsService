package zw.co.hyperfeeds.commerce;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.co.hyperfeeds.identity.CurrentUser;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/commerce")
class OrderCheckoutController {
    private static final Logger log = LoggerFactory.getLogger(OrderCheckoutController.class);
    private final JdbcClient jdbc;
    private final PaymentGateway gateway;

    OrderCheckoutController(JdbcClient jdbc, PaymentGateway gateway) {
        this.jdbc = jdbc;
        this.gateway = gateway;
    }

    @PostMapping("/checkout-options")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    Checkout checkout(Authentication authentication, @Valid @RequestBody CheckoutRequest request) {
        UUID user = CurrentUser.id(authentication);
        Map<String, Object> cart = jdbc.sql("select c.id,c.branch_id,u.phone_number from carts c join users u on u.id=c.user_id where c.user_id=:u and c.status='ACTIVE'")
                .param("u", user).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty"));
        UUID cartId = (UUID) cart.get("id");
        UUID branch = (UUID) cart.get("branch_id");
        List<Map<String, Object>> lines = jdbc.sql("select ci.product_id,p.name,ci.quantity,bp.amount,bp.currency from cart_items ci join products p on p.id=ci.product_id join branch_prices bp on bp.product_id=p.id and bp.branch_id=:b and bp.effective_to is null where ci.cart_id=:c")
                .param("b", branch).param("c", cartId).query().listOfRows();
        if (lines.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty or contains unpriced products");

        BigDecimal total = BigDecimal.ZERO;
        String currency = null;
        for (var line : lines) {
            UUID product = (UUID) line.get("product_id");
            BigDecimal quantity = (BigDecimal) line.get("quantity");
            BigDecimal price = (BigDecimal) line.get("amount");
            String lineCurrency = ((String) line.get("currency")).trim();
            if (currency != null && !currency.equals(lineCurrency)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart has mixed currencies");
            currency = lineCurrency;
            int changed = jdbc.sql("update branch_inventory set reserved=reserved+:q,version=version+1 where branch_id=:b and product_id=:p and on_hand-reserved>=:q")
                    .param("q", quantity).param("b", branch).param("p", product).update();
            if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient inventory for " + line.get("name"));
            total = total.add(quantity.multiply(price));
        }

        UUID orderId = UUID.randomUUID();
        String reference = "ORD-" + orderId.toString().substring(0, 8).toUpperCase();
        boolean payAtShop = request.paymentMethod.equals("PAY_AT_SHOP");
        Integer expiryHours = payAtShop ? unpaidExpiryHours() : null;
        String status = payAtShop ? "AWAITING_PAYMENT_AT_SHOP" : "PAYMENT_PENDING";
        jdbc.sql("insert into orders(id,reference,user_id,branch_id,status,total,currency,payment_method,fulfilment_method,expires_at) values(:id,:ref,:u,:b,:status,:total,:currency,:payment,:fulfilment,case when cast(:hours as integer) is null then null else now()+make_interval(hours=>cast(:hours as integer)) end)")
                .param("id", orderId).param("ref", reference).param("u", user).param("b", branch)
                .param("status", status).param("total", total).param("currency", currency)
                .param("payment", request.paymentMethod).param("fulfilment", request.fulfilmentMethod)
                .param("hours", expiryHours).update();
        for (var line : lines) {
            BigDecimal quantity = (BigDecimal) line.get("quantity");
            BigDecimal price = (BigDecimal) line.get("amount");
            jdbc.sql("insert into order_items(order_id,product_id,product_name,quantity,unit_price,line_total) values(:o,:p,:n,:q,:price,:line)")
                    .param("o", orderId).param("p", line.get("product_id")).param("n", line.get("name"))
                    .param("q", quantity).param("price", price).param("line", quantity.multiply(price)).update();
        }

        String paynowReference = null;
        String instructions;
        if (payAtShop) {
            instructions = "Quote order number " + reference + " at the shop within " + expiryHours + " hours.";
        } else {
            PaymentGateway.Payment payment = gateway.start(reference, total, currency, (String) cart.get("phone_number"));
            paynowReference = payment.reference();
            instructions = payment.instructions();
            jdbc.sql("insert into payments(order_id,provider,provider_reference,status,poll_url,instructions,amount,currency) values(:o,'PAYNOW',:ref,'SENT_TO_SUBSCRIBER',:poll,:instructions,:amount,:currency)")
                    .param("o", orderId).param("ref", payment.reference()).param("poll", payment.pollUrl())
                    .param("instructions", payment.instructions()).param("amount", total).param("currency", currency).update();
        }
        jdbc.sql("update carts set status='CHECKED_OUT',updated_at=now() where id=:id").param("id", cartId).update();
        log.info("ORDER_CREATED reference={} paymentMethod={} fulfilmentMethod={}", reference, request.paymentMethod, request.fulfilmentMethod);
        return new Checkout(orderId, reference, total, currency, paynowReference, instructions,
                request.paymentMethod, request.fulfilmentMethod, expiryHours);
    }

    @GetMapping("/orders/lookup")
    @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER','CUSTOMER_SERVICE')")
    Map<String, Object> lookup(@RequestParam String reference) {
        Map<String, Object> order = jdbc.sql("""
                select o.id,o.reference,o.status,o.total,o.currency,o.payment_method,
                       o.fulfilment_method,o.expires_at,o.created_at,b.name branch_name,
                       u.phone_number,coalesce(p.status,case when o.status='PAID' then 'PAID' else 'NOT_PAID' end) payment_status
                from orders o join branches b on b.id=o.branch_id join users u on u.id=o.user_id
                left join lateral(select status from payments where order_id=o.id order by created_at desc limit 1)p on true
                where upper(o.reference)=upper(:reference)
                """).param("reference", reference.trim()).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order number not found"));
        List<Map<String, Object>> items = jdbc.sql("select product_name,quantity,unit_price,line_total from order_items where order_id=:id order by product_name")
                .param("id", order.get("id")).query().listOfRows();
        Map<String, Object> result = new LinkedHashMap<>(order);
        result.put("items", items);
        return result;
    }

    private int unpaidExpiryHours() {
        String value = jdbc.sql("select config_value from system_configs where config_key='UNPAID_ORDER_EXPIRY_HOURS'")
                .query(String.class).optional().orElse("24");
        return Integer.parseInt(value);
    }

    record CheckoutRequest(
            @Pattern(regexp = "PAY_ON_APP|PAY_AT_SHOP") String paymentMethod,
            @Pattern(regexp = "PICKUP|DELIVERY") String fulfilmentMethod) {}
    record Checkout(UUID orderId, String reference, BigDecimal total, String currency,
                    String paynowReference, String instructions, String paymentMethod,
                    String fulfilmentMethod, Integer expiresInHours) {}
}
