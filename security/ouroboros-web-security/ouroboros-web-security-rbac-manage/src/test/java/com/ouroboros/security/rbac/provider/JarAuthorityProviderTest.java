package com.ouroboros.security.rbac.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.authority.Authority;

class JarAuthorityProviderTest {

  @Test
  void metadataAuthorityResourceOverridesLegacyResourceInsideJarProvider() {
    var authorities = new JarAuthorityProvider().getAuthority("metadata.order.probe");

    assertEquals(1, authorities.size());
    assertEquals("Metadata Authority", authorities.get(0).getTitle());
    assertEquals("/api/metadata", firstApi(authorities.get(0)));
  }

  private String firstApi(Authority authority) {
    return authority.getApis().get(0);
  }
}
