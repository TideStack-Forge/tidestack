package com.ouroboros.security.claims;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimsCoreBehaviorTest {

  @Test
  void builderAndImmutableClaimsSupportTypedReadsAndFallbacks() {
    var claims = Claims.builder()
        .claim("a", "x")
        .claim("n", 12)
        .claim("b", true)
        .claim("nullIgnored", null)
        .claimOrNull("nullable", null)
        .claimIfAbsent("a", "y")
        .claimIfAbsent("new", "z")
        .build();

    assertEquals("x", claims.getString("a").orElse(null));
    assertEquals(Integer.valueOf(12), claims.getInteger("n").orElse(null));
    assertEquals(Boolean.TRUE, claims.getBoolean("b").orElse(null));
    assertEquals("12", claims.get("n", String.class).orElse(null));
    assertFalse(claims.get("n", Boolean.class).isPresent());
    assertTrue(claims.has("nullable"));
    assertTrue(claims.getTypes().contains("new"));
    assertFalse(claims.has("nullIgnored"));
  }

  @Test
  void immutableClaimsAreDetachedFromInputAndReadableAsMap() {
    var source = new HashMap<String, Object>();
    source.put("k", "v1");
    var claims = new ImmutableClaims(source);

    source.put("k", "v2");

    assertEquals("v1", claims.getString("k").orElse(null));
    assertEquals("Claimsk=v1", claims.toString().replace("{", "").replace("}", ""));
    assertEquals("v1", claims.asMap().get("k"));
  }

  @Test
  void emptyClaimsAndStandardClaimNamesWork() {
    var empty = Claims.empty();
    assertFalse(empty.has(StandardClaims.SUB));
    assertNull(empty.getString(StandardClaims.EMAIL).orElse(null));
  }
}
