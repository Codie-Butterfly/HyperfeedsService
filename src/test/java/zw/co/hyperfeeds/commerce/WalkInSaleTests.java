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
        @Bean zw.co.hyperfeeds.bookings.ChickBookingController chickBookings(JdbcClient jdbc) { return new zw.co.hyperfeeds.bookings.ChickBookingController(jdbc); }
        @Bean WalkInChickBookingController walkInChicks(JdbcClient jdbc, zw.co.hyperfeeds.bookings.ChickBookingController bookings) { return new WalkInChickBookingController(jdbc, bookings); }
        @Bean zw.co.hyperfeeds.management.ExecutiveDashboardController executive(JdbcClient jdbc) { return new zw.co.hyperfeeds.management.ExecutiveDashboardController(jdbc); }
        @Bean WalkInSaleController controller(JdbcClient jdbc) { return new WalkInSaleController(jdbc); }
        @Bean OrderCheckoutController orders(JdbcClient jdbc) { return new OrderCheckoutController(jdbc, null); }
    }
    @Autowired zw.co.hyperfeeds.management.ExecutiveDashboardController executive;
    @Autowired WalkInChickBookingController chicks;
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


    private String chickSetup(boolean deposit) {
        jdbc.sql("update chick_booking_batches set status='CLOSED' where status='OPEN'").update();
        jdbc.sql("insert into chick_booking_batches(name,start_date,end_date,status) values('Walk-in test',current_date,current_date+7,'OPEN')").update();
        String breed="Breed-"+UUID.randomUUID();
        jdbc.sql("insert into chick_breed_configs(chick_type,breed,price_per_chick,currency,available) values('BROILER',:breed,2,'USD',true)").param("breed",breed).update();
        jdbc.sql("update system_configs set config_value=:value where config_key='CHICK_ORDER_DEPOSIT_ENABLED'").param("value",deposit?"true":"false").update();
        jdbc.sql("update system_configs set config_value='25' where config_key='CHICK_ORDER_DEPOSIT_PERCENTAGE'").update();
        return breed;
    }
    private WalkInChickBookingController.Request chickRequest(String breed, BigDecimal paid) {
        return new WalkInChickBookingController.Request(UUID.randomUUID(),branch,null,"Walk-in chick customer","",
            "BROILER",breed,100,new BigDecimal("200.00"),paid,"USD","CASH","");
    }
    @Test void walkInChickDepositBalanceAndReprints() {
        var request=chickRequest(chickSetup(true),new BigDecimal("50.00"));
        var booking=chicks.create(auth,request); UUID id=(UUID)booking.get("id");
        assertThat(booking.get("customer_name")).isEqualTo("Walk-in chick customer");
        assertThat((BigDecimal)booking.get("amount_owed")).isEqualByComparingTo("150.00");
        assertThat(chicks.create(auth,request).get("id")).isEqualTo(id);
        assertThat(jdbc.sql("select user_id from chick_bookings where id=:id").param("id",id).query().singleRow().get("user_id")).isNull();
        assertThat(chicks.issueCopy(auth,id).get("copy_number")).isEqualTo(1);
        assertThat(chicks.issueCopy(auth,id).get("is_reprint")).isEqualTo(true);
        var payment=new WalkInChickBookingController.BalanceRequest(new BigDecimal("150"),"USD","CARD","POS123");
        assertThat((BigDecimal)chicks.balancePayment(auth,id,payment).get("amount_owed")).isEqualByComparingTo("0");
        chicks.balancePayment(auth,id,payment);
        assertThat(jdbc.sql("select count(*) from payments where chick_booking_id=:id").param("id",id).query(Integer.class).single()).isEqualTo(2);
    }
    @Test void walkInChickUnderpaymentRollsBackAndClosedBatchRejects() {
        var request=chickRequest(chickSetup(true),new BigDecimal("49.00"));
        int before=jdbc.sql("select count(*) from chick_bookings").query(Integer.class).single();
        assertThatThrownBy(()->chicks.create(auth,request)).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.sql("select count(*) from chick_bookings").query(Integer.class).single()).isEqualTo(before);
        jdbc.sql("update chick_booking_batches set status='CLOSED' where status='OPEN'").update();
        assertThatThrownBy(()->chicks.create(auth,request)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void walkInChickPermissionsAndNoDeposit() {
        var request=chickRequest(chickSetup(false),BigDecimal.ZERO);
        role("CUSTOMER");
        assertThatThrownBy(()->chicks.create(auth,request)).isInstanceOf(AccessDeniedException.class);
        role("BRANCH_MANAGER");
        jdbc.sql("delete from employee_branches where user_id=:user").param("user",employee).update();
        assertThatThrownBy(()->chicks.create(auth,request)).isInstanceOf(ResponseStatusException.class);
        role("CUSTOMER_SERVICE");
        var result=chicks.create(auth,request);
        assertThat(result.get("status")).isEqualTo("CONFIRMED");
        assertThat((BigDecimal)result.get("amount_paid")).isEqualByComparingTo("0");
    }

    private WalkInSaleController.SaleRequest discounted(String type,String value,String reason,String paid) {
        var base=sale();
        return new WalkInSaleController.SaleRequest(base.requestId(),branch,null,base.customerName(),"",base.items(),"CASH","",new BigDecimal(paid),"USD",type,new BigDecimal(value),reason);
    }
    @Test void discountRecordedAndDashboardReconcilesNetSalesTargetsAndCurrency() {
        var request=discounted("PERCENTAGE","10","Regular customer","22.50");
        var sale=controller.create(auth,request);
        assertThat((BigDecimal)sale.get("subtotal")).isEqualByComparingTo("25");
        assertThat((BigDecimal)sale.get("discount_amount")).isEqualByComparingTo("2.50");
        assertThat(sale.get("discount_recorded_by")).isEqualTo(employee);
        assertThat(controller.create(auth,request).get("id")).isEqualTo(sale.get("id"));
        String month=java.time.YearMonth.now(java.time.ZoneId.of("Africa/Harare")).toString();
        assertThatThrownBy(()->executive.dashboard(month,"USD",branch)).isInstanceOf(AccessDeniedException.class);
        role("CEO");
        executive.target(auth,new zw.co.hyperfeeds.management.ExecutiveDashboardController.TargetRequest(month,branch,"USD",new BigDecimal("100")));
        var result=executive.dashboard(month,"USD",branch);
        Map<String,Object> summary=(Map<String,Object>)result.get("summary");
        assertThat((BigDecimal)summary.get("net_sales")).isEqualByComparingTo("22.50");
        assertThat((BigDecimal)summary.get("discounts")).isEqualByComparingTo("2.50");
        assertThat((BigDecimal)summary.get("cash_received")).isEqualByComparingTo("22.50");
        assertThat((BigDecimal)summary.get("remaining_to_target")).isEqualByComparingTo("77.50");
        assertThat((BigDecimal)summary.get("achievement_percent")).isEqualByComparingTo("22.5");
        var other=(Map<String,Object>)executive.dashboard(month,"ZAR",branch).get("summary");
        assertThat((BigDecimal)other.get("net_sales")).isEqualByComparingTo("0");
        assertThat(other.get("target")).isNull();
        var products=(List<Map<String,Object>>)result.get("products");
        assertThat(products).anyMatch(p->product.equals(p.get("id")) && ((BigDecimal)p.get("net_sales")).compareTo(new BigDecimal("22.50"))==0);
        executive.target(auth,new zw.co.hyperfeeds.management.ExecutiveDashboardController.TargetRequest(month,null,"USD",new BigDecimal("1000")));
        var company=(Map<String,Object>)executive.dashboard(month,"USD",null).get("summary");
        assertThat((BigDecimal)company.get("target")).isEqualByComparingTo("1000");
        assertThat((BigDecimal)((Map<?,?>)executive.dashboard(month,"USD",branch).get("summary")).get("target")).isEqualByComparingTo("100");
    }
    @Test void fixedDiscountAndInvalidDiscountRollback() {
        assertThatThrownBy(()->controller.create(auth,discounted("FIXED","26","Too high","0"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->controller.create(auth,discounted("PERCENTAGE","10","","22.50"))).isInstanceOf(ResponseStatusException.class);
        assertThat(stock()).isEqualByComparingTo("10");
        role("CUSTOMER_SERVICE");
        var sale=controller.create(auth,discounted("FIXED","5","Promotion","20"));
        assertThat((BigDecimal)sale.get("total")).isEqualByComparingTo("20");
        assertThat(sale.get("discount_reason")).isEqualTo("Promotion");
    }
}
