package com.ouroboros.ability.auth.config;

import com.ouroboros.security.externalauth.config.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import com.ouroboros.cache.MemoryCacheManager;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.ability.auth.config.ExternalAuthConfigurationValidator;
import com.ouroboros.security.externalauth.ExternalAuthProviderFactory;
import com.ouroboros.security.externalauth.BindingRateLimiter;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.ability.auth.support.MutableAuthConfiguration;

class ExternalAuthConfigurationTest {
  @Test
  void switchesTheRuntimeCatalogWhenProvidersAreAddedDisabledAndRemoved() {
    var values = new MutableAuthConfiguration();
    var configuration = new UnifiedExternalAuthConfiguration(values.service);
    var factory = new ExternalAuthProviderFactory();
    ReflectionTestUtils.setField(factory, "externalAuthConfiguration", configuration);
    ReflectionTestUtils.setField(factory, "specSource", new ConfigPropertiesProviderSpecSource(configuration));
    ReflectionTestUtils.setField(factory, "cacheManager", new MemoryCacheManager());
    ReflectionTestUtils.setField(factory, "secretResolver", new CompositeSecretResolver(List.of()));
    values.put(UnifiedExternalAuthConfiguration.KEY, """
        {"ldap":[{"key":"corp","urls":["ldaps://corp"],"baseDn":"dc=corp","externalIdAttribute":"uid"}]}
        """);
    factory.init();
    var oldProviders = factory.getAllProviders();
    assertTrue(factory.getDirectProvider("corp").isPresent());
    values.put(UnifiedExternalAuthConfiguration.KEY, """
        {"ldap":[{"key":"corp","enabled":false}]}
        """);
    assertTrue(factory.getDirectProvider("corp").isEmpty());
    assertTrue(factory.getAllProviders().isEmpty());
    assertEquals(1, oldProviders.size(), "in-flight readers retain a stable snapshot");
    values.put(UnifiedExternalAuthConfiguration.KEY, """
        {"cas":[{"key":"sso","casBaseUrl":"https://sso.example"}],"publicBaseUrl":"https://platform.example"}
        """);
    assertTrue(factory.getRedirectProvider("sso", "cas").isPresent());
    assertTrue(factory.getProvider("corp").isEmpty());
    values.put(UnifiedExternalAuthConfiguration.KEY, """
        {"cas":[]}
        """);
    assertTrue(factory.getAllProviders().isEmpty());
  }

  @Test
  void refusesCorruptStoredConfigurationInsteadOfReactivatingDefaults() {
    var values = new MutableAuthConfiguration();
    values.put(UnifiedExternalAuthConfiguration.KEY, "broken-json");
    assertThrows(com.ouroboros.exception.PlatformException.class, () -> new UnifiedExternalAuthConfiguration(values.service).get());
  }

  @Test
  void appliesRateLimitEditsWithoutDiscardingAttemptCounters() {
    var values = new MutableAuthConfiguration();
    values.put(UnifiedExternalAuthConfiguration.KEY, "{}");
    var limiter = new BindingRateLimiter();
    ReflectionTestUtils.setField(limiter, "cacheManager", new MemoryCacheManager());
    ReflectionTestUtils.setField(limiter, "properties", new UnifiedExternalAuthConfiguration(values.service));
    assertTrue(limiter.checkCrossTokenLimits("corp", "subject", "alice", "127.0.0.1").isEmpty());
    values.put(UnifiedExternalAuthConfiguration.KEY, """
        {"bindingRateLimit":{"byExternalId":"1/10m","sourceIpEnabled":false}}
        """);
    assertEquals("Rate limit exceeded for external identity",
        limiter.checkCrossTokenLimits("corp", "subject", "alice", "127.0.0.1").orElseThrow());
    values.put(UnifiedExternalAuthConfiguration.KEY, """
        {"bindingRateLimit":{"byExternalId":"10/10m","sourceIpEnabled":false,
          "providers":{"corp":{"byExternalId":"3/10m"}}}}
        """);
    assertTrue(limiter.checkCrossTokenLimits("corp", "subject", "alice", null).isEmpty());
    assertTrue(limiter.checkCrossTokenLimits("corp", "subject", "alice", null).isEmpty());
    assertTrue(limiter.checkCrossTokenLimits("corp", "subject", "alice", null).isPresent());
    assertTrue(limiter.checkCrossTokenLimits("another", "subject", "alice", null).isEmpty());
  }

  @Test
  void rejectsInvalidConfigurationBeforeSaving() {
    var validator = new ExternalAuthConfigurationValidator();
    assertThrows(ValidationException.class, () -> validator.validate("""
        {"oauth2":[{"key":"corp"}]}
        """));
    assertThrows(ValidationException.class, () -> validator.validate("""
        {"ldap":[{"key":"same","enabled":false},{"key":"same","enabled":false}]}
        """));
    assertThrows(ValidationException.class, () -> validator.validate("""
        {"bindingRateLimit":{"byUsername":"invalid"}}
        """));
  }
}
