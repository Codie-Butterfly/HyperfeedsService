package zw.co.hyperfeeds.identity;

public interface OtpSender {
    void send(String phoneNumber, String code);
}
