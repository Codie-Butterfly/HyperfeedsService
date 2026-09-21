package zw.co.hyperfeeds.commerce;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(WalkInSaleTests.Config.class)
@EnabledIfEnvironmentVariable(named="WALK_IN_TEST_DB_URL", matches=".+")
class WalkInSaleTests {
    @Configuration @EnableMethodSecurity @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            var ds = new DriverManagerDataSource(System.getenv("WALK_IN_TEST_DB_URL"), System.getProperty("user.name"), "");
            Flyway.configure().dataSource(ds).load().migrate(); return ds;
        }
        @Bean JdbcClient jdbc(DataSource ds) { return JdbcClient.create(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean WalkInSaleController controller(JdbcClient jdbc) { return new WalkInSaleController(jdbc); }
        @Bean OrderCheckoutController orders(JdbcClient jdbc) { return new OrderCheckoutController(jdbc, null); }
    }
    @Autowired JdbcClient jdbc;
    @Autowired WalkInSaleController controller;
    @Autowired OrderCheckoutController orders;
    UUID employee, branch, product;
    Authentication auth;

    @BeforeEach void seed() {
        employee=UUID.randomUUID(); branch=UUID.randomUUID(); product=UUID.randomUUID();
        jdbc.sql("insert into users(id,phone_number,first_name,last_name,employee) values(:id,:phone,'Test','Cashier',true)")
                .param("id",employee).param("phone",employee.toString().substring(0,30)).update();
        jdbc.sql("insert into branches(id,code,name,address,phone_number) values(:id,:code,'Test Branch','Test address','000')")
                .param("id",branch).param("code",branch.toString()).update();
        jdbc.sql("insert into employee_branches values(:user,:branch)").param("user",employee).param("branch",branch).update();
        UUID category=UUID.randomUUID();
        jdbc.sql("insert into product_categories(id,name) values(:id,:name)").param("id",category).param("name",category.toString()).update();
        jdbc.sql("insert into products(id,sku,category_id,name,pack_size,published) values(:id,:sku,:category,'Test feed','50kg',true)")
                .param("id",product).param("sku",product.toString()).param("category",category).update();
        jdbc.sql("insert into branch_prices(branch_id,product_id,amount,currency) values(:branch,:product,12.50,'USD')")
                .param("branch",branch).param("product",product).update();
        jdbc.sql("insert into branch_inventory(branch_id,product_id,on_hand,reserved) values(:branch,:product,10,2)")
                .param("branch",branch).param("product",product).update();
        role("BRANCH_MANAGER");
    }
    void role(String role) {
        auth=new UsernamePasswordAuthenticationToken(employee.toString(),"",List.of(new SimpleGrantedAuthority("ROLE_"+role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    WalkInSaleController.SaleRequest request(UUID key, UUID customer, String name, String phone, String method, String reference, String amount) {
        return new WalkInSaleController.SaleRequest(key,branch,customer,name,phone,
                List.of(new WalkInSaleController.LineRequest(product,new BigDecimal("2"))),method,reference,new BigDecimal(amount),"USD");
    }
    WalkInSaleController.SaleRequest sale() { return request(UUID.randomUUID(),null,"Regular "+employee,"","CASH","","25.00"); }
    BigDecimal stock() { return jdbc.sql("select on_hand from branch_inventory where branch_id=:b and product_id=:p").param("b",branch).param("p",product).query(BigDecimal.class).single(); }

    @Test void firstTimeCustomerWithoutPhoneAndRetryRecordsOnlyOneSale() {
        var request=sale(); var first=controller.create(auth,request); var retry=controller.create(auth,request);
        assertThat(retry.get("id")).isEqualTo(first.get("id"));
        assertThat(stock()).isEqualByComparingTo("8");
        assertThat(jdbc.sql("select count(*) from payments where order_id=:id").param("id",first.get("id")).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from walk_in_customers where created_by=:id").param("id",employee).query(Integer.class).single()).isEqualTo(1);
        assertThat(first).containsEntry("customer_name",request.customerName()).containsEntry("payment_method","CASH");
        assertThat(orders.lookup(auth,(String)first.get("reference"))).containsEntry("sales_channel","WALK_IN");
        orders.markCollected(auth,(UUID)first.get("id"));
        assertThat(controller.invoice(auth,(UUID)first.get("id"))).containsEntry("status","COLLECTED");
        assertThat(stock()).isEqualByComparingTo("8");
    }
    @Test void returningCustomerReusesSavedIdentityAndInvoiceName() {
        controller.create(auth,sale());
        var customer=controller.customers(employee.toString()).get(0);
        var second=controller.create(auth,request(UUID.randomUUID(),(UUID)customer.get("id"),"Ignored override","","CARD","POS-123","25.00"));
        assertThat(second.get("customer_name")).isEqualTo(customer.get("name"));
        assertThat(jdbc.sql("select count(*) from walk_in_customers where created_by=:id").param("id",employee).query(Integer.class).single()).isEqualTo(1);
        assertThat(stock()).isEqualByComparingTo("6");
    }
    @Test void amountMismatchRollsBackStockAndCustomerCreation() {
        assertThatThrownBy(() -> controller.create(auth,request(UUID.randomUUID(),null,"New Person","","CASH","","24.00"))).isInstanceOf(ResponseStatusException.class);
        assertThat(stock()).isEqualByComparingTo("10");
        assertThat(jdbc.sql("select count(*) from orders where created_by=:id").param("id",employee).query(Integer.class).single()).isZero();
    }
    @Test void insufficientStockCannotSellReservedUnits() {
        jdbc.sql("update branch_inventory set on_hand=3 where branch_id=:b").param("b",branch).update();
        assertThatThrownBy(() -> controller.create(auth,sale())).isInstanceOf(ResponseStatusException.class);
        assertThat(stock()).isEqualByComparingTo("3");
    }
    @Test void branchManagerCannotSellOrReadInvoiceFromUnassignedBranch() {
        var invoice=controller.create(auth,sale());
        jdbc.sql("delete from employee_branches where user_id=:u").param("u",employee).update();
        assertThatThrownBy(() -> controller.create(auth,sale())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.invoice(auth,(UUID)invoice.get("id"))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void customerCannotCreateSaleSearchDirectoryOrReadInvoice() {
        role("CUSTOMER");
        assertThatThrownBy(() -> controller.create(auth,sale())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.customers("Regular")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.invoice(auth,UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }
    @Test void customerServiceCanRecordPaymentWithReference() {
        role("CUSTOMER_SERVICE");
        var invoice=controller.create(auth,request(UUID.randomUUID(),null,"Customer","","MOBILE_MONEY","MM-123","25.00"));
        assertThat(invoice).containsEntry("payment_reference","MM-123").containsEntry("recorded_by","Test Cashier");
    }
    @Test void externalPaymentRequiresReference() {
        assertThatThrownBy(() -> controller.create(auth,request(UUID.randomUUID(),null,"Customer","","BANK_TRANSFER","","25.00"))).isInstanceOf(ResponseStatusException.class);
        assertThat(stock()).isEqualByComparingTo("10");
    }
    @Test void retryWithChangedDetailsRejected() {
        var request=sale(); controller.create(auth,request);
        assertThatThrownBy(() -> controller.create(auth,request(request.requestId(),null,"Someone else","","CASH","","25.00"))).isInstanceOf(ResponseStatusException.class);
        assertThat(stock()).isEqualByComparingTo("8");
    }
    @Test void duplicatePhonePromptsCustomerSelectionAndRollsBack() {
        String phone=employee.toString().substring(0,20);
        controller.create(auth,request(UUID.randomUUID(),null,"First",phone,"CASH","","25.00"));
        assertThat(controller.customers(phone)).hasSize(1);
        assertThatThrownBy(() -> controller.create(auth,request(UUID.randomUUID(),null,"Second",phone,"CASH","","25.00"))).isInstanceOf(ResponseStatusException.class);
        assertThat(stock()).isEqualByComparingTo("8");
    }
    @Test void invoiceCopiesAreTrackedAcrossStaffAndReadsDoNotConsumeOriginal() {
        var sale = controller.create(auth,sale());
        UUID id = (UUID) sale.get("id");
        controller.invoice(auth,id);
        var original = controller.issueInvoiceCopy(auth,id);
        assertThat(original).containsEntry("copy_number",1).containsEntry("is_reprint",false);
        role("CUSTOMER_SERVICE");
        var copy = controller.issueInvoiceCopy(auth,id);
        assertThat(copy).containsEntry("copy_number",2).containsEntry("is_reprint",true);
        assertThat(copy.get("copy_issued_at")).isNotNull();
        assertThat(copy.get("total")).isEqualTo(original.get("total"));
        assertThat(stock()).isEqualByComparingTo("8");
        assertThat(jdbc.sql("select count(*) from audit_events where entity_id=:id and action='INVOICE_COPY_ISSUED'")
                .param("id",id.toString()).query(Integer.class).single()).isEqualTo(2);
    }
    @Test void unauthorizedInvoiceCopyCannotConsumeOriginal() {
        var sale = controller.create(auth,sale()); UUID id=(UUID)sale.get("id");
        role("CUSTOMER");
        assertThatThrownBy(() -> controller.issueInvoiceCopy(auth,id)).isInstanceOf(AccessDeniedException.class);
        role("BRANCH_MANAGER");
        jdbc.sql("delete from employee_branches where user_id=:u").param("u",employee).update();
        assertThatThrownBy(() -> controller.issueInvoiceCopy(auth,id)).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.sql("select invoice_copy_count from orders where id=:id").param("id",id).query(Integer.class).single()).isZero();
    }

}
