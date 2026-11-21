package com.ouroboros.dev.editor;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import com.ouroboros.devhelper.command.*;
import com.ouroboros.devhelper.metadata.*;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.transport.socket.SocketDevHelperServerTransport;
import com.ouroboros.util.Json;

class LocalEditorIntegrationTest {
  @Test void servesExistingEditorApisThroughAuthenticatedSocketAndHttp() throws Exception {
    var activeIdentity = new AtomicReference<>(new HelperIdentity("local-helper", 1000L, "local-app"));
    var epoch = new AtomicLong(1);
    var snapshotReads = new AtomicLong();
    var requests = new java.util.concurrent.CopyOnWriteArrayList<CommandRequest>();
    try (var helper = new SocketDevHelperServerTransport("127.0.0.1", 0, "helper-secret")) {
      var language = new com.ouroboros.dev.metadata.Metadata(); language.setName("spel"); language.setType("script-intelligence");
      var snapshot = new com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot(); snapshot.setSource("dev-helper-runtime"); snapshot.setRuntimeProfile("spel");
      language.setDetails(Json.toMap(Json.toJsonString(snapshot)));
      helper.subscribe(request -> {
        requests.add(request);
        var identity = activeIdentity.get();
        Object data;
        if (request.getCommand().equals("knockKnock")) {
          var payload = new HashMap<String, Object>(); MetadataRefreshProtocol.addHelperIdentity(payload, identity); payload.put("metadataEpoch", epoch.get()); data = payload;
        } else if (request.getCommand().equals("readMetadata")) {
          data = new MetadataRefreshSnapshot().setSchemaVersion(2).setAppName("demo")
              .setHelperInstanceId(identity.getInstanceId()).setHelperStartedAtEpochMillis(identity.getStartedAtEpochMillis()).setAppIncarnationId(identity.getAppIncarnationId())
              .setRefreshOperationId(request.getRefreshOperationId()).setRefreshRevision(snapshotReads.incrementAndGet()).setCollectedAtEpochMillis(System.currentTimeMillis())
              .setModules(Map.of("script-intelligence", List.of(language)));
        } else if (request.getCommand().equals("editorCatalog") && "field-types".equals(request.getParams().get("kind"))) {
          data = List.of(Map.of("name", "customField", "label", "应用字段", "isPrimaryKeyType", false, "paramsUiSchema", List.of(Map.of("type", "input-text", "name", "customParam"))));
        } else if (request.getCommand().equals("approvalCandidates")) {
          data = List.of(Map.of("value", "user-1", "label", "Alice"));
        } else { data = List.of(Map.of("type", "applicationNode", "label", "应用节点")); }
        helper.send(new CommandResult(request, data).setAppName("demo").setHelperInstanceId(identity.getInstanceId())
            .setHelperStartedAtEpochMillis(identity.getStartedAtEpochMillis()).setAppIncarnationId(identity.getAppIncarnationId()));
      });
      var app = new SpringApplication(LocalEditorConfiguration.class);
      app.setDefaultProperties(Map.of("server.address", "127.0.0.1", "server.port", "0", "ouroboros.editor.app-name", "demo",
          "ouroboros.editor.token", "editor-secret", "ouroboros.dev-helper.socket.port", helper.port(), "ouroboros.dev-helper.socket.token", "helper-secret"));
      try (var context = (ServletWebServerApplicationContext) app.run()) {
        var base = "http://127.0.0.1:" + context.getWebServer().getPort(); var client = HttpClient.newHttpClient();
        var environment = "/api/dev/demo/code-intelligence/environment";
        assertEquals(401, get(client, base + environment, false).statusCode());
        assertEquals(404, get(client, base + "/api/dev/other/code-intelligence/environment", true).statusCode());
        var response = get(client, base + environment, true);
        assertEquals(200, response.statusCode(), response.body()); assertTrue(response.body().contains("CURRENT"), response.body());
        var keyOnly = client.send(HttpRequest.newBuilder(URI.create(base + environment))
            .header("X-TideStack-Dev-Key", "editor-secret").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, keyOnly.statusCode(), keyOnly.body());
        assertTrue(keyOnly.body().contains("CURRENT"), keyOnly.body());
        for (var headers : List.of(
            List.of("X-TideStack-Dev-Key", "wrong-key"),
            List.of("X-TideStack-Dev-Key", "editor-secret", "Authorization", "Bearer editor-secret"),
            List.of("X-TideStack-Dev-Key", "editor-secret", "X-TideStack-Dev-Key", "editor-secret"))) {
          var rejected = client.send(HttpRequest.newBuilder(URI.create(base + environment))
              .headers(headers.toArray(String[]::new)).GET().build(), HttpResponse.BodyHandlers.ofString());
          assertEquals(401, rejected.statusCode(), rejected.body());
        }
        var nodes = get(client, base + "/api/dev/demo/logicflow/node-define", true);
        assertEquals(200, nodes.statusCode()); assertTrue(nodes.body().contains("applicationNode"), nodes.body());
        var fields = get(client, base + "/api/app/demo/field-types/normal", true);
        assertEquals(200, fields.statusCode()); assertTrue(fields.body().contains("customField"), fields.body());
        assertFalse(get(client, base + "/api/app/demo/field-types/primary", true).body().contains("customField"));
        var fieldSchema = get(client, base + "/api/app/demo/field-types/customField/ui-schema", true);
        assertEquals(200, fieldSchema.statusCode()); assertTrue(fieldSchema.body().contains("customParam"), fieldSchema.body());
        var candidates = get(client, base + "/api/dev/demo/approval-workflow/candidate/ApplicationUsers/items?value%5B%5D=user-1", true);
        assertEquals(200, candidates.statusCode()); assertTrue(candidates.body().contains("Alice"), candidates.body());
        assertEquals(List.of("user-1"), requests.stream().filter(request -> "approvalCandidates".equals(request.getCommand())).findFirst().orElseThrow().getParams().get("values"));
        assertTrue(get(client, base + environment, true).body().contains("CURRENT"));
        var draft = """
            {"documentId":"local-editor","documentVersion":4,"draftRevision":7,"codeSite":"configuration.value",
             "language":"template","resource":{"type":"configuration","name":"demo.key"},
             "location":{"pointer":"/defaultValue"},"draft":{"key":"demo.key","defaultValue":"${value +}"}}
            """;
        var code = client.send(HttpRequest.newBuilder(URI.create(base + "/api/dev/demo/code-intelligence/context"))
            .header("Authorization", "Bearer editor-secret").header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(draft)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, code.statusCode(), code.body());
        var payload = Json.toBean(com.fasterxml.jackson.databind.JsonNode.class, code.body());
        assertEquals(0, payload.path("code").asInt(-1), code.body());
        assertEquals("local-editor", payload.path("data").path("documentId").asText());
        assertEquals(4, payload.path("data").path("documentVersion").asInt());
        assertEquals(7, payload.path("data").path("draftRevision").asInt());
        assertEquals("CURRENT", payload.path("data").path("runtimeStatus").asText());
        assertTrue(code.body().contains("syntax-error"), code.body());
        assertEquals(1, snapshotReads.get(), "Ordinary editor requests must reuse the loaded snapshot");
        epoch.incrementAndGet();
        await().atMost(Duration.ofSeconds(7)).untilAsserted(() -> {
          assertEquals(200, get(client, base + environment, true).statusCode());
          assertEquals(2, snapshotReads.get(), "A metadata epoch change must reload the snapshot");
        });
        activeIdentity.set(new HelperIdentity("restarted-helper", 2000L, "restarted-app"));
        await().atMost(Duration.ofSeconds(7)).untilAsserted(() -> {
          var restarted = get(client, base + environment, true);
          assertEquals(200, restarted.statusCode(), restarted.body());
          assertTrue(restarted.body().contains("restarted-helper"), restarted.body());
        });
        assertEquals(3, snapshotReads.get());
        assertEquals("restarted-helper", requests.stream().filter(request -> "readMetadata".equals(request.getCommand())).reduce((first, last) -> last).orElseThrow().getHelperInstanceId());
        assertTrue(requests.stream().noneMatch(request -> "refreshMetadata".equals(request.getCommand())), "Reading editor metadata must not rebuild application contexts");
        assertFalse(context.containsBean("dataSource")); assertFalse(context.containsBean("mqBridge"));
      }
    }
  }
  private HttpResponse<String> get(HttpClient client, String uri, boolean authenticated) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create(uri));
    if (authenticated) builder.header("Authorization", "Bearer editor-secret");
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
