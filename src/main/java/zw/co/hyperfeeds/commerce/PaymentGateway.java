package zw.co.hyperfeeds.commerce;
import java.math.BigDecimal;
public interface PaymentGateway {
 Payment start(String orderReference, BigDecimal amount, String currency, String phoneNumber);
 PaymentStatus poll(String pollUrl);
 record Payment(String reference,String pollUrl,String instructions){}
 record PaymentStatus(boolean paid,String status,String paynowReference){}
}
