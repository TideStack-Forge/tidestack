package com.ouroboros.enterprise.runtime.credential;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.enterprise.EnterpriseConnectionCredentialMapper;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;
import com.ouroboros.enterprise.EnterpriseCredential;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.util.Json;

class ManagedEnterpriseConnectionTest {
  private final EnterpriseCredentialCipher cipher = new EnterpriseCredentialCipher("key", Map.of("key", Base64.getEncoder().encodeToString(new byte[32])));

  @Test
  void rotationIsVisibleAcrossIndependentCatalogsAndChangesTheAuthenticationProfileRevision() {
    var database = mock(ConfigurationService.class);
    var first = cipher.encrypt("test", "main", "first-secret");
    var persisted = new AtomicReference<>(snapshot(EnterpriseManagedCredentials.KEY, Map.of("main", first), "cred-1"));
    when(database.getSnapshot(EnterpriseManagedCredentials.KEY)).thenAnswer(call -> Optional.of(persisted.get()));
    when(database.getSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY)).thenReturn(Optional.of(connections("connection-1")));
    var store = new EnterpriseManagedCredentials(database, cipher);
    var nodeA = catalog(database, store); var nodeB = catalog(database, store);
    var originalProfile = nodeA.findAuthProfileRequest("main").orElseThrow();
    assertThat(nodeA.resolve("main").orElseThrow().credential()).isEqualTo(new Credential("first-secret"));
    assertThat(nodeB.resolve("main").orElseThrow().config()).containsEntry("credentialGeneration", first.credentialVersion());
    var replacement = cipher.encrypt("test", "main", "second-secret");
    persisted.set(snapshot(EnterpriseManagedCredentials.KEY, Map.of("main", replacement), "cred-2"));
    assertThat(nodeB.resolve("main").orElseThrow().credential()).isEqualTo(new Credential("second-secret"));
    assertThat(nodeA.resolve("main").orElseThrow().config()).containsEntry("credentialGeneration", replacement.credentialVersion());
    assertThat(nodeA.findAuthProfileRequest("main").orElseThrow().revision()).isNotEqualTo(originalProfile.revision());
    assertThat(Json.toJsonString(nodeA.get("main").orElseThrow())).doesNotContain("first-secret", "second-secret", replacement.ciphertext());
  }

  @Test
  void concurrentPublicationRetriesInsteadOfPairingDifferentConnectionAndCredentialObservations() {
    var database = mock(ConfigurationService.class);
    var encrypted = cipher.encrypt("test", "main", "secret");
    when(database.getSnapshot(EnterpriseManagedCredentials.KEY)).thenReturn(Optional.of(snapshot(EnterpriseManagedCredentials.KEY, Map.of("main", encrypted), "cred-1")));
    when(database.getSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY))
        .thenReturn(Optional.of(connections("old"))).thenReturn(Optional.of(connections("new")));
    var catalog = catalog(database, new EnterpriseManagedCredentials(database, cipher));
    assertThat(catalog.resolve("main")).isPresent();
    verify(database, times(4)).getSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY);
  }

  @Test
  void persistentPublicationChurnOrDatabaseFailureCannotSilentlyReuseAnOlderCredential() {
    var database = mock(ConfigurationService.class);
    var encrypted = cipher.encrypt("test", "main", "secret");
    when(database.getSnapshot(EnterpriseManagedCredentials.KEY)).thenReturn(Optional.of(snapshot(EnterpriseManagedCredentials.KEY, Map.of("main", encrypted), "cred-1")));
    var counter = new java.util.concurrent.atomic.AtomicInteger();
    when(database.getSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY))
        .thenAnswer(call -> Optional.of(connections("revision-" + counter.incrementAndGet())));
    var catalog = catalog(database, new EnterpriseManagedCredentials(database, cipher));
    assertThatThrownBy(() -> catalog.resolve("main")).hasMessageContaining("配置正在变更");
    when(database.getSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY)).thenThrow(new IllegalStateException("database unavailable"));
    assertThatThrownBy(() -> catalog.resolve("main")).hasMessageContaining("database unavailable");
  }

  @Test
  void existingCompositeResolvesManagedPayloadAndJsonPointerWithoutExposingCiphertext() {
    var database = mock(ConfigurationService.class);
    var encrypted = cipher.encrypt("test", "main", "{\"secret\":\"private-value\"}");
    when(database.getSnapshot(EnterpriseManagedCredentials.KEY)).thenReturn(Optional.of(snapshot(EnterpriseManagedCredentials.KEY, Map.of("main", encrypted), "cred-1")));
    var resolver = new CompositeSecretResolver(List.of(new ManagedEnterpriseSecretResolver(new EnterpriseManagedCredentials(database, cipher))));
    assertThat(resolver.resolve("managed:main#/secret")).contains("private-value");
    assertThat(resolver.resolve("managed:missing")).isEmpty();
    assertThatThrownBy(() -> resolver.resolve("managed:../other")).hasMessageContaining("引用无效");
  }

  @Test
  void malformedStoredJsonCannotBeHiddenByConfigurationConversionToAnEmptyMap() {
    var database = mock(ConfigurationService.class);
    when(database.getSnapshot(EnterpriseManagedCredentials.KEY)).thenReturn(Optional.of(new ConfigurationSnapshot(
        EnterpriseManagedCredentials.KEY, Map.of(), "{broken-json}", "cred-1", null)));
    var store = new EnterpriseManagedCredentials(database, cipher);
    assertThatThrownBy(() -> store.resolve("managed:main")).hasMessageContaining("配置损坏");
    assertThatThrownBy(() -> EnterpriseManagedCredentials.records(Map.of("other", cipher.encrypt("test", "main", "secret"))))
        .hasMessageContaining("记录损坏");
  }

  private EnterpriseConnectionCatalog catalog(ConfigurationService service, EnterpriseManagedCredentials store) {
    var provider = new EnterpriseConnectionProvider() { @Override public String provider() { return "test"; } };
    return new EnterpriseConnectionCatalog(service, Optional.empty(), List.of(new Mapper()),
        EnterpriseConnectionProviderRegistry.of(List.of(provider)), Optional.of(store));
  }
  private static ConfigurationSnapshot connections(String revision) {
    return snapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY,
        List.of(Map.of("provider", "test", "connectionId", "main", "credentialRef", "managed:main")), revision);
  }
  private static ConfigurationSnapshot snapshot(String key, Object value, String revision) {
    return new ConfigurationSnapshot(key, value, Json.toJsonString(value), revision, null);
  }
  private record Credential(String secret) implements EnterpriseCredential {
    @Override public Map<String, String> applicationIdentity() { return Map.of("app", "test"); }
  }
  private static final class Mapper implements EnterpriseConnectionCredentialMapper {
    @Override public String provider() { return "test"; }
    @Override public EnterpriseCredential map(String value) { return new Credential(value); }
  }
}
