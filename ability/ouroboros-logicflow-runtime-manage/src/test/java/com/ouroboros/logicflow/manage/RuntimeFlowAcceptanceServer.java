package com.ouroboros.logicflow.manage;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;
import org.h2.tools.Server;
import org.springframework.http.HttpMethod;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshingEvent;
import com.ouroboros.logicflow.manage.web.RuntimeFlowAccess;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.util.Json;

/** Loopback acceptance fixture: real controllers, use cases, SPI, H2 and execution; trusted identity is a fixture boundary. */
public final class RuntimeFlowAcceptanceServer {
  private RuntimeFlowAcceptanceServer() { }
  public static void main(String[] args) throws Exception {
    if (args[0].equals("database")) {
      var database = Server.createTcpServer("-tcpPort", args[1], "-ifNotExists").start();
      Runtime.getRuntime().addShutdownHook(new Thread(database::stop));
      System.out.println("Shared H2 ready " + database.getURL());
      Thread.currentThread().join();
      return;
    }
    int port = Integer.parseInt(args[0]);
    var fixture = new RuntimeFlowFixture(Path.of(args[2]), new RuntimeFlowDatabaseFixture(args[1]));
    fixture.coordinator.initializeStorage(new OuroborosModelRefreshingEvent(fixture));
    fixture.coordinator.reconcile();
    fixture.observation.start();
    var mvc = RuntimeFlowHttpTest.mvc(fixture);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    var executor = Executors.newFixedThreadPool(4);
    server.createContext("/api/", exchange -> {
      try {
        var permissions = switch (exchange.getRequestHeaders().getFirst("X-Fixture-Role") == null ? "editor" : exchange.getRequestHeaders().getFirst("X-Fixture-Role")) {
          case "reader" -> Set.of(RuntimeFlowAccess.READ);
          case "none" -> Set.<String>of();
          default -> Set.of(RuntimeFlowAccess.READ, RuntimeFlowAccess.EDIT, RuntimeFlowAccess.RELOAD, RuntimeFlowAccess.OBSERVE_READ, RuntimeFlowAccess.OBSERVE_MANAGE);
        };
        byte[] body = exchange.getRequestBody().readAllBytes();
        var response = CurrentSecurityContextHolder.withContextThrowing(RuntimeFlowHttpTest.identity("fixture-operator", permissions), () ->
            mvc.perform(request(HttpMethod.valueOf(exchange.getRequestMethod()), URI.create(exchange.getRequestURI().toString()))
                .contentType("application/json").content(body)).andReturn().getResponse());
        byte[] bytes = response.getContentAsByteArray();
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(response.getStatus(), bytes.length);
        exchange.getResponseBody().write(bytes);
      } catch (Exception failure) {
        failure.printStackTrace(System.err); exchange.sendResponseHeaders(500, -1);
      } finally { exchange.close(); }
    });
    server.createContext("/fixture/execute", exchange -> {
      try {
        byte[] bytes = Json.toJsonString(java.util.Map.of("instanceId", fixture.coordinator.instanceId(),
            "result", fixture.runtime.getFlowModel(exchange.getRequestURI().getQuery() != null && exchange.getRequestURI().getQuery().equals("flow=child") ? "child" : "parent").orElseThrow().execute("input").get())).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
      } catch (Exception failure) { failure.printStackTrace(System.err); exchange.sendResponseHeaders(500, -1); }
      finally { exchange.close(); }
    });
    server.setExecutor(executor);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.stop(0); executor.shutdownNow(); fixture.close(); }));
    server.start();
    System.out.println("Runtime Flow acceptance fixture ready: " + server.getAddress() + " boot=" + fixture.coordinator.instanceId());
  }
}
