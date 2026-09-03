package zw.co.hyperfeeds.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.co.hyperfeeds.identity.CurrentUser;

@RestController @RequestMapping("/advertisements")
public class AdvertisingController {
 private static final Set<String> TEMPLATES=Set.of("DISCOUNT","SPECIAL","CHICKS","NEW_PRODUCT");
 private final JdbcClient jdbc; public AdvertisingController(JdbcClient jdbc){this.jdbc=jdbc;}

 @GetMapping
 List<Map<String,Object>> active(@RequestParam(required=false) UUID branchId){return jdbc.sql("""
   select id,template_type,branch_id,title,body,image_url,cta_label,cta_route,starts_at,ends_at
   from advertising_campaigns where active and starts_at<=now() and ends_at>now()
     and (branch_id is null or branch_id=:branch)
   order by created_at desc
   """).param("branch",branchId).query().listOfRows();}

 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER','BRANCH_MANAGER')") @Transactional
 UUID launch(Authentication a,@Valid @RequestBody CampaignRequest r){
   if(!TEMPLATES.contains(r.templateType))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported advert template");
   boolean main=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ADMIN")||x.getAuthority().equals("ROLE_MAIN_MANAGER"));
   if(!main&&(r.branchId==null||jdbc.sql("select count(*) from employee_branches where user_id=:u and branch_id=:b").param("u",CurrentUser.id(a)).param("b",r.branchId).query(Integer.class).single()==0))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch access denied");
   Instant start=r.startsAt==null?Instant.now():r.startsAt; Instant end=start.plus(Duration.ofHours(r.durationHours));UUID id=UUID.randomUUID();
   jdbc.sql("insert into advertising_campaigns(id,template_type,branch_id,title,body,image_url,cta_label,cta_route,starts_at,ends_at,created_by) values(:id,:type,:branch,:title,:body,:image,:label,:route,:start,:end,:user)")
    .param("id",id).param("type",r.templateType).param("branch",r.branchId).param("title",r.title).param("body",r.body).param("image",r.imageUrl).param("label",r.ctaLabel).param("route",r.ctaRoute).param("start",start).param("end",end).param("user",CurrentUser.id(a)).update();return id;}

 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 @PreAuthorize("hasAnyRole('ADMIN','MAIN_MANAGER','BRANCH_MANAGER')")
 void stop(Authentication a,@PathVariable UUID id){boolean main=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ADMIN")||x.getAuthority().equals("ROLE_MAIN_MANAGER"));UUID u=CurrentUser.id(a);int n=jdbc.sql("update advertising_campaigns set active=false where id=:id and (:main or branch_id in(select branch_id from employee_branches where user_id=:u))").param("id",id).param("main",main).param("u",u).update();if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}

 record CampaignRequest(@NotBlank String templateType,UUID branchId,@NotBlank @Size(max=200)String title,@NotBlank @Size(max=5000)String body,String imageUrl,String ctaLabel,String ctaRoute,Instant startsAt,@Min(1)@Max(2160)long durationHours){}
}
