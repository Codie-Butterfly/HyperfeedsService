package zw.co.hyperfeeds.commerce;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.co.hyperfeeds.identity.CurrentUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
@RequestMapping("/commerce/walk-in-sales")
@PreAuthorize("hasAnyRole('BRANCH_MANAGER','CUSTOMER_SERVICE')")
public class WalkInSaleController {
    private final JdbcClient jdbc;

    public WalkInSaleController(JdbcClient jdbc) { this.jdbc = jdbc; }

    // Customer Service retains its existing cross-branch access. Managers use assigned branches only.
    private boolean restricted(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_BRANCH_MANAGER"));
    }

    @GetMapping("/branches")
    public List<Map<String, Object>> branches(Authentication authentication) {
        return jdbc.sql("""
                select b.id,b.name from branches b where b.active and b.collection_enabled
                and (not :restricted or exists(select 1 from employee_branches eb
                    where eb.branch_id=b.id and eb.user_id=:employee)) order by b.name
                """).param("restricted", restricted(authentication))
                .param("employee", CurrentUser.id(authentication)).query().listOfRows();
    }

    @GetMapping("/customers")
    public List<Map<String, Object>> customers(@RequestParam String q) {
        String term = q.trim();
        if (term.length() < 2) return List.of();
        return jdbc.sql("""
                select id,name,phone_number from walk_in_customers
                where strpos(lower(name),lower(:term))>0 or strpos(coalesce(phone_number,''),:phone)>0
                order by name,id limit 30
                """).param("term", term).param("phone", term.replaceAll("\\s+", "")).query().listOfRows();
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(Authentication authentication, @Valid @RequestBody SaleRequest request) {
        UUID employee = CurrentUser.id(authentication);
        boolean allowed = jdbc.sql("""
                select exists(select 1 from branches b where b.id=:branch and b.active and b.collection_enabled
                and (not :restricted or exists(select 1 from employee_branches eb
                    where eb.branch_id=b.id and eb.user_id=:employee)))
                """).param("branch", request.branchId()).param("restricted", restricted(authentication))
                .param("employee", employee).query(Boolean.class).single();
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch is not available to you");
        if (!request.paymentMethod().equals("CASH") && clean(request.paymentReference()).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the external payment reference");

        // Serialize retries for the same request, including requests still committing on another connection.
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key,0))")
                .param("key", request.requestId().toString()).query().singleRow();
        String fingerprint = fingerprint(request);
        var existing = jdbc.sql("select id,created_by,request_fingerprint from orders where request_id=:request")
                .param("request", request.requestId()).query().listOfRows().stream().findFirst();
        if (existing.isPresent()) {
            var row = existing.get();
            if (!employee.equals(row.get("created_by")) || !fingerprint.equals(row.get("request_fingerprint")))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This sale request was already used with different details");
            return invoice(authentication, (UUID) row.get("id"));
        }

        var items = new ArrayList<Map<String, Object>>();
        BigDecimal total = BigDecimal.ZERO;
        String currency = null;
        var seen = new HashSet<UUID>();
        // Stable lock order prevents deadlocks when two staff sell the same products.
        for (var item : request.items().stream().sorted(Comparator.comparing(LineRequest::productId)).toList()) {
            if (!seen.add(item.productId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate product");
            var product = jdbc.sql("""
                    select p.name,bp.amount,trim(bp.currency) currency from products p
                    join product_categories c on c.id=p.category_id
                    join branch_prices bp on bp.product_id=p.id and bp.branch_id=:branch and bp.effective_to is null
                    where p.id=:product and p.active and p.published and c.active
                    """).param("branch", request.branchId()).param("product", item.productId()).query().listOfRows().stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "An item is unavailable or has no branch price"));
            String itemCurrency = (String) product.get("currency");
            if (currency != null && !currency.equals(itemCurrency))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Items must use the same currency");
            currency = itemCurrency;
            BigDecimal price = (BigDecimal) product.get("amount");
            BigDecimal lineTotal = price.multiply(item.quantity()).setScale(2, RoundingMode.HALF_UP);
            int changed = jdbc.sql("""
                    update branch_inventory set on_hand=on_hand-:quantity,version=version+1,updated_at=now()
                    where branch_id=:branch and product_id=:product and on_hand-reserved>=:quantity
                    """).param("quantity", item.quantity()).param("branch", request.branchId())
                    .param("product", item.productId()).update();
            if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock for " + product.get("name"));
            items.add(Map.of("id", item.productId(), "name", product.get("name"), "quantity", item.quantity(),
                    "unitPrice", price, "lineTotal", lineTotal));
            total = total.add(lineTotal);
        }
        if (!request.currency().equals(currency) || total.compareTo(request.amountPaid()) != 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment must match the current branch total and currency. Check the prices and payment amount.");
        UUID customerId = request.customerId();
        String customerName;
        String customerPhone;
        if (customerId != null) {
            var customer = jdbc.sql("select name,phone_number from walk_in_customers where id=:id")
                    .param("id", customerId).query().listOfRows().stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer not found; search again"));
            customerName = (String) customer.get("name");
            customerPhone = clean((String) customer.get("phone_number"));
        } else {
            customerId = UUID.randomUUID();
            customerName = request.customerName().trim();
            customerPhone = clean(request.customerPhone()).replaceAll("\\s+", "");
            int inserted = jdbc.sql("""
                    insert into walk_in_customers(id,name,phone_number,created_by)
                    values(:id,:name,nullif(:phone,''),:employee) on conflict do nothing
                    """).param("id", customerId).param("name", customerName).param("phone", customerPhone)
                    .param("employee", employee).update();
            if (inserted == 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A customer with this phone already exists. Search and select the returning customer.");
        }
        UUID id = UUID.randomUUID();
        String reference = "WIN-" + id.toString().replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
        jdbc.sql("""
                insert into orders(id,reference,branch_id,status,total,currency,payment_method,fulfilment_method,
                    sales_channel,walk_in_customer_id,customer_name,customer_phone,created_by,request_id,request_fingerprint)
                values(:id,:reference,:branch,'PAID',:total,:currency,'PAY_AT_SHOP','PICKUP',
                    'WALK_IN',:customer,:name,:phone,:employee,:request,:fingerprint)
                """).param("id", id).param("reference", reference).param("branch", request.branchId())
                .param("total", total).param("currency", currency).param("customer", customerId).param("name", customerName)
                .param("phone", customerPhone).param("employee", employee)
                .param("request", request.requestId()).param("fingerprint", fingerprint).update();
        for (var item : items) {
            jdbc.sql("""
                    insert into order_items(order_id,product_id,product_name,quantity,unit_price,line_total)
                    values(:order,:product,:name,:quantity,:price,:total)
                    """).param("order", id).param("product", item.get("id")).param("name", item.get("name"))
                    .param("quantity", item.get("quantity")).param("price", item.get("unitPrice"))
                    .param("total", item.get("lineTotal")).update();
        }
        jdbc.sql("""
                insert into payments(order_id,provider,status,amount,currency,external_method,external_reference,recorded_by)
                values(:order,'BRANCH','PAID',:amount,:currency,:method,:reference,:employee)
                """).param("order", id).param("amount", total).param("currency", currency)
                .param("method", request.paymentMethod()).param("reference", clean(request.paymentReference()))
                .param("employee", employee).update();
        jdbc.sql("""
                insert into audit_events(actor_user_id,action,entity_type,entity_id,details)
                values(:employee,'WALK_IN_SALE_RECORDED','ORDER',:id,
                    jsonb_build_object('branchId',cast(:branch as text),'paymentMethod',cast(:method as text)))
                """).param("employee", employee).param("id", id.toString()).param("branch", request.branchId().toString())
                .param("method", request.paymentMethod()).update();
        return invoice(authentication, id);
    }

    @GetMapping("/{id}/invoice")
    public Map<String, Object> invoice(Authentication authentication, @PathVariable UUID id) {
        Map<String, Object> invoice = jdbc.sql("""
                select o.id,o.reference,o.reference invoice_number,o.status,o.created_at,o.customer_name,
                    o.customer_phone,o.total,trim(o.currency) currency,b.name branch_name,b.address branch_address,
                    b.phone_number branch_phone,p.amount amount_paid,p.external_method payment_method,
                    p.external_reference payment_reference,p.created_at paid_at,
                    concat(u.first_name,' ',u.last_name) recorded_by
                from orders o join branches b on b.id=o.branch_id
                join payments p on p.order_id=o.id and p.provider='BRANCH' and p.status='PAID'
                join users u on u.id=p.recorded_by
                where o.id=:id and o.sales_channel='WALK_IN'
                and (not :restricted or exists(select 1 from employee_branches eb
                    where eb.branch_id=o.branch_id and eb.user_id=:employee))
                """).param("id", id).param("restricted", restricted(authentication))
                .param("employee", CurrentUser.id(authentication)).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        var result = new LinkedHashMap<>(invoice);
        result.put("items", jdbc.sql("select product_name,quantity,unit_price,line_total from order_items where order_id=:id order by product_name")
                .param("id", id).query().listOfRows());
        return result;
    }

    @PostMapping("/{id}/invoice-copies")
    @Transactional
    public Map<String, Object> issueInvoiceCopy(Authentication authentication, @PathVariable UUID id) {
        // Check branch access before changing the count. One atomic update also
        // serializes simultaneous requests from different tills/devices.
        var result = new LinkedHashMap<>(invoice(authentication, id));
        int copyNumber = jdbc.sql("update orders set invoice_copy_count=invoice_copy_count+1 where id=:id returning invoice_copy_count")
                .param("id", id).query(Integer.class).single();
        var issuedAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        result.put("copy_number", copyNumber);
        result.put("is_reprint", copyNumber > 1);
        result.put("copy_issued_at", issuedAt.toString());
        jdbc.sql("""
                insert into audit_events(actor_user_id,action,entity_type,entity_id,details)
                values(:employee,'INVOICE_COPY_ISSUED','ORDER',:id,
                    jsonb_build_object('copyNumber',cast(:copy as integer),'isReprint',cast(:reprint as boolean)))
                """).param("employee", CurrentUser.id(authentication)).param("id", id.toString())
                .param("copy", copyNumber).param("reprint", copyNumber > 1).update();
        return result;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static String fingerprint(SaleRequest r) {
        // Length-prefix fields to avoid ambiguous delimiters in names/references.
        var values = new ArrayList<String>(List.of(r.branchId().toString(), Objects.toString(r.customerId(), ""), r.customerName().trim(), clean(r.customerPhone()),
                r.paymentMethod(), clean(r.paymentReference()), r.amountPaid().stripTrailingZeros().toPlainString(), r.currency()));
        r.items().stream().sorted(Comparator.comparing(LineRequest::productId)).forEach(i -> {
            values.add(i.productId().toString()); values.add(i.quantity().stripTrailingZeros().toPlainString());
        });
        StringBuilder input = new StringBuilder();
        values.forEach(v -> input.append(v.length()).append(':').append(v));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public record SaleRequest(@NotNull UUID requestId, @NotNull UUID branchId, UUID customerId,
            @NotBlank @Size(max=200) String customerName, @Size(max=32) String customerPhone,
            @NotEmpty @Size(max=100) List<@NotNull @Valid LineRequest> items,
            @NotBlank @Pattern(regexp="CASH|CARD|MOBILE_MONEY|BANK_TRANSFER") String paymentMethod,
            @Size(max=120) String paymentReference,
            @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal amountPaid,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String currency) {}
    public record LineRequest(@NotNull UUID productId,
            @NotNull @DecimalMin("0.001") @Digits(integer=9,fraction=3) BigDecimal quantity) {}
}
