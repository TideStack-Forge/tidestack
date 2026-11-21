package com.ouroboros.dev.runtime.apisix.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.ouroboros.dev.runtime.apisix.config.ApisixGatewayProperties;
import com.ouroboros.dev.runtime.apisix.model.ApisixRoutePayload;

class RestTemplateApisixAdminClientTest {

  @Test
  void shouldPutRouteWithAdminKeyHeader() throws Exception {
    RecordingExchange recording = new RecordingExchange();
    HttpServer server = server(201, recording);
    try {
      RestTemplateApisixAdminClient client = new RestTemplateApisixAdminClient(properties(server));

      assertTrue(client.upsertRoute("rtg-docker-test-app-crm-dev", routePayload()).isSuccess());
      assertEquals("PUT", recording.method);
      assertEquals("/apisix/admin/routes/rtg-docker-test-app-crm-dev", recording.path);
      assertEquals("secret", recording.adminKey);
      assertTrue(recording.body.contains("\"hosts\":[\"crm-dev.develop.example.com\"]"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void shouldDeleteRouteWithDeleteRequest() throws Exception {
    RecordingExchange recording = new RecordingExchange();
    HttpServer server = server(200, recording);
    try {
      RestTemplateApisixAdminClient client = new RestTemplateApisixAdminClient(properties(server));

      assertTrue(client.deleteRoute("rtg-docker-test-app-crm-dev").isSuccess());
      assertEquals("DELETE", recording.method);
      assertEquals("/apisix/admin/routes/rtg-docker-test-app-crm-dev", recording.path);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void shouldTreatDeleteNotFoundAsSuccess() throws Exception {
    RecordingExchange recording = new RecordingExchange();
    HttpServer server = server(404, recording);
    try {
      RestTemplateApisixAdminClient client = new RestTemplateApisixAdminClient(properties(server));

      assertTrue(client.deleteRoute("rtg-docker-test-app-crm-dev").isSuccess());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void shouldReturnFailureForNon404ErrorResponses() throws Exception {
    RecordingExchange recording = new RecordingExchange();
    HttpServer server = server(500, recording);
    try {
      RestTemplateApisixAdminClient client = new RestTemplateApisixAdminClient(properties(server));

      assertTrue(client.upsertRoute("rtg-docker-test-app-crm-dev", routePayload()).isFailure());
    } finally {
      server.stop(0);
    }
  }

  private static HttpServer server(int status, RecordingExchange recording) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/apisix/admin/routes", exchange -> handle(exchange, status, recording));
    server.start();
    return server;
  }

  private static void handle(HttpExchange exchange, int status, RecordingExchange recording) throws IOException {
    recording.method = exchange.getRequestMethod();
    recording.path = exchange.getRequestURI().getPath();
    recording.adminKey = exchange.getRequestHeaders().getFirst("X-API-KEY");
    recording.body = read(exchange.getRequestBody());
    byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }

  private static String read(InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[256];
    int read;
    while ((read = inputStream.read(buffer)) >= 0) {
      outputStream.write(buffer, 0, read);
    }
    return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
  }

  private static ApisixGatewayProperties properties(HttpServer server) {
    ApisixGatewayProperties properties = new ApisixGatewayProperties();
    properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    properties.setAdminKey("secret");
    properties.setRouteIdPrefix("rtg");
    properties.setConnectTimeoutMillis(Integer.valueOf(1000));
    properties.setReadTimeoutMillis(Integer.valueOf(1000));
    return properties;
  }

  private static ApisixRoutePayload routePayload() {
    ApisixRoutePayload.Upstream upstream = new ApisixRoutePayload.Upstream();
    upstream.getNodes().put("ouroboros-test-app-crm-dev:80", Integer.valueOf(1));
    ApisixRoutePayload payload = new ApisixRoutePayload();
    payload.setId("rtg-docker-test-app-crm-dev");
    payload.getHosts().add("crm-dev.develop.example.com");
    payload.setUri("/*");
    payload.setUpstream(upstream);
    return payload;
  }

  static class RecordingExchange {
    private String method;
    private String path;
    private String adminKey;
    private String body;
  }
}
