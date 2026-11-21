package com.ouroboros.security.externalauth.oauth2;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import static org.assertj.core.api.Assertions.*;

class OAuth2HttpClientTest {
  @Test void requestAdmissionRunsForEveryExchangeAndRefusalNeverReachesTransport() throws Exception {
    var requests = new AtomicInteger(); var admissions = new AtomicInteger();
    var server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      requests.incrementAndGet(); exchange.getRequestBody().readAllBytes();
      byte[] response = "{\"value\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      try (var output = exchange.getResponseBody()) { output.write(response); }
    });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    try {
      var blocked = new OAuth2HttpClient(1000, 1000, () -> {
        admissions.incrementAndGet(); throw new IllegalStateException("admission rejected");
      });
      assertThatThrownBy(() -> blocked.fetchUserInfo(base + "/userinfo", "token")).hasMessageContaining("admission rejected");
      assertThat(requests).hasValue(0);
      var admitted = new OAuth2HttpClient(1000, 1000, admissions::incrementAndGet);
      assertThat(admitted.exchangeCode(base + "/token", "code", null, null, "client", "secret")).containsEntry("value", "ok");
      assertThat(admitted.fetchUserInfo(base + "/userinfo", "token")).containsEntry("value", "ok");
      assertThat(admitted.fetchJwks(base + "/keys")).containsEntry("value", "ok");
      assertThat(admissions).hasValue(4); assertThat(requests).hasValue(3);
    } finally { server.stop(0); }
  }
}
