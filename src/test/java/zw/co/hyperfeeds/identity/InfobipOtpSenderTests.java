package zw.co.hyperfeeds.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

class InfobipOtpSenderTests {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsOtpWithoutLoggingOrPersistingIt() throws IOException {
        var requestBody = new AtomicReference<String>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sms/3/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("App test-key");
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();

        sender().send("+263771234567", "482910");

        assertThat(requestBody.get())
                .contains("263771234567", "482910", "447491163443")
                .doesNotContain("+263771234567");
    }

    @Test
    void returnsSafeErrorWhenProviderRejectsMessage() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sms/3/messages", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() -> sender().send("+263771234567", "482910"))
                .isInstanceOfSatisfying(IdentityException.class, exception -> {
                    assertThat(exception.status.value()).isEqualTo(502);
                    assertThat(exception.code).isEqualTo("OTP_DELIVERY_FAILED");
                    assertThat(exception.getMessage()).doesNotContain("482910");
                });
    }

    private InfobipOtpSender sender() {
        return new InfobipOtpSender(RestClient.builder(),
                "http://localhost:" + server.getAddress().getPort(), "test-key", "447491163443");
    }
}
