package com.ouroboros.rpc.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.rpc.execution.*;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

class ConfiguredRpcCredentialTest {
  private final ConfigurationService service = mock(ConfigurationService.class);
  private final MockEnvironment environment = new MockEnvironment();

  ConfiguredRpcCredentialTest() {
    when(service.get(anyString())).thenReturn(Optional.empty());
    when(service.get(eq(String.class), anyString())).thenReturn(Optional.empty());
    initialize();
  }

  private void initialize() {
    new HttpBasicRpcAuthCredential(service, environment);
    new PlainRpcCredential(service, environment);
    new HmacSignatureRpcAuthCredential(service, environment);
    new AccessTokenRpcAuthCredential(service, environment, new StaticListableBeanFactory().getBeanProvider(RpcInvocationExecutor.class));
  }

  @AfterEach void reset() {
    new HttpBasicRpcAuthCredential(service, new MockEnvironment());
    new PlainRpcCredential(service, new MockEnvironment());
    new HmacSignatureRpcAuthCredential(service, new MockEnvironment());
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(null);
  }

  @Test void actualBuildersResolveConfigurationWithoutReevaluatingSecrets() {
    var secret = new java.util.concurrent.atomic.AtomicReference<>("$literal-${value}");
    when(service.get("secret")).thenAnswer(invocation -> Optional.of(secret.get()));
    when(service.get(String.class, "secret")).thenAnswer(invocation -> Optional.of(secret.get()));
    environment.withProperty("username", "alice").withProperty("secret", "$literal-${value}")
        .withProperty("placement", "header").withProperty("header", "X-Key").withProperty("mode", "static")
        .withProperty("tokenPlacement", "custom");
    var basic = new HttpBasicRpcAuthCredential.Builder().build(metadata("HttpBasic", "httpBasic", Map.of("username", "#{username}", "password", "#{secret}")));
    var basicRequest = basic.applyToRequest(new HttpRPCRequest(), Map.of()).get();
    assertEquals("alice:$literal-${value}", new String(Base64.getDecoder().decode(basicRequest.getHeaders().get("Authorization").substring(6)), StandardCharsets.UTF_8));

    var plain = new PlainRpcCredential.Builder().build(metadata("PlainCredentials", "plainCredentials", Map.of("credentials",
        List.of(Map.of("in", "#{placement}", "name", "#{header}", "value", "#{secret}")))));
    assertEquals("$literal-${value}", plain.applyToRequest(new HttpRPCRequest(), Map.of()).get().getHeaders().get("X-Key"));

    var token = new AccessTokenRpcAuthCredential.Builder().build(metadata("AccessToken", "accessToken", Map.of("mode", "#{mode}",
        "tokenPlacement", "#{tokenPlacement}", "staticAccessToken", "#{secret}", "customPlacement",
        List.of(Map.of("in", "#{placement}", "name", "#{header}", "value", "${accessToken}")))));
    assertInstanceOf(StaticAccessTokenRPCAuthCredential.class, token);
    assertEquals("$literal-${value}", token.applyToRequest(new HttpRPCRequest(), Map.of()).get().getHeaders().get("X-Key"));
    secret.set("updated");
    assertEquals("updated", token.applyToRequest(new HttpRPCRequest(), Map.of()).get().getHeaders().get("X-Key"));

    environment.withProperty("preset", "aws_sigv4").withProperty("algorithm", "SHA-256");
    var hmac = new HmacSignatureRpcAuthCredential.Builder().build(metadata("HmacSignature", "hmacSignature", Map.of(
        "preset", "#{preset}", "hashAlgorithm", "#{algorithm}", "accessKey", "#{username}", "secretKey", "#{secret}", "region", "us-east-1", "service", "execute-api")));
    var request = new HttpRPCRequest(); request.setMethod("GET"); request.setUrl("https://example.com/test");
    var signed = hmac.applyToRequest(request, Map.of()).get();
    assertTrue(signed.getHeaders().get("Authorization").startsWith("AWS4-HMAC-SHA256 Credential=alice/"));
  }

  @Test void refreshedCredentialCannotReuseAnOldInflightToken() throws Exception {
    var oldStarted = new CountDownLatch(1); var releaseOld = new CountDownLatch(1);
    var executor = mock(RpcInvocationExecutor.class);
    when(executor.invoke(any(RpcInvocationRequest.class))).thenAnswer(invocation -> {
      RpcInvocationRequest request = invocation.getArgument(0);
      if ("old".equals(request.getModelName())) {
        oldStarted.countDown();
        assertTrue(releaseOld.await(5, TimeUnit.SECONDS));
      }
      return RpcInvocationResult.success(Map.of("token", request.getModelName(), "expires", 60), 1L);
    });
    AccessTokenRpcAuthCredential.setRpcInvocationExecutor(executor);
    environment.setProperty("model", "old");
    var source = metadata("AccessToken", "accessToken", Map.of("mode", "dynamic", "request", Map.of("modelName", "#{model}"),
        "accessTokenPath", "$.token", "expiresConfig", Map.of("expiresPath", "$.expires")));
    var oldCredential = new AccessTokenRpcAuthCredential.Builder().build(source);
    var oldResult = CompletableFuture.supplyAsync(() -> oldCredential.applyToRequest(new HttpRPCRequest(), Map.of()).get());
    try {
      assertTrue(oldStarted.await(5, TimeUnit.SECONDS));
      environment.setProperty("model", "new");
      var refreshed = new AccessTokenRpcAuthCredential.Builder().build(source);
      assertEquals("Bearer new", refreshed.applyToRequest(new HttpRPCRequest(), Map.of()).get().getHeaders().get("Authorization"));
      releaseOld.countDown();
      assertEquals("Bearer old", oldResult.get(5, TimeUnit.SECONDS).getHeaders().get("Authorization"));
      assertEquals("Bearer new", refreshed.applyToRequest(new HttpRPCRequest(), Map.of()).get().getHeaders().get("Authorization"));
      verify(executor, times(2)).invoke(any(RpcInvocationRequest.class));
    } finally {
      releaseOld.countDown();
      oldResult.get(5, TimeUnit.SECONDS);
    }
  }

  @Test void explicitResourcesResolveAllCredentialKindsWithoutGlobalConfiguration() {
    environment.withProperty("placement", "invalid-global-placement").withProperty("mode", "invalid-global-mode");
    var resources = mock(RpcExecutionResources.class);
    var values = Map.of("secret", "$literal-${value}", "placement", "header", "header", "X-Workspace-Key",
        "mode", "static", "tokenPlacement", "custom", "preset", "aws_sigv4", "algorithm", "SHA-256", "username", "workspace-user");
    when(resources.getConfig(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0, String.class)));

    var basic = new HttpBasicRpcAuthCredential.Builder().build(metadata("HttpBasic", "httpBasic",
        Map.of("username", "${username}", "password", "#{secret}")), resources);
    var basicRequest = basic.applyToRequest(new HttpRPCRequest(), Map.of("username", "context-user")).get();
    assertEquals("context-user:$literal-${value}", new String(Base64.getDecoder().decode(
        basicRequest.getHeaders().get("Authorization").substring(6)), StandardCharsets.UTF_8));

    var plain = new PlainRpcCredential.Builder().build(metadata("PlainCredentials", "plainCredentials", Map.of("credentials",
        List.of(Map.of("in", "#{placement}", "name", "#{header}", "value", "#{secret}")))), resources);
    assertEquals("$literal-${value}", plain.applyToRequest(new HttpRPCRequest(), Map.of()).get().getHeaders().get("X-Workspace-Key"));

    var token = new AccessTokenRpcAuthCredential.Builder().build(metadata("AccessToken", "accessToken", Map.of(
        "mode", "#{mode}", "tokenPlacement", "#{tokenPlacement}", "staticAccessToken", "#{secret}", "customPlacement",
        List.of(Map.of("in", "#{placement}", "name", "#{header}", "value", "${accessToken}")))), resources);
    assertInstanceOf(StaticAccessTokenRPCAuthCredential.class, token);
    assertEquals("$literal-${value}", token.applyToRequest(new HttpRPCRequest(), Map.of()).get().getHeaders().get("X-Workspace-Key"));

    var hmac = new HmacSignatureRpcAuthCredential.Builder().build(metadata("HmacSignature", "hmacSignature", Map.of(
        "preset", "#{preset}", "hashAlgorithm", "#{algorithm}", "accessKey", "#{username}", "secretKey", "#{secret}",
        "region", "us-east-1", "service", "execute-api")), resources);
    var request = new HttpRPCRequest(); request.setMethod("GET"); request.setUrl("https://example.com/test");
    assertTrue(hmac.applyToRequest(request, Map.of()).get().getHeaders().get("Authorization")
        .startsWith("AWS4-HMAC-SHA256 Credential=workspace-user/"));
    verifyNoInteractions(service);
  }

  private RPCCredentialMetadata metadata(String type, String key, Map<String, Object> settings) {
    var metadata = new RPCCredentialMetadata(); metadata.setName("configured-auth"); metadata.setType(type);
    metadata.setExtraProp(key, settings); return metadata;
  }
}
