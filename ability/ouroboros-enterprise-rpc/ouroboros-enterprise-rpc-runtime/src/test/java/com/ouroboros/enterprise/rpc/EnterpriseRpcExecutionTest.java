package com.ouroboros.enterprise.rpc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.enterprise.*;
import com.ouroboros.enterprise.runtime.*;
import com.ouroboros.enterprise.token.*;
import com.ouroboros.rpc.*;
import com.ouroboros.rpc.execution.*;
import com.ouroboros.rpc.execution.protocol.OpenApiRpcProtocolInvoker;
import com.ouroboros.rpc.security.*;
import com.ouroboros.rpc.util.HttpRpcRequestBuilder;
import com.ouroboros.security.authentication.OuroborosAuthentication.EffectiveAuthority;
import com.ouroboros.security.authority.*;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.*;

class EnterpriseRpcExecutionTest {
  private final EnterpriseConnectionCatalog catalog = mock(EnterpriseConnectionCatalog.class);
  private final EnterpriseApplicationTokens tokens = mock(EnterpriseApplicationTokens.class);
  private final ConfigurationService configuration = mock(ConfigurationService.class);
  private final AtomicInteger requests = new AtomicInteger();
  private final com.ouroboros.enterprise.EnterpriseApiBudget apiBudget = mock(com.ouroboros.enterprise.EnterpriseApiBudget.class);
  private RpcInvocationExecutor executor;
  private RpcExecutionResourcesFactory factory;
  private org.mockito.MockedStatic<AuthorityCenter> authorities;

  @BeforeEach void setup() throws Exception {
    authorities = mockStatic(AuthorityCenter.class);
    authorities.when(() -> AuthorityCenter.getAuthority(anyString())).thenAnswer(call -> {
      var definition = new BaseAuthority(); definition.setFullName(call.getArgument(0)); return Optional.of(definition);
    });
    authenticate(EnterpriseRpcAutoConfiguration.USE);
    var provider = mock(EnterpriseConnectionProvider.class);
    when(provider.provider()).thenReturn("feishu"); when(provider.connectionIdentityFields()).thenReturn(Set.of("corpId"));
    var providers = EnterpriseConnectionProviderRegistry.of(List.of(provider));
    when(catalog.listSummaries()).thenReturn(List.of(new EnterpriseConnectionCatalog.ConnectionSummary("feishu-main", "feishu")));
    when(catalog.resolve("feishu-main", "feishu")).thenReturn(Optional.of(new ResolvedEnterpriseConnection("feishu-main",
        Map.of("provider", "feishu", "corpId", "corp", "revision", "1"), () -> Map.of("appId", "app"))));
    when(tokens.provider()).thenReturn("feishu");
    when(tokens.targets(any())).thenReturn(List.of(new EnterpriseTokenTarget("tenant_access_token", "open.feishu.cn", "/open-apis/", "HEADER", "Authorization", "Bearer ")));
    when(tokens.accessToken(any(), any())).thenReturn("actual-enterprise-token");
    var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
    var executorProvider = beans.getBeanProvider(RpcInvocationExecutor.class);
    factory = new EnterpriseRpcAutoConfiguration().enterpriseRpcResourcesFactory(catalog, providers, List.of(tokens),
        configuration, new MockEnvironment(), executorProvider, apiBudget);
    var transport = WebClient.builder().exchangeFunction(outgoing -> {
      requests.incrementAndGet();
      assertThat(outgoing.headers().getFirst("Authorization")).isEqualTo("Bearer actual-enterprise-token");
      return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
          .body("{\"echo\":\"actual-enterprise-token\",\"ok\":true}").build());
    });
    executor = new RpcInvocationExecutor(List.of(new OpenApiRpcProtocolInvoker(new HttpRpcRequestBuilder(configuration, new MockEnvironment()), transport)), List.of(), List.of(factory));
    beans.addBean("executor", executor);
    RPCModelDefinitionCenter.refresh(); RPCAuthCredentialCenter.refresh();
  }

  @AfterEach void cleanup() { authorities.close(); CurrentSecurityContextHolder.clearDelegate(); }

  @Test void installedRpcModuleContributesNavigationWithoutGrantingInvocationOrRuntimeManagement() {
    var pages = new EnterpriseRpcAutoConfiguration().enterpriseRpcIntegrationContributor().pages();
    assertThat(pages).singleElement().satisfies(page -> {
      assertThat(page.section()).isEqualTo(com.ouroboros.integration.management.IntegrationManagementPage.Section.CONNECTIONS);
      assertThat(page.schemaPath()).isEqualTo("/system-setting/integration-management/enterprise-rpc");
      assertThat(page.authorities()).containsExactly(EnterpriseRpcAutoConfiguration.CATALOG);
    });
  }

  @Test void runtimeUsesManagedResourcesThroughOriginalExecutor() {
    assertThat(requests).hasValue(0);
    var result = executor.invoke(new RpcInvocationRequest("enterpriseRpc-test", Map.of("connectionId", "evil")));
    assertThat(result.isSuccess()).describedAs(String.valueOf(result.getError())).isTrue();
    assertThat(result.getPayload()).isEqualTo(Map.of("echo", "***", "ok", true));
    assertThat(requests).hasValue(1);
  }

  @Test void developmentTestRetainsModeLimitsEvidenceAndMandatorySecretDisclosureToRedactor() {
    var parent = mock(RpcExecutionResources.class);
    when(parent.findDefinition("enterpriseRpc-test")).thenReturn(RPCModelDefinitionCenter.getDefinition("enterpriseRpc-test"));
    doReturn(Optional.of(RPCAuthCredentialCenter.getRpcCredential("enterpriseAuth-test").orElseThrow())).when(parent).findCredential("enterpriseAuth-test");
    when(parent.mode()).thenReturn(RpcExecutionResources.Mode.DEVELOPMENT_TEST);
    when(parent.remainingDurationMs()).thenReturn(5000L); when(parent.maxRequestBytes()).thenReturn(1024);
    when(parent.redact(any())).thenAnswer(call -> call.getArgument(0));
    var resources = factory.create(new RpcInvocationRequest("enterpriseRpc-test", Map.of()), parent);
    assertThat(resources.mode()).isEqualTo(RpcExecutionResources.Mode.DEVELOPMENT_TEST);
    assertThat(factory.create(new RpcInvocationRequest("enterpriseRpc-test", Map.of()), resources)).isSameAs(resources);
    var result = executor.invoke(new RpcInvocationRequest("enterpriseRpc-test", Map.of()), resources);
    assertThat(result.isSuccess()).isTrue();
    verify(parent).onSecret("actual-enterprise-token");
    verify(parent).onExchange(any(RpcHttpExchange.class));
    assertThat(resources.redact("upstream echo actual-enterprise-token")).isEqualTo("upstream echo ***");
    assertThat(resources.invoke(new RpcInvocationRequest("enterpriseRpc-test", Map.of())).isSuccess()).isTrue();
    assertThat(requests).hasValue(2);
  }

  @Test void editPermissionDoesNotGrantRuntimeOrTestTokenUse() throws Exception {
    authenticate("systemSetting.integrationManagement.connections.enterprise.configure");
    assertThat(executor.invoke(new RpcInvocationRequest("enterpriseRpc-test", Map.of(), true, "token-fetch", Map.of())).isFailure()).isTrue();
    assertThat(requests).hasValue(0);
    verify(tokens, never()).accessToken(any(), any());
  }

  @Test void installedBuilderContributesEditableTypeAndRejectsIdentityOverrides() {
    assertThat(RPCAuthCredentialCenter.credentialTypes()).anySatisfy(type -> assertThat(type.name()).isEqualTo("EnterpriseAccessToken"));
    var metadata = new com.ouroboros.rpc.security.metadata.RPCCredentialMetadata();
    metadata.setType("EnterpriseAccessToken");
    metadata.setExtraProp("enterprise", Map.of("provider", "feishu", "tokenKind", "tenant_access_token", "connectionId", "evil"));
    assertThat(RPCAuthCredentialCenter.validate(metadata).isFailure()).isTrue();
  }

  @Test void sharedBudgetRejectionStopsTransportBeforeAnyRemoteRequest() {
    doThrow(new com.ouroboros.enterprise.EnterpriseApiRateLimitException("budget depleted"))
        .when(apiBudget).acquire("feishu");
    var result = executor.invoke(new RpcInvocationRequest("enterpriseRpc-test", Map.of()));
    assertThat(result.isFailure()).isTrue();
    assertThat(requests).hasValue(0);
    verify(apiBudget).acquire("feishu");
  }

  @Test void domainSessionPinsThePreviouslyAuthorizedConnectionAndUsesOriginalRpcModels() throws Exception {
    var selected = catalog.resolve("feishu-main", "feishu").orElseThrow();
    var provider = mock(EnterpriseConnectionProvider.class);
    when(provider.provider()).thenReturn("feishu"); when(provider.connectionIdentityFields()).thenReturn(Set.of("corpId"));
    var client = new EnterpriseRpcClient(executor, catalog, EnterpriseConnectionProviderRegistry.of(List.of(provider)),
        List.of(tokens), key -> null, mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));
    var session = client.session(selected, EnterpriseDeadline.afterMillis(10000));
    // Workers execute their owning domain's previously authorized operation.
    CurrentSecurityContextHolder.clearDelegate();
    assertThat(session.invoke("enterpriseRpc-test", Map.of()).path("ok").asBoolean()).isTrue();
    assertThatThrownBy(() -> session.invoke("missing", Map.of())).isInstanceOf(com.ouroboros.exception.BusinessException.class);
    when(catalog.resolve("feishu-main", "feishu")).thenReturn(Optional.empty());
    when(catalog.listSummaries()).thenReturn(List.of(new EnterpriseConnectionCatalog.ConnectionSummary("another-corp", "feishu")));
    assertThatThrownBy(() -> session.invoke("enterpriseRpc-test", Map.of())).isInstanceOf(com.ouroboros.exception.BusinessException.class);
    assertThat(requests).hasValue(1);
  }

  @Test void domainSessionRefusesAnAlreadyOutdatedConnectionBeforeAnyRequest() {
    var provider = mock(EnterpriseConnectionProvider.class);
    when(provider.provider()).thenReturn("feishu"); when(provider.connectionIdentityFields()).thenReturn(Set.of("corpId"));
    var client = new EnterpriseRpcClient(executor, catalog, EnterpriseConnectionProviderRegistry.of(List.of(provider)),
        List.of(tokens), key -> null, mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));
    var old = new ResolvedEnterpriseConnection("feishu-main", Map.of("provider", "feishu", "corpId", "corp", "revision", "old"), () -> Map.of("appId", "app"));
    assertThatThrownBy(() -> client.session(old, EnterpriseDeadline.afterMillis(10000)))
        .isInstanceOf(com.ouroboros.exception.ValidationException.class);
    assertThat(requests).hasValue(0);
  }

  private void authenticate(String... permissions) {
    var grants = Arrays.stream(permissions).map(name -> {
      var authority = new BaseAuthority(); authority.setFullName(name);
      return new EffectiveAuthority(authority, ResolvedAccessControlParams.all());
    }).toList();
    var role = mock(Role.class); when(role.getAuthorities()).thenAnswer(call -> new ArrayList<>(grants));
    var user = mock(User.class); when(user.getUserIdentity()).thenReturn("admin"); when(user.getRoles()).thenReturn(List.of(role));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
  }
}
