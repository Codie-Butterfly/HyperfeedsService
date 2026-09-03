package zw.co.hyperfeeds.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.co.hyperfeeds.identity.CurrentUser;

@RestController @RequestMapping("/management/stock-requests")
public class StockRequestController {
 private final JdbcClient jdbc; public StockRequestController(JdbcClient jdbc){this.jdbc=jdbc;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('BRANCH_MANAGER')")
 UUID create(Authentication a,@Valid @RequestBody CreateRequest r){UUID u=CurrentUser.id(a);int access=jdbc.sql("select count(*) from employee_branches where user_id=:u and branch_id=:b").param("u",u).param("b",r.branchId).query(Integer.class).single();if(access==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN);UUID id=UUID.randomUUID();jdbc.sql("insert into stock_requests(id,branch_id,product_id,requested_by,requested_quantity,note) values(:id,:b,:p,:u,:q,:n)").param("id",id).param("b",r.branchId).param("p",r.productId).param("u",u).param("q",r.quantity).param("n",r.note).update();return id;}
 @GetMapping @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER','BRANCH_MANAGER')")
 List<Map<String,Object>> list(Authentication a){boolean main=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ADMIN")||x.getAuthority().equals("ROLE_MAIN_MANAGER"));String filter=main?"":" where sr.branch_id in(select branch_id from employee_branches where user_id=:u)";var q=jdbc.sql("select sr.id,sr.branch_id,b.name branch_name,sr.product_id,p.name product_name,sr.requested_quantity,sr.note,sr.status,sr.created_at from stock_requests sr join branches b on b.id=sr.branch_id join products p on p.id=sr.product_id"+filter+" order by sr.created_at desc");if(!main)q.param("u",CurrentUser.id(a));return q.query().listOfRows();}
 @PatchMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER')") @Transactional
 void review(Authentication a,@PathVariable UUID id,@Valid @RequestBody ReviewRequest r){int n=jdbc.sql("update stock_requests set status=:s,reviewed_by=:u,reviewed_at=now(),updated_at=now() where id=:id").param("s",r.status).param("u",CurrentUser.id(a)).param("id",id).update();if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
 record CreateRequest(@NotNull UUID branchId,@NotNull UUID productId,@NotNull @DecimalMin("0.001") BigDecimal quantity,String note){}
 record ReviewRequest(@NotBlank @Pattern(regexp="APPROVED|REJECTED|FULFILLED") String status){}
}
