package com.ouroboros.enterprise.rpc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.ouroboros.enterprise.*;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.token.*;
import com.ouroboros.rpc.execution.*;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.RPCAuthCredentialCenter;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.util.Json;

class EnterpriseAccessTokenCredentialTest {
  private final EnterpriseConnectionCatalog catalog = mock(EnterpriseConnectionCatalog.class);
  private final EnterpriseApplicationTokens tokens = mock(EnterpriseApplicationTokens.class);
  private final AtomicInteger authorizations = new AtomicInteger();
  private ResolvedEnterpriseConnection connection;
  private EnterpriseRpcResources resources;
  private EnterpriseAccessTokenCredential credential;

  @BeforeEach void setup() throws Exception {
    connection = connection("corp-a", "app-a", "1");
    when(catalog.listSummaries()).thenReturn(List.of(new EnterpriseConnectionCatalog.ConnectionSummary("corp-a", "feishu")));
    when(catalog.resolve("corp-a", "feishu")).thenAnswer(ignored -> Optional.of(connection));
    when(tokens.targets(any())).thenReturn(List.of(new EnterpriseTokenTarget("tenant_access_token", "open.feishu.cn",
        "/open-apis/", "HEADER", "Authorization", "Bearer ")));
    when(tokens.accessToken(any(), any())).thenReturn("shared-sensitive+token");
    resources = resources(provider -> authorizations.incrementAndGet());
    credential = new EnterpriseAccessTokenCredential(metadata("feishu", "tenant_access_token", Map.of("tokenPlacement", "Authorization")));
  }

  @Test void discoversDefinitionWithoutRemoteAccessAndRejectsUntrustedParameters() throws Exception {
    assertThat(RPCAuthCredentialCenter.build(metadata("feishu", "tenant_access_token", Map.of()), null).isSuccess()).isTrue();
    var result = credential.applyToRequest(request("https://open.feishu.cn/open-apis/im/v1/messages"),
        Map.of("connectionId", "corp-a", "resources", resources));
    assertThat(result.isFailure()).isTrue();
    verify(tokens, never()).accessToken(any(), any());
  }

  @Test void usesUniqueAuthorizedConnectionAndForcesTokenRedaction() throws Exception {
    var request = request("https://open.feishu.cn/open-apis/im/v1/messages");
    assertThat(credential.applyToRequest(request, Map.of("connectionId", "attacker", "accessToken", "attacker"), resources).isSuccess()).isTrue();
    assertThat(request.getHeaders()).containsEntry("Authorization", "Bearer shared-sensitive+token");
    assertThat(authorizations.get()).isGreaterThanOrEqualTo(1);
    assertThat(resources.redact(Map.of("url", "?q=shared-sensitive%2Btoken", "echo", "shared-sensitive+token")))
        .isEqualTo(Map.of("url", "?q=***", "echo", "***"));
    verify(tokens).accessToken(eq(connection), any());
  }

  @ParameterizedTest @ValueSource(strings = {
      "https://attacker.example/open-apis/im/v1/messages", "http://open.feishu.cn/open-apis/im/v1/messages",
      "https://open.feishu.cn.attacker.example/open-apis/im/v1/messages", "https://open.feishu.cn:8443/open-apis/im/v1/messages",
      "https://user@open.feishu.cn/open-apis/im/v1/messages", "https://open.feishu.cn/open-apis/im/v1/messages#fragment",
      "https://open.feishu.cn/outside/", "https://open.feishu.cn/open-apis/../outside",
      "https://open.feishu.cn/open-apis/%2e%2e/outside"
  }) void rejectsWrongDestinationBeforeAcquiringToken(String url) throws Exception {
    assertThat(credential.applyToRequest(request(url), Map.of(), resources).isFailure()).isTrue();
    verify(tokens, never()).accessToken(any(), any());
  }

  @Test void rejectsMissingOrAmbiguousChannelConnection() throws Exception {
    when(catalog.listSummaries()).thenReturn(List.of());
    assertThat(credential.applyToRequest(request(validUrl()), Map.of(), resources).isFailure()).isTrue();
    when(catalog.listSummaries()).thenReturn(List.of(new EnterpriseConnectionCatalog.ConnectionSummary("corp-a", "feishu"),
        new EnterpriseConnectionCatalog.ConnectionSummary("corp-b", "feishu")));
    assertThat(credential.applyToRequest(request(validUrl()), Map.of(), resources).isFailure()).isTrue();
    verify(tokens, never()).accessToken(any(), any());
  }

  @Test void editingOrInternalCallDoesNotGrantCredentialUse() throws Exception {
    var denied = resources(provider -> { throw new com.ouroboros.exception.ValidationException("denied"); });
    assertThat(credential.applyToRequest(request(validUrl()), Map.of("internalCall", true), denied).isFailure()).isTrue();
    verify(tokens, never()).accessToken(any(), any());
  }

  @Test void rejectsWrongTokenKindOrCustomPlacementBeforeRemoteCall() throws Exception {
    var wrongKind = new EnterpriseAccessTokenCredential(metadata("feishu", "user_access_token", Map.of()));
    assertThat(wrongKind.applyToRequest(request(validUrl()), Map.of(), resources).isFailure()).isTrue();
    var extraPlacement = new EnterpriseAccessTokenCredential(metadata("feishu", "tenant_access_token",
        Map.of("tokenPlacement", "custom", "customPlacement", List.of(
            Map.of("in", "HEADER", "name", "Authorization", "value", "Bearer ${accessToken}"),
            Map.of("in", "BODY", "name", "secret", "value", "${accessToken}")))));
    assertThat(extraPlacement.applyToRequest(request(validUrl()), Map.of(), resources).isFailure()).isTrue();
    verify(tokens, never()).accessToken(any(), any());
  }

  @Test void stopsWhenRotatedOrDisabledDuringTokenAcquisition() throws Exception {
    when(tokens.accessToken(any(), any())).thenAnswer(ignored -> { connection = connection("corp-a", "app-a", "2"); return "old-token"; });
    var request = request(validUrl());
    assertThat(credential.applyToRequest(request, Map.of(), resources).isFailure()).isTrue();
    assertThat(request.getHeaders()).doesNotContainKey("Authorization");
    when(catalog.resolve("corp-a", "feishu")).thenThrow(new com.ouroboros.exception.ValidationException("disabled"));
    assertThat(credential.applyToRequest(request(validUrl()), Map.of(), resources).isFailure()).isTrue();
  }

  @Test void permitsNewCredentialGenerationForSameApplicationButNeverSwitchesTarget() {
    assertThat(credential.applyToRequest(request(validUrl()), Map.of(), resources).isSuccess()).isTrue();
    connection = connection("corp-a", "app-a", "2");
    assertThat(credential.applyToRequest(request(validUrl()), Map.of(), resources).isSuccess()).isTrue();
    connection = connection("corp-a", "app-b", "3");
    assertThat(credential.applyToRequest(request(validUrl()), Map.of(), resources).isFailure()).isTrue();
    when(catalog.resolve("corp-a", "feishu")).thenReturn(Optional.empty());
    when(catalog.listSummaries()).thenReturn(List.of(new EnterpriseConnectionCatalog.ConnectionSummary("corp-b", "feishu")));
    assertThat(credential.applyToRequest(request(validUrl()), Map.of(), resources).isFailure()).isTrue();
  }

  @Test void injectsQueryTokenThroughOriginalPlacementEngine() throws Exception {
    when(tokens.targets(any())).thenReturn(List.of(new EnterpriseTokenTarget("access_token", "qyapi.weixin.qq.com",
        "/cgi-bin/", "QUERY", "access_token", "")));
    var queryCredential = new EnterpriseAccessTokenCredential(metadata("feishu", "access_token", Map.of("tokenPlacement", "custom",
        "customPlacement", List.of(Map.of("in", "QUERY", "name", "access_token", "value", "${accessToken}")))));
    var request = request("https://qyapi.weixin.qq.com/cgi-bin/user/list");
    assertThat(queryCredential.applyToRequest(request, Map.of(), resources).isSuccess()).isTrue();
    assertThat(request.getQuery()).containsEntry("access_token", "shared-sensitive+token");
  }

  @Test void refusesMissingProviderAndEmptyOrFailedToken() throws Exception {
    var absent = new EnterpriseAccessTokenCredential(metadata("missing", "tenant_access_token", Map.of()));
    assertThat(absent.applyToRequest(request(validUrl()), Map.of(), resources).isFailure()).isTrue();
    when(tokens.accessToken(any(), any())).thenReturn("");
    assertThat(credential.applyToRequest(request(validUrl()), Map.of(), resources).isFailure()).isTrue();
    var failure = new java.io.IOException("upstream unavailable");
    when(tokens.accessToken(any(), any())).thenThrow(failure);
    assertThat(credential.applyToRequest(request(validUrl()), Map.of(), resources).getCause()).hasCause(failure);
  }

  private EnterpriseRpcResources resources(java.util.function.Consumer<String> authorize) {
    var provider = mock(EnterpriseConnectionProvider.class);
    when(provider.provider()).thenReturn("feishu");
    when(provider.connectionIdentityFields()).thenReturn(java.util.Set.of("corpId"));
    var providers = com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry.of(List.of(provider));
    return new EnterpriseRpcResources(null, catalog, providers, Map.of("feishu", tokens), authorize, key -> null,
        (nested, resource) -> RpcInvocationResult.success("nested", 1), 60000,
        mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));
  }
  private ResolvedEnterpriseConnection connection(String id, String app, String revision) {
    return new ResolvedEnterpriseConnection(id, Map.of("provider", "feishu", "revision", revision, "corpId", "corp"),
        () -> Map.of("appId", app));
  }
  private RPCCredentialMetadata metadata(String provider, String kind, Map<String, Object> placement) {
    return Json.toBean(RPCCredentialMetadata.class, Json.toJsonString(Map.of("name", "enterprise-token", "type", "EnterpriseAccessToken", "isEnabled", true,
        "enterprise", Map.of("provider", provider, "tokenKind", kind), "accessToken", placement)));
  }
  private HttpRPCRequest request(String url) { var request = new HttpRPCRequest(); request.setUrl(url); request.setMethod("GET"); return request; }
  private String validUrl() { return "https://open.feishu.cn/open-apis/contact/v3/users"; }
}
