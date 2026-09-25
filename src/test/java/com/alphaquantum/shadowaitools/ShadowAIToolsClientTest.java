package com.alphaquantum.shadowaitools;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ShadowAIToolsClientTest {
  private HttpServer server;

  private ShadowAIToolsClient stub(int status, String body) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        ex -> {
          try {
            if (status < 400) {
                assertEquals("test-key", ex.getRequestHeaders().getFirst("X-API-Key"));
            }
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
          } catch (AssertionError e) {
            ex.sendResponseHeaders(500, -1);
          } finally {
            ex.close();
          }
        });
    server.start();
    return ShadowAIToolsClient.builder()
        .apiKey("test-key")
        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
        .timeout(Duration.ofSeconds(5))
        .build();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void rejectsEmptyKey() {
    assertThrows(IllegalArgumentException.class, () -> new ShadowAIToolsClient(""));
  }

  @Test
  void sendsKeyAndParsesJson() throws Exception {
    Map<String, Object> r = stub(200, "{\"ok\":true}").check("example.com");
    assertEquals(Boolean.TRUE, r.get("ok"));
  }

  @Test
  void httpErrorBecomesApiException() throws Exception {
    ApiException e =
        assertThrows(ApiException.class, () -> stub(429, "slow down").check("example.com"));
    assertEquals(429, e.getStatusCode());
    assertEquals("slow down", e.getBody());
  }
}
