package com.ouroboros.workflow.approval.external.runtime.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.identity.mapping.EnterpriseIdentityMappingService;
import com.ouroboros.security.identity.mapping.PlatformPrincipalRef;

class ConnectionScopedIdentityResolverTest {

  private final EnterpriseIdentityMappingService mappingService =
      mock(EnterpriseIdentityMappingService.class);
  private final ConnectionScopedIdentityResolver resolver =
      new ConnectionScopedIdentityResolver(mappingService);

  @Test
  void resolvesBothDirectionsWithinConnectionScope() {
    when(mappingService.toExternalMember("conn-1", new PlatformPrincipalRef("platform-1")))
        .thenReturn(Optional.of("member-1"));
    when(mappingService.toPlatform("conn-1", "member-1"))
        .thenReturn(Optional.of(new PlatformPrincipalRef("platform-1")));

    assertThat(resolver.toExternalMember("platform-1", "conn-1")).contains("member-1");
    assertThat(resolver.toPlatformUser("member-1", "conn-1")).contains("platform-1");
  }

  @Test
  void doesNotUseBindingFromAnotherConnection() {
    when(mappingService.toExternalMember("conn-1", new PlatformPrincipalRef("platform-1")))
        .thenReturn(Optional.empty());

    assertThat(resolver.toExternalMember("platform-1", "conn-1")).isEmpty();
  }

  @Test
  void missingInboundMappingDoesNotGuessPlatformIdentity() {
    when(mappingService.toPlatform("conn-1", "member-1"))
        .thenReturn(Optional.empty());

    assertThat(resolver.toPlatformUser("member-1", "conn-1")).isEmpty();
  }

  @Test
  void blankIdentifiersAreRejected() {
    assertThat(resolver.toExternalMember("", "conn-1")).isEmpty();
    assertThat(resolver.toPlatformUser(" ", "conn-1")).isEmpty();
    assertThat(resolver.toPlatformUser("member-1", "")).isEmpty();
  }
}
