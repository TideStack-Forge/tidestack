package com.ouroboros.security.externalauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.security.externalauth.config.ProviderSpec;
import com.ouroboros.security.rbac.service.UserSsoService;

class EnterpriseLoginProofTest {
  private final ExternalAuthService service = new ExternalAuthService();
  private final UserSsoService bindings = mock(UserSsoService.class);
  private final CacheManager cache = mock(CacheManager.class);
  private final AtomicReference<EnterpriseAuthProfileResolver.Resolution> profile = new AtomicReference<>();
  private final Map<String, Object> entries = new HashMap<>();
  private final ExternalIdentity identity = ExternalIdentity.of("wecom-main", "member-1", "Alice", null, Map.of());

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "userSsoService", bindings);
    ReflectionTestUtils.setField(service, "cacheManager", cache);
    ReflectionTestUtils.setField(service, "enterpriseAuthProfileResolver",
        (EnterpriseAuthProfileResolver) connection -> profile.get());
    when(cache.get(anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(entries.get(call.getArgument(1))));
    doAnswer(call -> { entries.put(call.getArgument(1), call.getArgument(2)); return null; })
        .when(cache).put(anyString(), anyString(), any(), anyLong());
    doAnswer(call -> { entries.remove(call.getArgument(1)); return null; }).when(cache).evict(anyString(), anyString());
    profile.set(current("generation-1"));
  }

  @Test
  void sharedCacheRoundTripPreservesIdentityAttemptsAndConnectionRevision() throws Exception {
    var serializer = new org.springframework.core.serializer.support.SerializingConverter();
    var deserializer = new org.springframework.core.serializer.support.DeserializingConverter();
    doAnswer(call -> {
      entries.put(call.getArgument(1), deserializer.convert(serializer.convert(call.getArgument(2))));
      return null;
    }).when(cache).put(anyString(), anyString(), any(), anyLong());
    String token = service.processExternalIdentity(identity, "trace", "generation-1").get().getBindingToken();
    service.decrementBindingToken(token);
    assertThat(service.validateBindingToken(token).orElseThrow().getExternalId()).isEqualTo(identity.getExternalId());
    profile.set(current("generation-2"));
    assertThat(service.validateBindingToken(token)).isEmpty();
  }

  @Test
  void pendingBindingKeepsTheVerifiedGenerationAndBecomesInvalidAfterRotation() {
    String token = service.processExternalIdentity(identity, "trace", "generation-1").get().getBindingToken();
    assertThat(service.validateBindingToken(token)).contains(identity);
    profile.set(current("generation-2"));
    assertThat(service.validateBindingToken(token)).isEmpty();
    profile.set(current("generation-1"));
    assertThat(service.validateBindingToken(token)).isEmpty();
    assertThat(entries).doesNotContainKey(token);
  }

  @Test
  void passwordRetryDoesNotDropTheConnectionGeneration() {
    String token = service.processExternalIdentity(identity, "trace", "generation-1").get().getBindingToken();
    service.decrementBindingToken(token);
    assertThat(service.validateBindingToken(token)).contains(identity);
    profile.set(EnterpriseAuthProfileResolver.Resolution.claimedWithoutProfile());
    assertThat(service.validateBindingToken(token)).isEmpty();
  }

  @Test
  void rejectsAProofChangedDuringTheRemoteIdentityExchangeBeforeLookingUpAnyBinding() {
    profile.set(current("generation-2"));
    var result = service.processExternalIdentity(identity, "trace", "generation-1");
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getCode()).isEqualTo(40102);
    verifyNoInteractions(bindings);
    assertThat(entries).isEmpty();
  }

  @Test
  void pendingBindingFailsClosedWhenItsPluginDisappearsOrTheDatabaseReadFails() {
    String token = service.processExternalIdentity(identity, "trace", "generation-1").get().getBindingToken();
    profile.set(EnterpriseAuthProfileResolver.Resolution.notClaimed());
    assertThat(service.validateBindingToken(token)).isEmpty();
    profile.set(current("generation-1"));
    String second = service.processExternalIdentity(identity, "trace", "generation-1").get().getBindingToken();
    ReflectionTestUtils.setField(service, "enterpriseAuthProfileResolver", (EnterpriseAuthProfileResolver) connection -> {
      throw new IllegalStateException("database unavailable");
    });
    assertThat(service.validateBindingToken(second)).isEmpty();
  }

  @Test
  void ordinaryIdentitySourcesKeepTheirOriginalBindingFlowWithoutAnEnterprisePlugin() {
    ReflectionTestUtils.setField(service, "enterpriseAuthProfileResolver", null);
    String token = service.processExternalIdentity(identity, "trace").get().getBindingToken();
    assertThat(service.validateBindingToken(token)).contains(identity);
    assertThat(service.processExternalIdentity(identity, "trace", "generation-1").isLeft()).isTrue();
  }

  private static EnterpriseAuthProfileResolver.Resolution current(String revision) {
    var spec = new ProviderSpec();
    spec.setKey("wecom-main");
    spec.setEnabled(true);
    spec.setConnectionScoped(true);
    spec.setConnectionProfileRevision(revision);
    return EnterpriseAuthProfileResolver.Resolution.claimed(spec);
  }
}
