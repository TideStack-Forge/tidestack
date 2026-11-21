package com.ouroboros.security.externalauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ouroboros.security.externalauth.config.ProviderSpec;

@ExtendWith(MockitoExtension.class)
class ExternalAuthProviderInfoServiceTest {

  @Mock
  private ExternalAuthProviderFactory providerFactory;

  @Mock
  private ProviderHealthChecker healthyChecker;

  @Mock
  private ProviderHealthChecker unhealthyChecker;

  private ExternalAuthProviderInfoService service;

  @BeforeEach
  void setUp() {
    service = new ExternalAuthProviderInfoService(providerFactory);
  }

  @Test
  void shouldListEnabledProvidersForWebAppInfoAndBasicApi() {
    ProviderSpec ldapSpec = new ProviderSpec();
    ldapSpec.setKey("corp-ldap");
    ldapSpec.setType("ldap");
    ldapSpec.setDisplayName("Corp LDAP");
    ldapSpec.setEnabled(true);

    ProviderSpec oauthSpec = new ProviderSpec();
    oauthSpec.setKey("oidc-main");
    oauthSpec.setType("oauth2");
    oauthSpec.setDisplayName("OIDC Main");
    oauthSpec.setEnabled(true);

    ProviderSpec casSpec = new ProviderSpec();
    casSpec.setKey("cas-main");
    casSpec.setType("cas");
    casSpec.setDisplayName("CAS Main");
    casSpec.setEnabled(true);

    ProviderSpec disabledSpec = new ProviderSpec();
    disabledSpec.setKey("disabled-cas");
    disabledSpec.setType("cas");
    disabledSpec.setDisplayName("Disabled CAS");
    disabledSpec.setEnabled(false);

    Collection<ExternalAuthProvider> providers = Arrays.asList(
        new StubProvider(ldapSpec),
        new StubProvider(oauthSpec),
        new StubProvider(casSpec),
        new StubProvider(disabledSpec)
    );

    when(providerFactory.getAllProviders()).thenReturn(providers);
    when(providerFactory.isUnavailableAtStartup("corp-ldap")).thenReturn(false);
    when(providerFactory.isUnavailableAtStartup("oidc-main")).thenReturn(false);
    when(providerFactory.isUnavailableAtStartup("cas-main")).thenReturn(false);
    when(providerFactory.getHealthChecker("corp-ldap")).thenReturn(healthyChecker);
    when(providerFactory.getHealthChecker("oidc-main")).thenReturn(unhealthyChecker);
    when(providerFactory.getHealthChecker("cas-main")).thenReturn(healthyChecker);
    when(healthyChecker.isHealthy()).thenReturn(true);
    when(unhealthyChecker.isHealthy()).thenReturn(false);

    List<ExternalAuthProviderInfoService.ProviderInfo> result = service.listProviders();

    assertEquals(3, result.size());
    assertEquals("corp-ldap", result.get(0).getKey());
    assertNull(result.get(0).getLoginUrl());
    assertEquals("healthy", result.get(0).getAvailability());
    assertEquals("/api/auth/oauth2/oidc-main/authorize", result.get(1).getLoginUrl());
    assertEquals("unhealthy-runtime", result.get(1).getAvailability());
    assertEquals("cas-main", result.get(2).getKey());
    assertEquals("/api/auth/cas/cas-main/login", result.get(2).getLoginUrl());
    assertEquals("healthy", result.get(2).getAvailability());
  }

  @Test
  void connectionScopedBridgeUsesTheExistingOAuthBrowserEntry() {
    var spec = new ProviderSpec();
    spec.setKey("wecom-main");
    spec.setType("wecom-enterprise");
    spec.setConnectionScoped(true);
    spec.setEnabled(true);
    when(providerFactory.getAllProviders()).thenReturn(List.of(new StubProvider(spec)));
    assertEquals("/api/auth/oauth2/wecom-main/authorize", service.listProviders().get(0).getLoginUrl());
  }

  @Test
  void shouldExposeProvidersAsWebAppInfoField() {
    ProviderSpec oauthSpec = new ProviderSpec();
    oauthSpec.setKey("oidc-main");
    oauthSpec.setType("oauth2");
    oauthSpec.setDisplayName("OIDC Main");
    oauthSpec.setEnabled(true);

    when(providerFactory.getAllProviders()).thenReturn(Arrays.asList(new StubProvider(oauthSpec)));
    when(providerFactory.isUnavailableAtStartup("oidc-main")).thenReturn(false);

    assertTrue(service.getWebAppInfo().containsKey("authProviders"));
  }

  private static class StubProvider implements ExternalAuthProvider {

    private final ProviderSpec spec;

    private StubProvider(ProviderSpec spec) {
      this.spec = spec;
    }

    @Override
    public String getProviderKey() {
      return spec.getKey();
    }

    @Override
    public ProviderSpec getSpec() {
      return spec;
    }
  }
}
