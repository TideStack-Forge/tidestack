package com.ouroboros.enterprise.runtime.auth;

import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileProvider;

import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.externalauth.EnterpriseAuthProfileResolver;
import com.ouroboros.security.externalauth.config.OAuth2ProviderSpec;
import com.ouroboros.security.externalauth.config.ProviderSpec;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

class EnterpriseConnectionAuthProfileResolverTest {

  @Test
  void loginCandidatesReflectTheCurrentConnectionCatalog() {
    var connections = mock(EnterpriseConnectionCatalog.class);
    var resolver = new EnterpriseConnectionAuthProfileResolver(connections, List.of(),
        mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));
    when(connections.listSummaries()).thenReturn(List.of(new EnterpriseConnectionCatalog.ConnectionSummary("main", "feishu")));
    assertThat(resolver.connectionIds()).containsExactly("main");
    when(connections.listSummaries()).thenReturn(List.of());
    assertThat(resolver.connectionIds()).isEmpty();
  }

  @Test
  void protocolRequestsRecheckProfileRevisionBeforeSpendingTheSharedBudget() {
    var connections = mock(EnterpriseConnectionCatalog.class);
    var budget = mock(com.ouroboros.enterprise.EnterpriseApiBudget.class);
    var resolver = new EnterpriseConnectionAuthProfileResolver(connections, List.of(), budget);
    var request = new EnterpriseConnectionAuthProfileRequest("main", "feishu", "revision", "managed:main", Map.of());
    when(connections.findAuthProfileRequest("main")).thenReturn(Optional.of(request));
    resolver.beforeRequest("main", "revision");
    org.mockito.Mockito.verify(budget).acquire("feishu");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.beforeRequest("main", "outdated"))
        .hasMessageContaining("发生变更");
    when(connections.findAuthProfileRequest("main")).thenReturn(Optional.empty());
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.beforeRequest("main", "revision"))
        .hasMessageContaining("停用");
    org.mockito.Mockito.verifyNoMoreInteractions(budget);
  }

  @Test
  void rejectsDuplicateProviderFacetRegistrationsAtConstruction() {
    // 同一 provider 注册两个 auth facet 会让"用哪个 profile"变成隐式选择，必须构造期失败。
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new EnterpriseConnectionAuthProfileResolver(mock(EnterpriseConnectionCatalog.class),
    List.of(new TestProfileProvider("feishu", profile("feishu-main")),
        new TestProfileProvider("feishu", profile("feishu-main"))), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class)))
        .isInstanceOf(com.ouroboros.exception.BusinessException.class)
        .hasMessageContaining("provider auth profile 重复");
  }

  @Test
  void blankConnectionIdIsNotClaimed() {
    EnterpriseConnectionAuthProfileResolver resolver = new EnterpriseConnectionAuthProfileResolver(mock(EnterpriseConnectionCatalog.class), List.of(), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));

    assertThat(resolver.resolve(null).connectionClaimed()).isFalse();
    assertThat(resolver.resolve("  ").connectionClaimed()).isFalse();
    assertThat(resolver.resolve(null).profile()).isEmpty();
  }

  @Test
  void profileReturnedForAnotherConnectionIsRejected() {
    EnterpriseConnectionAuthProfileRequest request = new EnterpriseConnectionAuthProfileRequest(
        "feishu-main", "feishu", "derived-revision", "secret:feishu-main", Map.of());
    EnterpriseConnectionCatalog connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.findAuthProfileRequest("feishu-main")).thenReturn(Optional.of(request));
    // provider 返回了 key 不匹配的 profile：必须 fail-closed，而不是把别人的 profile 装上去。
    EnterpriseConnectionAuthProfileResolver resolver = new EnterpriseConnectionAuthProfileResolver(connectionConfigProvider,
    List.of(new TestProfileProvider("feishu", profile("another-connection"))), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));

    EnterpriseAuthProfileResolver.Resolution resolved = resolver.resolve("feishu-main");

    assertThat(resolved.connectionClaimed()).isTrue();
    assertThat(resolved.profile()).isEmpty();
  }

  @Test
  void profileWithoutConnectionScopedRevisionIsRejected() {
    EnterpriseConnectionAuthProfileRequest request = new EnterpriseConnectionAuthProfileRequest(
        "feishu-main", "feishu", "derived-revision", "secret:feishu-main", Map.of());
    EnterpriseConnectionCatalog connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.findAuthProfileRequest("feishu-main")).thenReturn(Optional.of(request));
    OAuth2ProviderSpec withoutRevision = profile("feishu-main");
    withoutRevision.setConnectionProfileRevision(null);
    EnterpriseConnectionAuthProfileResolver resolver = new EnterpriseConnectionAuthProfileResolver(connectionConfigProvider, List.of(new TestProfileProvider("feishu", withoutRevision)), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));

    assertThat(resolver.resolve("feishu-main").profile()).isEmpty();
  }

  @Test
  void profileProviderFailureFailsClosedInsteadOfPropagating() {
    EnterpriseConnectionAuthProfileRequest request = new EnterpriseConnectionAuthProfileRequest(
        "feishu-main", "feishu", "derived-revision", "secret:feishu-main", Map.of());
    EnterpriseConnectionCatalog connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.findAuthProfileRequest("feishu-main")).thenReturn(Optional.of(request));
    // record 是 final，无法继承；这里直接用匿名实现表达"facet 内部抛异常"。
    EnterpriseConnectionAuthProfileProvider throwing = new EnterpriseConnectionAuthProfileProvider() {
      @Override
      public String provider() {
        return "feishu";
      }

      @Override
      public Optional<ProviderSpec> resolve(EnterpriseConnectionAuthProfileRequest ignored) {
        throw new IllegalStateException("secret resolution exploded");
      }
    };
    EnterpriseConnectionAuthProfileResolver resolver = new EnterpriseConnectionAuthProfileResolver(connectionConfigProvider, List.of(throwing), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));

    EnterpriseAuthProfileResolver.Resolution resolved = resolver.resolve("feishu-main");

    assertThat(resolved.connectionClaimed()).isTrue();
    assertThat(resolved.profile()).isEmpty();
  }

  @Test
  void resolvesOnlyTheProfileOwnedByTheConnectionProvider() {
    EnterpriseConnectionAuthProfileRequest request = new EnterpriseConnectionAuthProfileRequest(
        "feishu-main", "feishu", "derived-revision", "secret:feishu-main", Map.of("departmentId", "dept-1"));
    EnterpriseConnectionCatalog connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.findAuthProfileRequest("feishu-main")).thenReturn(Optional.of(request));
    OAuth2ProviderSpec expectedProfile = profile("feishu-main");
    EnterpriseConnectionAuthProfileProvider profileProvider = new TestProfileProvider("feishu", expectedProfile);
    EnterpriseConnectionAuthProfileResolver resolver = new EnterpriseConnectionAuthProfileResolver(connectionConfigProvider, List.of(profileProvider), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));

    EnterpriseAuthProfileResolver.Resolution resolved = resolver.resolve("feishu-main");

    assertThat(resolved.connectionClaimed()).isTrue();
    assertThat(resolved.profile()).containsSame(expectedProfile);
  }

  @Test
  void rejectsProfileThatIsNotEnabledConnectionScopedOAuth2() {
    EnterpriseConnectionAuthProfileRequest request = new EnterpriseConnectionAuthProfileRequest(
        "feishu-main", "feishu", "derived-revision", "secret:feishu-main", Map.of());
    EnterpriseConnectionCatalog connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.findAuthProfileRequest("feishu-main")).thenReturn(Optional.of(request));
    OAuth2ProviderSpec invalidProfile = profile("feishu-main");
    invalidProfile.setConnectionScoped(false);
    EnterpriseConnectionAuthProfileResolver resolver = new EnterpriseConnectionAuthProfileResolver(connectionConfigProvider, List.of(new TestProfileProvider("feishu", invalidProfile)), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));

    EnterpriseAuthProfileResolver.Resolution resolved = resolver.resolve("feishu-main");

    assertThat(resolved.connectionClaimed()).isTrue();
    assertThat(resolved.profile()).isEmpty();
  }

  @Test
  void rejectsProfileWhoseRevisionDoesNotMatchThePublishedConnection() {
    EnterpriseConnectionAuthProfileRequest request = new EnterpriseConnectionAuthProfileRequest(
        "feishu-main", "feishu", "derived-revision", "secret:feishu-main", Map.of());
    EnterpriseConnectionCatalog connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.findAuthProfileRequest("feishu-main")).thenReturn(Optional.of(request));
    OAuth2ProviderSpec invalidProfile = profile("feishu-main");
    invalidProfile.setConnectionProfileRevision("another-revision");
    EnterpriseConnectionAuthProfileResolver resolver = new EnterpriseConnectionAuthProfileResolver(connectionConfigProvider, List.of(new TestProfileProvider("feishu", invalidProfile)), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));

    EnterpriseAuthProfileResolver.Resolution resolved = resolver.resolve("feishu-main");

    assertThat(resolved.connectionClaimed()).isTrue();
    assertThat(resolved.profile()).isEmpty();
  }

  @Test
  void returnsEmptyWhenNoProviderAuthenticationFacetIsRegistered() {
    EnterpriseConnectionCatalog connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.findAuthProfileRequest("feishu-main")).thenReturn(Optional.of(
        new EnterpriseConnectionAuthProfileRequest("feishu-main", "feishu", "derived-revision", null, Map.of())));
    EnterpriseConnectionAuthProfileResolver resolver = new EnterpriseConnectionAuthProfileResolver(connectionConfigProvider, List.of(), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));

    EnterpriseAuthProfileResolver.Resolution resolved = resolver.resolve("feishu-main");

    assertThat(resolved.connectionClaimed()).isTrue();
    assertThat(resolved.profile()).isEmpty();
  }

  @Test
  void disablesOnlyAuthenticationForAConnection() {
    EnterpriseConnectionAuthProfileRequest request = new EnterpriseConnectionAuthProfileRequest(
        "feishu-main", "feishu", "derived-revision", "secret:feishu-main", Map.of(), false);
    EnterpriseConnectionCatalog connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.findAuthProfileRequest("feishu-main")).thenReturn(Optional.of(request));
    OAuth2ProviderSpec profile = profile("feishu-main");
    EnterpriseConnectionAuthProfileProvider profileProvider = new TestProfileProvider("feishu", profile);
    EnterpriseConnectionAuthProfileResolver resolver = new EnterpriseConnectionAuthProfileResolver(connectionConfigProvider, List.of(profileProvider), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));

    EnterpriseAuthProfileResolver.Resolution resolved = resolver.resolve("feishu-main");

    assertThat(resolved.connectionClaimed()).isTrue();
    assertThat(resolved.profile()).isEmpty();
  }

  @Test
  void doesNotClaimAConnectionThatDoesNotExist() {
    EnterpriseConnectionCatalog connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.findAuthProfileRequest("unknown")).thenReturn(Optional.empty());
    EnterpriseConnectionAuthProfileResolver resolver = new EnterpriseConnectionAuthProfileResolver(connectionConfigProvider, List.of(), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));

    EnterpriseAuthProfileResolver.Resolution resolved = resolver.resolve("unknown");

    assertThat(resolved.connectionClaimed()).isFalse();
    assertThat(resolved.profile()).isEmpty();
  }

  @Test
  void claimsTheConnectionWhenLookupFailsSoAuthenticationFailsClosed() {
    EnterpriseConnectionCatalog connectionConfigProvider = mock(EnterpriseConnectionCatalog.class);
    when(connectionConfigProvider.findAuthProfileRequest("feishu-main"))
        .thenThrow(new IllegalStateException("configuration unavailable"));
    EnterpriseConnectionAuthProfileResolver resolver = new EnterpriseConnectionAuthProfileResolver(connectionConfigProvider, List.of(), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));

    EnterpriseAuthProfileResolver.Resolution resolved = resolver.resolve("feishu-main");

    assertThat(resolved.connectionClaimed()).isTrue();
    assertThat(resolved.profile()).isEmpty();
  }

  private static OAuth2ProviderSpec profile(String connectionId) {
    OAuth2ProviderSpec result = new OAuth2ProviderSpec();
    result.setKey(connectionId);
    result.setType("oauth2");
    result.setEnabled(true);
    result.setConnectionScoped(true);
    result.setConnectionProfileRevision("derived-revision");
    return result;
  }

  private record TestProfileProvider(String provider, OAuth2ProviderSpec profile)
      implements EnterpriseConnectionAuthProfileProvider {

    @Override
    public Optional<ProviderSpec> resolve(EnterpriseConnectionAuthProfileRequest request) {
      return Optional.of(profile);
    }
  }
}
