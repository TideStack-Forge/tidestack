package com.ouroboros.security.identity.mapping;

import java.util.Optional;

/** Additional verified business associations consumed by the existing member mapping service. */
public interface EnterpriseIdentityMappingContributor {
  Optional<PlatformPrincipalRef> toPlatform(String connectionId, String externalMemberIdentity);
  Optional<String> toExternalMember(String connectionId, PlatformPrincipalRef principal);
}
