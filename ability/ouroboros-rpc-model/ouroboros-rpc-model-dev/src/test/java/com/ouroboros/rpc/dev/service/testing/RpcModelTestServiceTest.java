package com.ouroboros.rpc.dev.service.testing;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.rpc.dev.service.impl.RpcMetadataDevelopServiceImpl;
import com.ouroboros.rpc.dev.service.impl.RpcAuthCredentialMetadataDevelopServiceImpl;
import com.ouroboros.rpc.execution.RpcInvocationExecutor;
import com.ouroboros.rpc.execution.protocol.OpenApiRpcProtocolInvoker;
import com.ouroboros.rpc.util.HttpRpcRequestBuilder;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.file.VCSFile;
import io.vavr.control.Try;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RpcModelTestServiceTest {
  private final Map<String, String> files = new LinkedHashMap<>();
  private final AtomicInteger mainCalls = new AtomicInteger();
  private final AtomicInteger tokenCalls = new AtomicInteger();
  private final List<String> bodies = java.util.Collections.synchronizedList(new ArrayList<>());
  private final List<String> authorization = java.util.Collections.synchronizedList(new ArrayList<>());
  private HttpServer server;
  private RpcModelTestService service;
  private MockEnvironment environment;
  private ConfigurationService config;
  private String base;
  private int status = 200;
  private final java.util.Queue<Integer> statuses = new java.util.concurrent.ConcurrentLinkedQueue<>();
  private long delayMs;
  private Runnable afterRead;
  private String branch = "dev";
  private String response = "{\"ok\":true,\"password\":\"must-hide\"}";

  @BeforeEach
  void setup() throws Exception {
    DevAppHolder.setAppName("app-one");
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/main", exchange -> {
      mainCalls.incrementAndGet();
      if (delayMs > 0) {
        try { Thread.sleep(delayMs); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new java.io.IOException(error); }
      }
      bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      byte[] data = response.getBytes(StandardCharsets.UTF_8);
      Integer next = statuses.poll();
      exchange.sendResponseHeaders(next == null ? status : next, data.length);
      exchange.getResponseBody().write(data); exchange.close();
    });
    server.createContext("/token", exchange -> {
      tokenCalls.incrementAndGet();
      exchange.getRequestBody().readAllBytes();
      byte[] data = "{\"access_token\":\"per-click-token\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, data.length);
      exchange.getResponseBody().write(data); exchange.close();
    });
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
    var models = mock(RpcMetadataDevelopServiceImpl.class, CALLS_REAL_METHODS);
    var credentials = mock(RpcAuthCredentialMetadataDevelopServiceImpl.class, CALLS_REAL_METHODS);
    var vcs = mock(VCSManager.class);
    when(vcs.getCurrentBranch()).thenAnswer(ignore -> Try.success(branch));
    doReturn(vcs).when(models).getVcsManager();
    doAnswer(invocation -> file("rpc-model/" + invocation.getArgument(0) + ".rpc.json"))
        .when(models).getModelVCSFile(anyString());
    doAnswer(invocation -> file("rpc-model/auth/" + invocation.getArgument(0) + ".rpc.auth.json"))
        .when(credentials).getModelVCSFile(anyString());
    config = mock(ConfigurationService.class);
    when(config.get(eq(String.class), anyString())).thenReturn(Optional.empty());
    environment = new MockEnvironment().withProperty("service.host", base);
    var executor = new RpcInvocationExecutor(List.of(new OpenApiRpcProtocolInvoker(new HttpRpcRequestBuilder(config, environment))), List.of());
    service = new RpcModelTestService(new RpcModelTestSnapshotReader(models, credentials), executor, config, environment);
    putModel("Lookup", "/main", null, 0, List.of(parameter("id", "body", "$recordId", true)));
  }

  @AfterEach
  void cleanup() { server.stop(0); DevAppHolder.removeAppName(); }

  @Test
  void semanticsDiscoversTopLevelConfigurationAndIgnoresNonTemplateLeaves() {
    var model = Json.toMap(files.get(key("rpc-model/Lookup.rpc.json")));
    model.put("timeout", "#{rpc.timeout}"); model.put("retry", "#{rpc.retry}");
    model.put("description", "${not valid SpEL[}");
    model.put("request", Map.of("url", "#{service.host}/main", "method", "#{http.method}", "parameters",
        List.of(parameter("$literalName", "body", "$recordId", true))));
    files.put(key("rpc-model/Lookup.rpc.json"), Json.toJsonString(model));
    var init = service.initialize("Lookup", Map.of());
    assertEquals(List.of("recordId"), init.variables().stream().map(RpcModelTestInputs.Variable::name).toList());
    assertEquals(java.util.Set.of("service.host", "http.method", "rpc.timeout", "rpc.retry"),
        init.configItems().stream().map(RpcModelTestInputs.ConfigItem::name).collect(java.util.stream.Collectors.toSet()));
    assertEquals(0, mainCalls.get()); assertEquals(0, tokenCalls.get());
  }

  @Test
  void semanticsResolvesConfiguredCredentialAndModeBeforeCollectingDependencies() {
    environment.withProperty("credential.name", "Credential").withProperty("token.mode", "static");
    putCredential("AccessToken", Map.of("accessToken", Map.of("mode", "#{token.mode}", "staticAccessToken", "#{token.value}")));
    putModel("Lookup", "/main", "#{credential.name}", 0, List.of());
    var init = service.initialize("Lookup", Map.of());
    assertTrue(init.configItems().stream().anyMatch(item -> item.name().equals("token.value")));
    assertEquals(0, mainCalls.get()); assertEquals(0, tokenCalls.get());
  }

  @Test
  void semanticsInitializationDoesNotConstructConfiguredCredentials() {
    environment.withProperty("placement", "header").withProperty("header", "X-Key");
    putCredential("PlainCredentials", Map.of("plainCredentials", Map.of("credentials",
        List.of(Map.of("in", "#{placement}", "name", "#{header}", "value", "#{secret}")))));
    putModel("Lookup", "/main", "Credential", 0, List.of());
    var init = service.initialize("Lookup", Map.of());
    assertTrue(init.configItems().stream().anyMatch(item -> item.name().equals("secret")));
    assertEquals(0, mainCalls.get()); assertEquals(0, tokenCalls.get());
  }

  @Test
  void semanticsMissingStructuralConfigCanBeCompletedWithoutAnyInitializationCalls() {
    putCredential("AccessToken", Map.of("accessToken", Map.of("mode", "#{token.mode}", "staticAccessToken", "#{token.value}")));
    putModel("Lookup", "/main", "#{credential.name}", 0, List.of());
    var partial = service.initialize("Lookup", Map.of());
    assertFalse(partial.ready()); assertTrue(partial.testable());
    assertEquals(List.of("credential.name"), partial.missingConfigKeys());
    var credential = service.initialize("Lookup", Map.of("credential.name", "Credential"));
    assertFalse(credential.ready());
    assertEquals(List.of("token.mode", "token.value"), credential.missingConfigKeys());
    var values = Map.of("credential.name", "Credential", "token.mode", "static", "token.value", "fixture-private-token");
    var ready = service.initialize("Lookup", values);
    assertTrue(ready.ready()); assertTrue(ready.missingConfigKeys().isEmpty());
    assertFalse(Json.toJsonString(ready).contains("fixture-private-token"));
    assertEquals(0, mainCalls.get()); assertEquals(0, tokenCalls.get());
    assertTrue(service.execute("Lookup", new RpcModelTestRequest(ready.revision(), Map.of(), values)).success());
    assertEquals(List.of("Bearer fixture-private-token"), authorization);
  }

  @Test
  void semanticsConfiguredModeAndTokenModelChangesInvalidateBeforeNetworkAndPruneInputs() {
    putCredential("AccessToken", Map.of("accessToken", Map.of("mode", "#{token.mode}", "request", Map.of("modelName", "#{token.model}"),
        "accessTokenPath", "$.access_token", "expiresConfig", Map.of("expiresValueSource", "response", "expiresPath", "$.expires_in"))));
    putModel("Lookup", "/main", "Credential", 0, List.of());
    putModel("Token", "/token", null, 0, List.of(parameter("first", "body", "$firstInput", true)));
    putModel("OtherToken", "/token", null, 0, List.of(parameter("second", "body", "$secondInput", true)));
    var firstValues = Map.of("token.mode", "dynamic", "token.model", "Token");
    var first = service.initialize("Lookup", firstValues);
    assertTrue(first.ready());
    assertEquals(List.of("firstInput"), first.variables().stream().map(RpcModelTestInputs.Variable::name).toList());
    var secondValues = Map.of("token.mode", "dynamic", "token.model", "OtherToken");
    var stale = service.execute("Lookup", new RpcModelTestRequest(first.revision(), Map.of("firstInput", "one"), secondValues));
    assertEquals("RESOURCE_CHANGED", stale.error().reason());
    assertEquals(0, mainCalls.get()); assertEquals(0, tokenCalls.get());
    var second = service.initialize("Lookup", secondValues);
    assertNotEquals(first.revision(), second.revision());
    assertEquals(List.of("secondInput"), second.variables().stream().map(RpcModelTestInputs.Variable::name).toList());
    assertTrue(service.execute("Lookup", new RpcModelTestRequest(second.revision(), Map.of("secondInput", "two"), secondValues)).success());
    assertEquals(1, mainCalls.get()); assertEquals(1, tokenCalls.get());
  }

  @Test
  void semanticsConfigurationIsMemoizedAndOrdinarySecretsAreNotVersions() {
    putCredential("AccessToken", Map.of("accessToken", Map.of("mode", "static", "staticAccessToken", "#{token.value}")));
    putModel("Lookup", "/main", "#{credential.name}", 0, List.of());
    when(config.get(String.class, "credential.name")).thenReturn(Optional.of("Credential"), Optional.of("Missing"));
    var first = service.initialize("Lookup", Map.of("token.value", "first-secret"));
    assertTrue(first.ready());
    verify(config, times(1)).get(String.class, "credential.name");
    var second = service.initialize("Lookup", Map.of("credential.name", "Credential", "token.value", "second-secret"));
    assertEquals(first.revision(), second.revision());
    assertFalse(Json.toJsonString(second).contains("second-secret"));
    assertEquals(0, mainCalls.get()); assertEquals(0, tokenCalls.get());
  }

  @Test
  void semanticsJsonPathsAndDescriptionsAreNeverParsedAsVariableExpressions() {
    putCredential("AccessToken", Map.of("accessToken", Map.of("mode", "dynamic", "request", Map.of("modelName", "Token"),
        "accessTokenPath", "$['data']['access_token']", "expiresConfig", Map.of("expiresPath", "#{expires.path}")),
        "description", "${invalid[}"));
    putModel("Lookup", "/main", "Credential", 0, List.of());
    putModel("Token", "/token", null, 0, List.of());
    var init = service.initialize("Lookup", Map.of());
    assertTrue(init.variables().isEmpty());
    assertTrue(init.configItems().stream().anyMatch(item -> item.name().equals("expires.path")));
    assertFalse(init.ready()); assertEquals(List.of("expires.path"), init.missingConfigKeys());
  }

  @Test
  void semanticsUnsupportedSpiTypesAreRejectedDuringReadOnlyPreparation() {
    var model = Json.toMap(files.get(key("rpc-model/Lookup.rpc.json")));
    model.put("type", "UnsupportedProtocol");
    files.put(key("rpc-model/Lookup.rpc.json"), Json.toJsonString(model));
    assertEquals("RESOURCE_UNAVAILABLE", assertThrows(RpcModelTestFailure.class,
        () -> service.initialize("Lookup", Map.of())).toError().reason());
    putModel("Lookup", "/main", "Credential", 0, List.of());
    putCredential("UnsupportedAuth", Map.of());
    assertEquals("AUTHENTICATION_FAILED", assertThrows(RpcModelTestFailure.class,
        () -> service.initialize("Lookup", Map.of())).toError().reason());
    assertEquals(0, mainCalls.get()); assertEquals(0, tokenCalls.get());
  }

  @Test
  void initializationDiscoversInputsWithoutTargetCallsAndExecutionUsesLatestUncommittedContent() {
    var init = service.initialize("Lookup", Map.of());
    assertTrue(init.testable());
    assertEquals("development-platform", init.executionEnvironment());
    assertEquals("recordId", init.variables().getFirst().name());
    assertEquals("service.host", init.configItems().getFirst().name());
    assertTrue(init.configItems().getFirst().hasDefault());
    assertFalse(Json.toJsonString(init).contains(base));
    assertEquals(0, mainCalls.get()); assertEquals(0, tokenCalls.get());
    var result = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of("recordId", "R001"), Map.of()));
    assertTrue(result.success(), () -> String.valueOf(result.error()));
    assertEquals(1, mainCalls.get());
    assertEquals("R001", Json.toMap(bodies.getFirst()).get("id"));
    assertEquals(200, result.response().status());
    assertEquals(1, result.attemptCount());
    assertEquals(bodies.getFirst(), result.request().body().text());
    assertFalse(Json.toJsonString(result).contains("must-hide"));
    assertTrue(result.response().body().text().contains("***"));
  }

  @Test
  void changedDependenciesAndUnknownInputsFailBeforeAnyTargetCall() {
    var init = service.initialize("Lookup", Map.of());
    putModel("Lookup", "/main", null, 1, List.of(parameter("id", "body", "$recordId", true)));
    var changed = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of("recordId", 1), Map.of()));
    assertEquals("RESOURCE_CHANGED", changed.error().reason()); assertNull(changed.request());
    var fresh = service.initialize("Lookup", Map.of());
    var unknown = service.execute("Lookup", new RpcModelTestRequest(fresh.revision(), Map.of("url", "http://other"), Map.of()));
    assertEquals("INPUT_INVALID", unknown.error().reason());
    var missing = service.execute("Lookup", new RpcModelTestRequest(fresh.revision(), Map.of(), Map.of()));
    assertEquals("INPUT_INVALID", missing.error().reason());
    var config = service.execute("Lookup", new RpcModelTestRequest(fresh.revision(), Map.of("recordId", 1), Map.of("unknown", "secret")));
    assertEquals("INPUT_INVALID", config.error().reason());
    assertEquals(0, mainCalls.get()); assertEquals(0, tokenCalls.get());
  }

  @Test
  void testInvocationDoesNotReplayWithoutSavedSafeContract() {
    status = 500; response = "{\"message\":\"result unknown\"}";
    putModel("Lookup", "/main", null, 2, List.of());
    var init = service.initialize("Lookup", Map.of());

    var result = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of(), Map.of()));

    assertFalse(result.success());
    assertEquals(1, mainCalls.get());
    assertEquals(1, result.attemptCount());
  }

  @Test
  void actualNonSuccessResponsesAndEveryRetryArePreserved() {
    status = 422; response = "{\"message\":\"bad input\"}";
    putModel("Lookup", "/main", null, 2, List.of());
    var model = Json.toMap(files.get(key("rpc-model/Lookup.rpc.json")));
    model.put("retrySafe", true);
    files.put(key("rpc-model/Lookup.rpc.json"), Json.toJsonString(model));
    var init = service.initialize("Lookup", Map.of());
    var result = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of(), Map.of()));
    assertFalse(result.success());
    assertEquals("REMOTE_HTTP_ERROR", result.error().reason());
    assertEquals(3, mainCalls.get()); assertEquals(3, result.attemptCount());
    assertEquals(422, result.response().status());
    assertEquals(response, result.response().body().text());
    assertTrue(result.attempts().stream().allMatch(attempt -> attempt.response().status() == 422));
  }

  @Test
  void transientFailuresRetryToSuccessWhileTlsAndConnectionFailuresKeepNullResponse() throws Exception {
    putModel("Lookup", "/main", null, 2, List.of());
    statuses.addAll(List.of(500, 500, 200));
    var model = Json.toMap(files.get(key("rpc-model/Lookup.rpc.json")));
    model.put("retrySafe", true);
    files.put(key("rpc-model/Lookup.rpc.json"), Json.toJsonString(model));
    var init = service.initialize("Lookup", Map.of());
    var recovered = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of(), Map.of()));
    assertTrue(recovered.success()); assertEquals(3, mainCalls.get());
    assertEquals(List.of(500, 500, 200), recovered.attempts().stream().map(attempt -> attempt.response().status()).toList());
    putModel("Lookup", "/main", null, 0, List.of());
    init = service.initialize("Lookup", Map.of());
    var tls = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of(),
        Map.of("service.host", base.replace("http:", "https:"))));
    assertFalse(tls.success()); assertNull(tls.response()); assertEquals(1, tls.attemptCount());
    int port;
    try (var reserved = new java.net.ServerSocket(0)) { port = reserved.getLocalPort(); }
    var refused = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of(),
        Map.of("service.host", "http://127.0.0.1:" + port)));
    assertEquals("CALL_FAILED", refused.error().reason()); assertNull(refused.response());
    assertEquals(3, mainCalls.get());
  }

  @Test
  void dynamicTokenCallsShareSnapshotAndHaveOnlyPerClickCache() {
    putCredential("AccessToken", Map.of("accessToken", Map.of("mode", "dynamic", "request", Map.of("modelName", "Token"),
        "accessTokenPath", "$.access_token", "expiresConfig", Map.of("expiresValueSource", "response", "expiresPath", "$.expires_in"))));
    putModel("Lookup", "/main", "Credential", 1, List.of());
    putModel("Token", "/token", null, 0, List.of());
    var model = Json.toMap(files.get(key("rpc-model/Lookup.rpc.json")));
    model.put("retrySafe", true);
    files.put(key("rpc-model/Lookup.rpc.json"), Json.toJsonString(model));
    var init = service.initialize("Lookup", Map.of());
    assertEquals(0, tokenCalls.get());
    var tokenVersion = init.revision();
    status = 503;
    var first = service.execute("Lookup", new RpcModelTestRequest(tokenVersion, Map.of(), Map.of()));
    assertEquals(1, tokenCalls.get()); assertEquals(2, mainCalls.get());
    assertEquals(1, first.authenticationCalls().size());
    assertEquals(List.of("Bearer per-click-token", "Bearer per-click-token"), authorization);
    assertFalse(Json.toJsonString(first).contains("per-click-token"));
    assertNull(first.authenticationCalls().getFirst().response().body().text());
    status = 200;
    assertTrue(service.execute("Lookup", new RpcModelTestRequest(tokenVersion, Map.of(), Map.of())).success());
    assertEquals(2, tokenCalls.get());
    putModel("Token", "/token", null, 1, List.of());
    var changed = service.execute("Lookup", new RpcModelTestRequest(tokenVersion, Map.of(), Map.of()));
    assertEquals("RESOURCE_CHANGED", changed.error().reason()); assertEquals(2, tokenCalls.get());
  }

  @Test
  void basicPlainAndStaticTokenUsePerCallOverridesWithoutWritingConfiguration() {
    var definitions = List.of(
        Map.<String, Object>of("type", "HttpBasic", "extra", Map.of("httpBasic", Map.of("username", "$username", "password", "#{password}"))),
        Map.<String, Object>of("type", "PlainCredentials", "extra", Map.of("plainCredentials", Map.of("credentials",
            List.of(parameter("Authorization", "header", "#{password}", false))))),
        Map.<String, Object>of("type", "AccessToken", "extra", Map.of("accessToken", Map.of("mode", "static", "staticAccessToken", "#{password}")))
    );
    for (var definition : definitions) {
      putCredential(String.valueOf(definition.get("type")), definition.get("extra"));
      putModel("Lookup", "/main", "Credential", 0, List.of());
      var init = service.initialize("Lookup", Map.of());
      var variables = "HttpBasic".equals(definition.get("type")) ? Map.<String, Object>of("username", "alice") : Map.<String, Object>of();
      var result = service.execute("Lookup", new RpcModelTestRequest(init.revision(), variables, Map.of("password", "private-override")));
      assertTrue(result.success(), () -> String.valueOf(result.error()));
      assertFalse(Json.toJsonString(result).contains("private-override"));
      assertFalse(Json.toJsonString(result).contains("alice"));
    }
    assertEquals("Basic YWxpY2U6cHJpdmF0ZS1vdmVycmlkZQ==", authorization.get(0));
    assertEquals("private-override", authorization.get(1));
    assertEquals("Bearer private-override", authorization.get(2));
    verify(config, never()).get("password");
    assertNull(environment.getProperty("password"));
  }

  @Test
  void disabledModelsCyclesInvalidExpressionsAndMissingCredentialsNeverCallTargets() {
    var disabled = Json.toMap(files.get(key("rpc-model/Lookup.rpc.json")));
    disabled.put("isEnabled", false); files.put(key("rpc-model/Lookup.rpc.json"), Json.toJsonString(disabled));
    assertFalse(service.initialize("Lookup", Map.of()).testable());
    putModel("Lookup", "/main", "missing", 0, List.of());
    assertThrows(RpcModelTestFailure.class, () -> service.initialize("Lookup", Map.of()));
    putCredential("AccessToken", Map.of("accessToken", Map.of("request", Map.of("modelName", "Lookup"))));
    putModel("Lookup", "/main", "Credential", 0, List.of());
    assertThrows(RpcModelTestFailure.class, () -> service.initialize("Lookup", Map.of()));
    putModel("Lookup", "/main", null, 0, List.of(parameter("bad", "body", "${broken[}", false)));
    assertThrows(RpcModelTestFailure.class, () -> service.initialize("Lookup", Map.of()));
    assertEquals(0, mainCalls.get()); assertEquals(0, tokenCalls.get());
  }

  @Test
  void requestLimitAndExpressionFailureAreDistinguishedFromTargetFailure() {
    environment.withProperty("ouroboros.rpc.developer-test.max-request-bytes", "8");
    var init = service.initialize("Lookup", Map.of());
    var limited = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of("recordId", "a-long-input"), Map.of()));
    assertEquals("REQUEST_INVALID", limited.error().reason());
    putModel("Lookup", "/main", null, 0, List.of(parameter("bad", "body", "${1 / 0}", false)));
    var second = service.initialize("Lookup", Map.of());
    var invalid = service.execute("Lookup", new RpcModelTestRequest(second.revision(), Map.of(), Map.of()));
    assertEquals("EVALUATION_FAILED", invalid.error().reason());
    assertEquals(0, mainCalls.get());
  }

  @Test
  void snapshotIsScopedToApplicationEvenForIdenticalNames() {
    var first = service.initialize("Lookup", Map.of());
    DevAppHolder.setAppName("app-two");
    putModel("Lookup", "/main", null, 0, List.of(parameter("id", "body", "$otherId", true)));
    var second = service.initialize("Lookup", Map.of());
    assertNotEquals(first.revision(), second.revision());
    assertEquals("otherId", second.variables().getFirst().name());
    var result = service.execute("Lookup", new RpcModelTestRequest(first.revision(), Map.of("recordId", 1), Map.of()));
    assertEquals("RESOURCE_CHANGED", result.error().reason()); assertEquals(0, mainCalls.get());
  }

  @Test
  void hmacUsesOriginalSignedBodyBytesAndMasksSignatureAndCredentialValues() {
    putCredential("HmacSignature", Map.of("hmacSignature", Map.of("preset", "aliyun_rpc_v1",
        "accessKey", "#{access.key}", "secretKey", "#{secret.key}", "action", "Lookup", "version", "2026-01-01")));
    putModel("Lookup", "/main", "Credential", 0, List.of(parameter("id", "body", "$recordId", true)));
    var init = service.initialize("Lookup", Map.of());
    assertEquals(0, mainCalls.get());
    var result = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of("recordId", "R001"),
        Map.of("access.key", "private-ak", "secret.key", "private-sk")));
    assertTrue(result.success(), () -> String.valueOf(result.error()));
    assertEquals(bodies.getFirst(), result.request().body().text());
    assertTrue(result.request().url().contains("Signature=***"));
    assertFalse(Json.toJsonString(result).contains("private-ak"));
    assertFalse(Json.toJsonString(result).contains("private-sk"));
  }

  @Test
  void responseDecodeFailureKeepsActualStatusAndBudgetStopsFurtherRetries() {
    response = "invalid-json";
    putModel("Lookup", "/main", null, 0, List.of());
    var init = service.initialize("Lookup", Map.of());
    var decoded = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of(), Map.of()));
    assertEquals("DECODE_FAILED", decoded.error().reason());
    assertEquals(200, decoded.response().status());
    assertNull(decoded.response().body().text());
    response = "{}"; delayMs = 300;
    environment.withProperty("ouroboros.rpc.developer-test.max-duration-ms", "70");
    putModel("Lookup", "/main", null, 10000, List.of());
    var next = service.initialize("Lookup", Map.of());
    long start = System.nanoTime();
    var timeout = service.execute("Lookup", new RpcModelTestRequest(next.revision(), Map.of(), Map.of()));
    assertEquals("DEADLINE_EXCEEDED", timeout.error().reason());
    assertTrue((System.nanoTime() - start) / 1_000_000 < 1500);
    assertTrue(timeout.attemptCount() <= 1);
    assertNull(timeout.response());
  }

  @Test
  void unstableReadsAndBranchChangesRejectBeforeCallingAnyTarget() {
    afterRead = () -> putModel("Lookup", "/main", null, 1, List.of());
    assertEquals("RESOURCE_CHANGED", assertThrows(RpcModelTestFailure.class,
        () -> service.initialize("Lookup", Map.of())).toError().reason());
    var initialized = service.initialize("Lookup", Map.of());
    branch = "other";
    var result = service.execute("Lookup", new RpcModelTestRequest(initialized.revision(), Map.of(), Map.of()));
    assertEquals("RESOURCE_CHANGED", result.error().reason());
    assertEquals(0, mainCalls.get()); assertEquals(0, tokenCalls.get());
  }

  @Test
  void emptyOverridesAndConcurrentConfigViewsNeverModifyDefaults() throws Exception {
    putModel("Lookup", "/main", null, 0, List.of(parameter("input", "body", "#{input}", false)));
    when(config.get(String.class, "input")).thenReturn(Optional.of("platform-default"));
    var initialized = service.initialize("Lookup", Map.of());
    try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks = List.of("", "click-two").stream().map(value -> workers.submit(() -> {
        DevAppHolder.setAppName("app-one");
        try { return service.execute("Lookup", new RpcModelTestRequest(initialized.revision(), Map.of(), Map.of("input", value))); }
        finally { DevAppHolder.removeAppName(); }
      })).toList();
      for (var task : tasks) { var result = task.get(); assertTrue(result.success(), () -> String.valueOf(result.error())); }
    }
    assertEquals(java.util.Set.of("", "click-two"), bodies.stream().map(body -> Json.toMap(body).get("input"))
        .collect(java.util.stream.Collectors.toSet()));
    assertEquals(Optional.of("platform-default"), config.get(String.class, "input"));
  }

  @Test
  void noContentTruncationAndResponseLimitsHaveDistinctEvidence() {
    putModel("Lookup", "/main", null, 0, List.of());
    var init = service.initialize("Lookup", Map.of());
    status = 204; response = "";
    var empty = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of(), Map.of()));
    assertTrue(empty.success(), () -> String.valueOf(empty.error())); assertEquals(204, empty.response().status());
    status = 200; response = "{\"data\":\"" + "x".repeat(300) + "\"}";
    environment.setProperty("ouroboros.rpc.developer-test.max-preview-bytes", "80");
    var truncated = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of(), Map.of()));
    assertTrue(truncated.success()); assertTrue(truncated.response().body().truncated());
    assertEquals(80, truncated.response().body().text().getBytes(StandardCharsets.UTF_8).length);
    response = "{\"data\":\"" + "汉".repeat(300) + "\"}";
    var unicode = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of(), Map.of()));
    assertTrue(unicode.success()); assertTrue(unicode.response().body().truncated());
    assertTrue(unicode.response().body().text().getBytes(StandardCharsets.UTF_8).length <= 80);
    assertFalse(unicode.response().body().text().contains("\ufffd"));
    response = "{\"data\":\"" + "x".repeat(300000) + "\"}";
    var limited = service.execute("Lookup", new RpcModelTestRequest(init.revision(), Map.of(), Map.of()));
    assertFalse(limited.success()); assertEquals("RESPONSE_LIMIT", limited.error().reason());
    assertEquals(200, limited.response().status()); assertNull(limited.response().body());
  }

  private VCSFile file(String path) {
    var file = mock(VCSFile.class);
    when(file.exists()).thenAnswer(ignore -> files.containsKey(key(path)));
    when(file.getPath()).thenReturn(path);
    when(file.getContent()).thenAnswer(ignore -> {
      var content = files.get(key(path));
      var hook = afterRead;
      afterRead = null;
      if (hook != null) hook.run();
      return content;
    });
    return file;
  }

  private String key(String path) { return DevAppHolder.getAppName() + ":" + path; }

  private void putModel(String name, String endpoint, String credential, int retry, List<?> parameters) {
    var model = new LinkedHashMap<String, Object>();
    model.put("name", name); model.put("title", name); model.put("type", "OpenAPI"); model.put("isEnabled", true);
    model.put("authCredential", credential); model.put("retry", retry); model.put("timeout", 3000);
    model.put("request", Map.of("url", "#{service.host}" + endpoint, "method", "POST", "parameters", parameters));
    files.put(key("rpc-model/" + name + ".rpc.json"), Json.toJsonString(model));
  }

  private void putCredential(String type, Object extra) {
    var credential = Json.toMap(Json.toJsonString(extra));
    credential.put("name", "Credential"); credential.put("type", type); credential.put("isEnabled", true);
    files.put(key("rpc-model/auth/Credential.rpc.auth.json"), Json.toJsonString(credential));
  }

  private Map<String, Object> parameter(String name, String in, String value, boolean required) {
    return Map.of("name", name, "in", in, "value", value, "required", required);
  }
}
