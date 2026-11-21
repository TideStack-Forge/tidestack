package com.ouroboros.rpc.execution.protocol;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.StreamUtils;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.rpc.RPCModelDefinition;
import com.ouroboros.rpc.execution.RpcInvocationContext;
import com.ouroboros.rpc.execution.RpcInvocationRequest;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.execution.RpcExecutionResources;
import com.ouroboros.rpc.execution.RpcHttpExchange;
import com.ouroboros.rpc.exception.RPCException;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.metadata.RPCModelMetadata;
import com.ouroboros.rpc.model.OpenApiRpcModelDefinitionBuilder;
import com.ouroboros.rpc.security.HmacSignatureRpcAuthCredential;
import com.ouroboros.rpc.security.PlainRpcCredential;
import com.ouroboros.rpc.security.RPCAuthCredential;
import com.ouroboros.rpc.security.RPCAuthCredentialCenter;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.rpc.security.hmac.SerializedBody;
import com.ouroboros.rpc.util.HttpRpcRequestBuilder;

class OpenApiRpcProtocolInvokerTest {
  private static final int SUCCESS_PATH_TIMEOUT_MS = 5000;

  @Test void explicitResourcesResolveRequestAndCredentialAndKeepExchangeEvidence() throws Exception {
    var service = mock(ConfigurationService.class);
    var environment = new MockEnvironment().withProperty("method", "invalid-global-method")
        .withProperty("credential", "global-auth").withProperty("timeout", "invalid-global-timeout");
    var invoker = new OpenApiRpcProtocolInvoker(new HttpRpcRequestBuilder(service, environment));
    var resources = mock(RpcExecutionResources.class);
    var values = Map.of("method", "GET", "placement", "header", "header", "X-Workspace-Value", "required", "true",
        "credential", "workspace-auth", "timeout", "5000", "retry", "0", "secret", "workspace-secret");
    when(resources.getConfig(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0, String.class)));
    when(resources.remainingDurationMs()).thenReturn(5000L);
    when(resources.maxRequestBytes()).thenReturn(1024);
    var credentialMetadata = new RPCCredentialMetadata();
    credentialMetadata.setName("workspace-auth");
    credentialMetadata.setExtraProp("plainCredentials", Map.of("credentials",
        List.of(Map.of("in", "header", "name", "X-Workspace-Auth", "value", "#{secret}"))));
    var credential = new PlainRpcCredential.Builder().build(credentialMetadata, resources);
    doReturn(java.util.Optional.of(credential)).when(resources).findCredential("workspace-auth");

    try (var server = new TestHttpServer(0, "{\"ok\":true}")) {
      server.start();
      var metadata = openApiMetadata(server.getBaseUrl() + "/api/users", 5000, 0);
      metadata.setRequest(Map.of("url", server.getBaseUrl() + "/api/users", "method", "#{method}", "parameters",
          List.of(Map.of("in", "#{placement}", "name", "#{header}", "required", "#{required}", "value", "${value}"))));
      metadata.setAuthCredential("#{credential}");
      metadata.setExtraProp("timeout", "#{timeout}");
      metadata.setExtraProp("retry", "#{retry}");
      var context = new RpcInvocationContext(new RpcInvocationRequest(metadata.getName(), Map.of("value", "context-value")),
          new OpenApiRpcModelDefinitionBuilder().build(metadata), resources);
      var result = invoker.invoke(context);
      assertTrue(result.isSuccess(), () -> String.valueOf(result.getError()));
      assertEquals("context-value", server.getHeader("X-Workspace-Value"));
      assertEquals("workspace-secret", server.getHeader("X-Workspace-Auth"));
      var exchange = org.mockito.ArgumentCaptor.forClass(RpcHttpExchange.class);
      verify(resources).onExchange(exchange.capture());
      assertEquals("GET", exchange.getValue().method());
      assertEquals(200, exchange.getValue().status());
      assertEquals("{\"ok\":true}", new String(exchange.getValue().responseBody(), StandardCharsets.UTF_8));
      verify(resources).findCredential("workspace-auth");
      verifyNoInteractions(service);
    }
  }

  @Test void configuredCredentialReferenceAndTransportNumbersUseTheirResolvedValues() throws Exception {
    var service = mock(ConfigurationService.class);
    when(service.get(eq(String.class), anyString())).thenReturn(java.util.Optional.empty());
    var environment = new MockEnvironment().withProperty("credential", "configured-signature")
        .withProperty("timeout", "5000").withProperty("retry", "0");
    var invoker = new OpenApiRpcProtocolInvoker(new HttpRpcRequestBuilder(service, environment));
    try (var server = new TestHttpServer(0, "{\"ok\":true}")) {
      server.start();
      var metadata = openApiMetadata(server.getBaseUrl() + "/api/users", 5000, 0);
      metadata.setRequest(Map.of("url", server.getBaseUrl() + "/api/users", "method", "GET"));
      metadata.setAuthCredential("#{credential}"); metadata.setExtraProp("timeout", "#{timeout}"); metadata.setExtraProp("retry", "#{retry}");
      var context = new RpcInvocationContext(new RpcInvocationRequest(metadata.getName(), Map.of()), new OpenApiRpcModelDefinitionBuilder().build(metadata));
      withRegisteredCredential(aliyunRpcV1Credential("configured-signature"), () -> {
        assertTrue(invoker.invoke(context).isSuccess());
        assertTrue(server.getQuery().contains("AccessKeyId=test-ak"));
        environment.setProperty("timeout", "invalid-private-value");
        var failure = invoker.invoke(context);
        assertTrue(failure.isFailure());
        assertTrue(failure.getError().getCause().getMessage().contains("timeout必须是整数"));
        assertFalse(failure.getError().getCause().getMessage().contains("invalid-private-value"));
      });
    }
  }

  @Test
  void invoke_usesTimeoutOverrideBeforeDefinitionDefault() throws Exception {
    TestHttpServer server = new TestHttpServer(150L, "{\"status\":\"ok\"}");
    server.start();

    try {
      OpenApiRpcProtocolInvoker invoker = new OpenApiRpcProtocolInvoker(newRequestBuilder());
      RPCModelDefinition definition = new OpenApiRpcModelDefinitionBuilder()
          .build(openApiMetadata(server.getBaseUrl() + "/api/users/{userId}", 50, 0));

      Map<String, Object> options = new HashMap<>();
      options.put("timeoutOverrideMs", 1500L);

      Map<String, Object> parameters = new HashMap<>();
      parameters.put("userId", "42");
      parameters.put("keyword", "alice");
      parameters.put("traceId", "trace-1");
      parameters.put("enabled", true);

      RpcInvocationContext context = new RpcInvocationContext(
          new RpcInvocationRequest("openApiTest", parameters, false, null, options),
          definition
      );

      RpcInvocationResult result = invoker.invoke(context);
      Throwable error = result.getError();
      assertTrue(
          result.isSuccess(),
          error == null ? "unknown failure" : error + " cause=" + (error.getCause() == null ? "null" : error.getCause())
      );
      assertTrue(result.getPayload() instanceof Map);
      assertEquals("ok", ((Map<?, ?>) result.getPayload()).get("status"));
      assertEquals("/api/users/42", server.getPath());
      assertEquals("keyword=alice", server.getQuery());
      assertEquals("trace-1", server.getHeader("X-Trace"));
      assertNull(context.getProtocolRequest().getAttribute(SerializedBody.REQUEST_ATTRIBUTE));
      assertTrue(server.getBody().contains("\"enabled\":true"));
    } finally {
      server.close();
    }
  }

  @Test
  void invoke_mapsTransportFailuresToRpcException() throws Exception {
    int port = unusedPort();
    OpenApiRpcProtocolInvoker invoker = new OpenApiRpcProtocolInvoker(newRequestBuilder());
    RPCModelDefinition definition = new OpenApiRpcModelDefinitionBuilder()
        .build(openApiMetadata("http://127.0.0.1:" + port + "/api/users/{userId}", 100, 0));

    Map<String, Object> parameters = new HashMap<>();
    parameters.put("userId", "42");
    parameters.put("keyword", "alice");
    parameters.put("traceId", "trace-1");
    parameters.put("enabled", true);

    RpcInvocationResult result = invoker.invoke(new RpcInvocationContext(
        new RpcInvocationRequest("openApiFailure", parameters),
        definition
    ));

    assertTrue(result.isFailure());
    assertTrue(result.getError() instanceof RPCException);
    assertEquals("调用失败", result.getError().getMessage());
  }

  @Test
  void invoke_replaysReadOnlyRequestAfterTransientHttpFailure() throws Exception {
    try (var server = new TestHttpServer(0L, "{\"status\":\"ok\"}", 500, 200)) {
      server.start();
      var metadata = openApiMetadata(server.getBaseUrl() + "/api/users", 1000, 1);
      metadata.setRequest(Map.of("url", server.getBaseUrl() + "/api/users", "method", "GET"));
      metadata.setExtraProp("retrySafe", true);
      var definition = new OpenApiRpcModelDefinitionBuilder().build(metadata);

      RpcInvocationResult result = new OpenApiRpcProtocolInvoker(newRequestBuilder()).invoke(
          new RpcInvocationContext(new RpcInvocationRequest("openApiReadOnlyRetry", Map.of()), definition));

      assertTrue(result.isSuccess(), () -> String.valueOf(result.getError()));
      assertEquals(2, server.getRequestCount());
    }
  }

  @Test
  void invoke_doesNotReplayWriteWhenRemoteResultIsUnknown() throws Exception {
    try (var server = new TestHttpServer(0L, "{\"status\":\"ok\"}", 500, 200)) {
      server.start();
      var metadata = openApiMetadata(server.getBaseUrl() + "/api/users", 1000, 2);
      metadata.setRequest(Map.of("url", server.getBaseUrl() + "/api/users", "method", "POST"));
      var definition = new OpenApiRpcModelDefinitionBuilder().build(metadata);

      RpcInvocationResult result = new OpenApiRpcProtocolInvoker(newRequestBuilder()).invoke(
          new RpcInvocationContext(new RpcInvocationRequest("openApiWriteNoRetry", Map.of()), definition));

      assertTrue(result.isFailure());
      assertEquals(1, server.getRequestCount());
    }
  }

  @Test
  void invoke_methodAndCallerOptionsCannotGrantReplayPermission() throws Exception {
    try (var server = new TestHttpServer(0L, "{\"status\":\"ok\"}", 500, 200)) {
      server.start();
      var metadata = openApiMetadata(server.getBaseUrl() + "/api/users", 1000, 2);
      metadata.setRequest(Map.of("url", server.getBaseUrl() + "/api/users", "method", "GET"));
      metadata.setExtraProp("retrySafe", "#{retrySafe}");
      var definition = new OpenApiRpcModelDefinitionBuilder().build(metadata);
      var options = Map.<String, Object>of("retryOverride", 3, "retrySafe", true);
      var invocation = new RpcInvocationRequest("openApiNoReplayContract", Map.of("retrySafe", true), false, null, options);

      var result = new OpenApiRpcProtocolInvoker(newRequestBuilder()).invoke(new RpcInvocationContext(invocation, definition));

      assertTrue(result.isFailure());
      assertEquals(1, server.getRequestCount());
    }
  }

  @Test
  void invoke_replaysPostOnlyWithExplicitIdempotentContract() throws Exception {
    try (var server = new TestHttpServer(0L, "{\"status\":\"ok\"}", 500, 200)) {
      server.start();
      var metadata = openApiMetadata(server.getBaseUrl() + "/api/users", 1000, 1);
      metadata.setRequest(Map.of("url", server.getBaseUrl() + "/api/users", "method", "POST"));
      metadata.setExtraProp("retrySafe", true);
      var definition = new OpenApiRpcModelDefinitionBuilder().build(metadata);

      var result = new OpenApiRpcProtocolInvoker(newRequestBuilder()).invoke(
          new RpcInvocationContext(new RpcInvocationRequest("openApiIdempotentRetry", Map.of()), definition));

      assertTrue(result.isSuccess(), () -> String.valueOf(result.getError()));
      assertEquals(2, server.getRequestCount());
    }
  }

  @Test
  void invoke_doesNotReplayTimedOutWriteEvenWithRetryOverride() {
    var attempts = new AtomicInteger();
    var client = org.springframework.web.reactive.function.client.WebClient.builder().exchangeFunction(request -> {
      attempts.incrementAndGet();
      return reactor.core.publisher.Mono.never();
    });
    var metadata = openApiMetadata("http://example.com/api/users", 30, 3);
    metadata.setRequest(Map.of("url", "http://example.com/api/users", "method", "POST"));
    var definition = new OpenApiRpcModelDefinitionBuilder().build(metadata);
    var invocation = new RpcInvocationRequest("openApiTimedOutWrite", Map.of(), false, null, Map.of("retryOverride", 3));

    var result = new OpenApiRpcProtocolInvoker(newRequestBuilder(), client).invoke(new RpcInvocationContext(invocation, definition));

    assertTrue(result.isFailure());
    assertEquals("TIMEOUT", ((com.ouroboros.rpc.execution.RpcExecutionException) result.getError()).getStage());
    assertEquals(1, attempts.get());
  }

  @Test
  void invoke_failsWhenReferencedAuthCredentialIsMissing() throws Exception {
    TestHttpServer server = new TestHttpServer(0L, "{\"status\":\"ok\"}");
    server.start();

    try {
      OpenApiRpcProtocolInvoker invoker = new OpenApiRpcProtocolInvoker(newRequestBuilder());
      RPCModelMetadata metadata = openApiMetadata(server.getBaseUrl() + "/api/users/{userId}", 100, 0);
      metadata.setAuthCredential("missing-credential-for-openapi-test");
      RPCModelDefinition definition = new OpenApiRpcModelDefinitionBuilder().build(metadata);

      Map<String, Object> parameters = new HashMap<>();
      parameters.put("userId", "42");
      parameters.put("keyword", "alice");
      parameters.put("traceId", "trace-1");
      parameters.put("enabled", true);

      RpcInvocationResult result = invoker.invoke(new RpcInvocationContext(
          new RpcInvocationRequest("openApiMissingCredential", parameters),
          definition
      ));

      assertTrue(result.isFailure());
      assertTrue(result.getError() instanceof RPCException);
      assertEquals("认证失败", result.getError().getMessage());
      assertNotNull(result.getError().getCause());
      assertTrue(result.getError().getCause().getMessage().contains("missing-credential-for-openapi-test"));
      assertNull(server.getPath());
    } finally {
      server.close();
    }
  }

  @Test
  void invoke_usesSerializedBodyAttributeWhenPresent() throws Exception {
    TestHttpServer server = new TestHttpServer(0L, "{\"status\":\"ok\"}");
    server.start();

    try {
      HttpRPCRequest request = new HttpRPCRequest();
      request.setMethod("POST");
      request.setUrl(server.getBaseUrl() + "/api/users/42");
      request.header("Content-Type", "application/json");
      request.setAttribute(SerializedBody.REQUEST_ATTRIBUTE, SerializedBody.of("{\"a\":true,\"z\":1}"));

      OpenApiRpcProtocolInvoker invoker = new OpenApiRpcProtocolInvoker(mockRequestBuilder(request));
      RpcInvocationResult result = invoker.invoke(openApiContextFor());

      assertTrue(result.isSuccess());
      assertEquals("{\"a\":true,\"z\":1}", server.getBody());
      assertNotEquals("{\"bodyText\":\"{\\\"a\\\":true,\\\"z\\\":1}\"}", server.getBody());
    } finally {
      server.close();
    }
  }

  @Test
  void invoke_usesSerializedJsonContentTypeWhenRequestDidNotDeclareIt() throws Exception {
    TestHttpServer server = new TestHttpServer(0L, "{\"status\":\"ok\"}");
    server.start();

    try {
      HttpRPCRequest request = new HttpRPCRequest();
      request.setMethod("POST");
      request.setUrl(server.getBaseUrl() + "/api/users/42");
      request.body("z", 1);
      request.body("a", true);
      request.setAttribute(SerializedBody.REQUEST_ATTRIBUTE, SerializedBody.of("{\"a\":true,\"z\":1}", "application/json"));

      OpenApiRpcProtocolInvoker invoker = new OpenApiRpcProtocolInvoker(mockRequestBuilder(request));
      RpcInvocationResult result = invoker.invoke(openApiContextFor(SUCCESS_PATH_TIMEOUT_MS));

      Throwable error = result.getError();
      assertTrue(
          result.isSuccess(),
          error == null ? "unknown failure" : error + " cause=" + (error.getCause() == null ? "null" : error.getCause())
      );
      assertEquals("{\"a\":true,\"z\":1}", server.getBody());
      assertTrue(server.getHeader("Content-Type").contains("application/json"));
    } finally {
      server.close();
    }
  }

  @Test
  void invoke_requestQueryOverridesSameNameInUrlWithoutDuplicateSending() throws Exception {
    TestHttpServer server = new TestHttpServer(0L, "{\"status\":\"ok\"}");
    server.start();

    try {
      HttpRPCRequest request = new HttpRPCRequest();
      request.setMethod("POST");
      request.setUrl(server.getBaseUrl() + "/api/users/42?keep=1&keyword=url-value");
      request.query("keyword", "request-value");
      request.query("page", "2");
      request.body("enabled", true);

      OpenApiRpcProtocolInvoker invoker = new OpenApiRpcProtocolInvoker(mockRequestBuilder(request));
      RpcInvocationResult result = invoker.invoke(openApiContextFor(SUCCESS_PATH_TIMEOUT_MS));

      Throwable error = result.getError();
      assertTrue(
          result.isSuccess(),
          error == null ? "unknown failure" : error + " cause=" + (error.getCause() == null ? "null" : error.getCause())
      );
      assertEquals("/api/users/42", server.getPath());
      assertTrue(server.getQuery().contains("keep=1"));
      assertTrue(server.getQuery().contains("keyword=request-value"));
      assertTrue(server.getQuery().contains("page=2"));
      assertFalse(server.getQuery().contains("keyword=url-value"));
      assertEquals(1, countMatches(server.getQuery(), "keyword="));
    } finally {
      server.close();
    }
  }

  @Test
  void invoke_preservesEncodedReservedCharactersInMergedUrlQuery() throws Exception {
    TestHttpServer server = new TestHttpServer(0L, "{\"status\":\"ok\"}");
    server.start();

    try {
      HttpRPCRequest request = new HttpRPCRequest();
      request.setMethod("GET");
      request.setUrl(server.getBaseUrl() + "/api/users/42?z=a%26b&x=%2F");
      request.query("keyword", "request-value");

      OpenApiRpcProtocolInvoker invoker = new OpenApiRpcProtocolInvoker(mockRequestBuilder(request));
      RpcInvocationResult result = invoker.invoke(openApiContextFor(SUCCESS_PATH_TIMEOUT_MS));

      Throwable error = result.getError();
      assertTrue(
          result.isSuccess(),
          error == null ? "unknown failure" : error + " cause=" + (error.getCause() == null ? "null" : error.getCause())
      );
      assertNotNull(server.getRawQuery());
      assertTrue(server.getRawQuery().contains("z=a%26b"));
      assertTrue(server.getRawQuery().contains("x=%2F"));
      assertFalse(server.getRawQuery().contains("%2526"));
      assertFalse(server.getRawQuery().contains("%252F"));
    } finally {
      server.close();
    }
  }

  @Test
  void invoke_appliesAliyunRpcV1QuerySignatureBeforeSending() throws Exception {
    TestHttpServer server = new TestHttpServer(0L, "{\"status\":\"ok\"}");
    server.start();

    try {
      RPCAuthCredential<HttpRPCRequest> credential = aliyunRpcV1Credential("aliyun-rpc-v1-openapi-test");
      withRegisteredCredential(credential, () -> {
        OpenApiRpcProtocolInvoker invoker = new OpenApiRpcProtocolInvoker(newRequestBuilder());
        RPCModelMetadata metadata = queryOnlyOpenApiMetadata(server.getBaseUrl() + "/api/users/{userId}");
        metadata.setAuthCredential(credential.getName());
        RPCModelDefinition definition = new OpenApiRpcModelDefinitionBuilder().build(metadata);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", "42");
        parameters.put("keyword", "alice");

        RpcInvocationResult result = invoker.invoke(new RpcInvocationContext(
            new RpcInvocationRequest("openApiAliyunRpcV1", parameters),
            definition
        ));

        Throwable error = result.getError();
        assertTrue(
            result.isSuccess(),
            error == null ? "unknown failure" : error + " cause=" + (error.getCause() == null ? "null" : error.getCause())
        );
        assertEquals("/api/users/42", server.getPath());
        assertNotNull(server.getQuery());
        assertTrue(server.getQuery().contains("keyword=alice"));
        assertTrue(server.getQuery().contains("AccessKeyId=test-ak"));
        assertTrue(server.getQuery().contains("SignatureMethod=HMAC-SHA1"));
        assertTrue(server.getQuery().contains("SignatureVersion=1.0"));
        assertTrue(server.getQuery().contains("Action=DescribeInstances"));
        assertTrue(server.getQuery().contains("Version=2014-05-26"));
        assertTrue(server.getQuery().contains("SignatureNonce="));
        assertTrue(server.getQuery().contains("Signature="));
      });
    } finally {
      server.close();
    }
  }

  private static HttpRpcRequestBuilder newRequestBuilder() {
    ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.get(eq(String.class), anyString())).thenReturn(java.util.Optional.empty());
    return new HttpRpcRequestBuilder(configurationService, new MockEnvironment());
  }

  private static RPCModelMetadata openApiMetadata(String url, int timeout, int retry) {
    RPCModelMetadata metadata = new RPCModelMetadata();
    metadata.setName("openApiTest");
    metadata.setTitle("OpenApi Test");
    metadata.setType("OpenApi");
    metadata.setEnabled(true);
    metadata.setRequest(requestDefine(url));
    metadata.setExtraProp("timeout", timeout);
    metadata.setExtraProp("retry", retry);
    return metadata;
  }

  private static HttpRpcRequestBuilder mockRequestBuilder(HttpRPCRequest request) {
    HttpRpcRequestBuilder requestBuilder = mock(HttpRpcRequestBuilder.class);
    when(requestBuilder.buildConfiguredRequest(anyMap(), anyMap())).thenReturn(io.vavr.control.Try.success(request));
    return requestBuilder;
  }

  private static RpcInvocationContext openApiContextFor() {
    return openApiContextFor(SUCCESS_PATH_TIMEOUT_MS);
  }

  private static RpcInvocationContext openApiContextFor(int timeout) {
    return new RpcInvocationContext(
        new RpcInvocationRequest("openApiTransportTest", Collections.emptyMap()),
        new OpenApiRpcModelDefinitionBuilder().build(openApiMetadata("http://example.com/api/users/{userId}", timeout, 0))
    );
  }

  private static Map<String, Object> requestDefine(String url) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("method", "post");
    request.put("url", url);

    List<Map<String, Object>> parameters = new ArrayList<>();
    parameters.add(parameter("userId", "path", "${userId}", true));
    parameters.add(parameter("keyword", "query", "${keyword}", true));
    parameters.add(parameter("X-Trace", "header", "${traceId}", true));
    parameters.add(parameter("enabled", "body", "${enabled}", true));
    request.put("parameters", parameters);
    return request;
  }

  private static RPCModelMetadata queryOnlyOpenApiMetadata(String url) {
    RPCModelMetadata metadata = new RPCModelMetadata();
    metadata.setName("openApiAliyunRpcV1");
    metadata.setTitle("OpenApi Aliyun RpcV1");
    metadata.setType("OpenApi");
    metadata.setEnabled(true);
    metadata.setRequest(queryOnlyRequestDefine(url));
    metadata.setExtraProp("timeout", 1000);
    metadata.setExtraProp("retry", 0);
    return metadata;
  }

  private static Map<String, Object> queryOnlyRequestDefine(String url) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("method", "get");
    request.put("url", url);

    List<Map<String, Object>> parameters = new ArrayList<>();
    parameters.add(parameter("userId", "path", "${userId}", true));
    parameters.add(parameter("keyword", "query", "${keyword}", true));
    request.put("parameters", parameters);
    return request;
  }

  private static Map<String, Object> parameter(String name, String in, String value, boolean required) {
    Map<String, Object> parameter = new LinkedHashMap<>();
    parameter.put("name", name);
    parameter.put("in", in);
    parameter.put("value", value);
    parameter.put("required", required);
    return parameter;
  }

  private static int unusedPort() throws IOException {
    ServerSocket serverSocket = new ServerSocket(0);
    try {
      return serverSocket.getLocalPort();
    } finally {
      serverSocket.close();
    }
  }

  private static RPCAuthCredential<HttpRPCRequest> aliyunRpcV1Credential(String credentialName) {
    RPCCredentialMetadata metadata = new RPCCredentialMetadata();
    metadata.setName(credentialName);
    metadata.setTitle(credentialName);
    metadata.setType("HmacSignature");
    metadata.setEnabled(true);

    Map<String, Object> aliyunRpcV1 = new LinkedHashMap<>();
    aliyunRpcV1.put("signatureMethod", "HMAC-SHA1");

    Map<String, Object> hmacSignature = new LinkedHashMap<>();
    hmacSignature.put("preset", "aliyun_rpc_v1");
    hmacSignature.put("accessKey", "test-ak");
    hmacSignature.put("secretKey", "test-sk");
    hmacSignature.put("action", "DescribeInstances");
    hmacSignature.put("version", "2014-05-26");
    hmacSignature.put("aliyunRpcV1", aliyunRpcV1);

    Map<String, Object> extraProps = new LinkedHashMap<>();
    extraProps.put("hmacSignature", hmacSignature);
    metadata.setExtraProps(extraProps);
    return new HmacSignatureRpcAuthCredential.Builder().build(metadata);
  }

  private static void withRegisteredCredential(
      RPCAuthCredential<HttpRPCRequest> credential,
      ThrowingRunnable runnable
  ) throws Exception {
    Field field = RPCAuthCredentialCenter.class.getDeclaredField("authenticatorMap");
    field.setAccessible(true);
    Object previous = field.get(null);

    Map<String, RPCAuthCredential> current = new java.util.TreeMap<>(String::compareToIgnoreCase);
    current.put(credential.getName(), credential);
    field.set(null, current);
    try {
      runnable.run();
    } finally {
      field.set(null, previous);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static int countMatches(String text, String token) {
    if (text == null || token == null || token.isEmpty()) {
      return 0;
    }
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(token, index)) >= 0) {
      count++;
      index += token.length();
    }
    return count;
  }

  private static class TestHttpServer implements AutoCloseable {
    private final long delayMs;
    private final String responseBody;
    private final int[] responseStatuses;
    private final AtomicInteger requestCount = new AtomicInteger();
    private HttpServer server;
    private volatile String path;
    private volatile String query;
    private volatile String rawQuery;
    private volatile String body;
    private final Map<String, String> headers = new HashMap<>();

    private TestHttpServer(long delayMs, String responseBody) {
      this(delayMs, responseBody, 200);
    }

    private TestHttpServer(long delayMs, String responseBody, int... responseStatuses) {
      this.delayMs = delayMs;
      this.responseBody = responseBody;
      this.responseStatuses = responseStatuses;
    }

    private void start() throws IOException {
      server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext("/api/users", exchange -> {
        int requestIndex = requestCount.getAndIncrement();
        path = exchange.getRequestURI().getPath();
        query = exchange.getRequestURI().getQuery();
        rawQuery = exchange.getRequestURI().getRawQuery();
        body = StreamUtils.copyToString(exchange.getRequestBody(), StandardCharsets.UTF_8);
        for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
          headers.put(entry.getKey(), entry.getValue().isEmpty() ? null : entry.getValue().get(0));
        }
        if (delayMs > 0) {
          try {
            Thread.sleep(delayMs);
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
        }

        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        int responseStatus = responseStatuses[Math.min(requestIndex, responseStatuses.length - 1)];
        exchange.sendResponseHeaders(responseStatus, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
          outputStream.write(bytes);
        }
      });
      server.start();
    }

    private String getBaseUrl() {
      return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String getPath() {
      return path;
    }

    private String getQuery() {
      return query;
    }

    private String getRawQuery() {
      return rawQuery;
    }

    private String getBody() {
      return body;
    }

    private int getRequestCount() {
      return requestCount.get();
    }

    private String getHeader(String name) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
          return entry.getValue();
        }
      }
      return null;
    }

    @Override
    public void close() {
      if (server != null) {
        server.stop(0);
      }
    }
  }
}
