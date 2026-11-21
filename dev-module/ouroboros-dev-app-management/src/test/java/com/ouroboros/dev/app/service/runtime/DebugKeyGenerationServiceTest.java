package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.app.model.DebugKeyMaterial;
import com.ouroboros.dev.runtime.model.DebugAccessStrategy;

class DebugKeyGenerationServiceTest {

  @Test
  void shouldGenerateThirtyTwoCharacterHexDebugKey() {
    DebugKeyGenerationService service = new DebugKeyGenerationService(Duration.ofDays(90));

    String debugKey = service.generateDebugKey();

    assertEquals(32, debugKey.length());
    assertTrue(debugKey.matches("[0-9a-f]{32}"));
  }

  @Test
  void shouldGenerateUniqueDebugKeysAcrossMultipleCalls() {
    DebugKeyGenerationService service = new DebugKeyGenerationService(Duration.ofDays(90));
    Set<String> keys = new HashSet<String>();

    for (int index = 0; index < 256; index++) {
      assertTrue(keys.add(service.generateDebugKey()));
    }
  }

  @Test
  void shouldCalculateExpiresAtUsingProvidedDuration() {
    DebugKeyGenerationService service = new DebugKeyGenerationService(Duration.ofDays(90));
    Duration duration = Duration.ofHours(12);

    Instant before = Instant.now();
    Instant expiresAt = service.calculateExpiresAt(duration);
    Instant after = Instant.now();

    assertFalse(expiresAt.isBefore(before.plus(duration).minusSeconds(1)));
    assertFalse(expiresAt.isAfter(after.plus(duration).plusSeconds(1)));
  }

  @Test
  void shouldCreateDebugKeyMaterialWithDefaultExpiryAndRuntimeStrategy() {
    DebugKeyGenerationService service = new DebugKeyGenerationService(Duration.ofDays(30));

    Instant before = Instant.now();
    DebugKeyMaterial material = service.createDebugKeyMaterial("alice/dev", "integration test");
    Instant after = Instant.now();

    assertEquals("alice/dev", material.getProfileId());
    assertNotNull(material.getDebugKey());
    assertEquals(32, material.getDebugKey().length());
    assertTrue(material.getDebugKey().matches("[0-9a-f]{32}"));
    assertEquals(DebugAccessStrategy.SESSION_TUNNEL, material.getRuntimeStrategy());
    assertEquals("integration test", material.getDescription());
    assertNotNull(material.getCreatedAt());
    assertNotNull(material.getExpiresAt());
    assertTrue(!material.getCreatedAt().isBefore(before) && !material.getCreatedAt().isAfter(after));
    assertEquals(Duration.ofDays(30).toDays(), Duration.between(material.getCreatedAt(), material.getExpiresAt()).toDays());
    assertEquals(null, material.getLastRotatedAt());
    assertEquals(null, material.getCreatedBy());
    assertEquals(null, material.getUpdatedBy());
  }
}
