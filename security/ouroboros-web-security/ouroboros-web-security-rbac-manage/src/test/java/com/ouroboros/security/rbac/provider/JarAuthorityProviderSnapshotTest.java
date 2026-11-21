package com.ouroboros.security.rbac.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.util.Json;

class JarAuthorityProviderSnapshotTest {
  @Test
  void successfulSnapshotDoesNotDeserializeDefinitionsOnEveryLookup() {
    try (var json = mockStatic(Json.class, CALLS_REAL_METHODS)) {
      var provider = new JarAuthorityProvider(spec -> List.of(
          new MetadataResource("good.authority.json", "{\"fullName\":\"good\",\"title\":\"Good\"}")));
      json.clearInvocations();

      assertEquals("Good", provider.getAuthority("good").getFirst().getTitle());
      assertEquals(1, provider.getAllAuthorities().size());
      assertTrue(provider.getAuthority("missing").isEmpty());

      json.verifyNoInteractions();
    }
  }

  @Test
  void concurrentLookupsReuseTheCompletedSnapshot() throws Exception {
    var provider = new JarAuthorityProvider(spec -> List.of(
        new MetadataResource("good.authority.json", "{\"fullName\":\"good\"}")));
    var expected = provider.getAuthority("good").getFirst();
    List<Callable<Authority>> lookups = IntStream.range(0, 64)
        .mapToObj(index -> (Callable<Authority>) () -> provider.getAuthority("good").getFirst())
        .toList();

    try (var executor = Executors.newFixedThreadPool(8)) {
      for (var result : executor.invokeAll(lookups, 10, TimeUnit.SECONDS)) {
        assertSame(expected, result.get());
      }
    }
  }

  @Test
  void malformedAuthorityIsSkippedAndSameInstanceCanRecover() {
    var broken = new java.util.concurrent.atomic.AtomicReference<>("{");
    var provider = new JarAuthorityProvider(spec -> List.of(
        new MetadataResource("good.authority.json", "{\"fullName\":\"good\"}"),
        new MetadataResource("broken.authority.json", broken.get())));
    assertEquals(1, provider.getAllAuthorities().size());
    assertTrue(provider.getAuthority("broken").isEmpty());
    broken.set("{\"fullName\":\"recovered\"}");
    assertEquals(1, provider.getAuthority("recovered").size());
    assertEquals(2, provider.getAllAuthorities().size());
  }

  @Test
  void sourceFailureIsNonblockingAndSameInstanceCanRecover() {
    var broken = new java.util.concurrent.atomic.AtomicBoolean(true);
    var provider = new JarAuthorityProvider(spec -> {
      if (broken.get()) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("good.authority.json", "{\"fullName\":\"good\"}"));
    });
    assertTrue(provider.getAllAuthorities().isEmpty());
    broken.set(false);
    assertEquals(1, provider.getAllAuthorities().size());
  }

  @Test
  void recreatedProviderKeepsDefinitionsAfterResourceRemovalAndCallerMutation() throws Exception {
    var file = Path.of(getClass().getResource("/META-INF/ouroboros/metadata/probe.authority.json").toURI());
    var content = Files.readString(file);
    var first = new JarAuthorityProvider().getAuthority("metadata.order.probe").getFirst();
    first.getApis().clear();
    try {
      Files.delete(file);

      var second = new JarAuthorityProvider().getAuthority("metadata.order.probe").getFirst();

      assertEquals("/api/metadata", second.getApis().getFirst());
    } finally {
      Files.writeString(file, content);
    }
  }
}
