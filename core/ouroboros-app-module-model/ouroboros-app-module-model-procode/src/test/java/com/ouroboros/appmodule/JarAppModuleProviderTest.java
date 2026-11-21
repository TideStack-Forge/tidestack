package com.ouroboros.appmodule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.ouroboros.metadata.resource.MetadataResource;

class JarAppModuleProviderTest {
  private static final String MODULES = """
      [{"fullName":"baseInfo"},{"fullName":"laboratory","subMenus":["sample"]},
       {"fullName":"product","authorities":["report"]}]
      """;

  @Test
  void subsequentUsersKeepAllModulesAfterResourcesBecomeUnavailable() {
    var reads = new AtomicInteger();
    var provider = new JarAppModuleProvider(() -> {
      if (reads.incrementAndGet() > 1) {
        throw new UncheckedIOException(new IOException("jar unavailable"));
      }
      return List.of(new MetadataResource("app-modules.json", MODULES));
    });
    var first = provider.getAppModules();
    first.get(1).getSubMenus().clear();
    first.get(2).getAuthorities().clear();
    first.clear();

    var second = provider.getAppModules();

    assertEquals(List.of("baseInfo", "laboratory", "product"), second.stream().map(AppModule::getFullName).toList());
    assertEquals(List.of("sample"), second.get(1).getSubMenus());
    assertEquals(List.of("report"), second.get(2).getAuthorities());
    assertEquals(1, reads.get());
  }

  @Test
  void concurrentFirstReadsPublishOneCompleteSnapshot() throws Exception {
    var reads = new AtomicInteger();
    var provider = new JarAppModuleProvider(() -> {
      reads.incrementAndGet();
      return List.of(new MetadataResource("app-modules.json", MODULES));
    });
    try (var executor = Executors.newFixedThreadPool(8)) {
      var tasks = new ArrayList<Callable<List<AppModule>>>();
      for (int i = 0; i < 32; i++) {
        tasks.add(provider::getAppModules);
      }
      for (var result : executor.invokeAll(tasks, 5, TimeUnit.SECONDS)) {
        assertEquals(3, result.get().size());
      }
    }
    assertEquals(1, reads.get());
  }

  @Test
  void failedParseKeepsHealthyDefinitionsAndCanRetry() {
    var invalid = new AtomicReference<>("{");
    var provider = new JarAppModuleProvider(() -> List.of(
        new MetadataResource("good.json", MODULES), new MetadataResource("broken.json", invalid.get())));

    assertEquals(3, provider.getAppModules().size());
    invalid.set("[]");

    assertEquals(3, provider.getAppModules().size());
  }

  @Test
  void failedReadCanRecoverAndValidEmptyDefinitionsAreCached() {
    var reads = new AtomicInteger();
    var provider = new JarAppModuleProvider(() -> {
      if (reads.incrementAndGet() == 1) {
        throw new UncheckedIOException(new IOException("enumeration failed"));
      }
      return List.of();
    });
    assertTrue(provider.getAppModules().isEmpty());
    assertEquals(List.of(), provider.getAppModules());
    assertEquals(List.of(), provider.getAppModules());
    assertEquals(2, reads.get());
  }

  @Test
  void nullDefinitionsAreNotPublishedAsSuccessfulSnapshots() {
    for (var content : List.of("null", "[null]")) {
      var text = new AtomicReference<>(content);
      var provider = new JarAppModuleProvider(() -> List.of(new MetadataResource("null.json", text.get())));

      assertTrue(provider.getAppModules().isEmpty());
      text.set(MODULES);

      assertEquals(3, provider.getAppModules().size());
    }
  }
}
