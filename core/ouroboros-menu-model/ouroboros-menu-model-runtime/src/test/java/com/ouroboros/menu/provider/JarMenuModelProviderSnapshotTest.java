package com.ouroboros.menu.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.ouroboros.metadata.resource.MetadataResource;

class JarMenuModelProviderSnapshotTest {
  @Test
  void readAndParseFailuresAreLoggedWithContextAndOriginalStackTrace() {
    var logger = (Logger) LoggerFactory.getLogger(JarMenuModelProvider.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var failure = new IllegalStateException("jar read failure");
      var unreadable = new JarMenuModelProvider(spec -> {
        throw failure;
      });
      assertTrue(unreadable.getAllMenus().isEmpty());
      assertTrue(appender.list.stream().anyMatch(event -> event.getThrowableProxy() != null
          && event.getThrowableProxy().getMessage().equals(failure.getMessage())));
      appender.list.clear();

      var malformed = new JarMenuModelProvider(spec -> List.of(
          new MetadataResource("broken.menu.json", "{"),
          new MetadataResource("good.menu.json", "{\"fullName\":\"good\"}")));
      assertEquals(1, malformed.getAllMenus().size());
      var parseLog = appender.list.stream()
          .filter(event -> event.getFormattedMessage().contains("broken.menu.json")).findFirst().orElseThrow();
      assertNotNull(parseLog.getThrowableProxy());
      assertTrue(parseLog.getThrowableProxy().getStackTraceElementProxyArray().length > 0);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void malformedMenuIsSkippedAndSameProviderRetriesWithoutCachingPartialResult() {
    var broken = new AtomicReference<>("{");
    var reads = new AtomicInteger();
    var provider = new JarMenuModelProvider(spec -> {
      reads.incrementAndGet();
      return List.of(new MetadataResource("good.menu.json", "{\"fullName\":\"good\"}"),
          new MetadataResource("broken.menu.json", broken.get()));
    });

    assertEquals(List.of("good"), provider.getAllMenus().keySet().stream().toList());
    broken.set("{\"fullName\":\"recovered\"}");
    assertEquals(2, provider.getAllMenus().size());
    var successfulReads = reads.get();
    provider.getAllMenus();
    assertEquals(successfulReads, reads.get());
  }

  @Test
  void sourceFailureDoesNotBlockConstructionAndSameProviderCanRecover() {
    var broken = new java.util.concurrent.atomic.AtomicBoolean(true);
    var provider = new JarMenuModelProvider(spec -> {
      if (broken.get()) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("good.menu.json", "{\"fullName\":\"good\"}"));
    });
    assertTrue(provider.getAllMenus().isEmpty());
    broken.set(false);
    assertEquals(1, provider.getAllMenus().size());
  }

  @Test
  void recreatedProviderKeepsDefinitionsAfterResourceRemovalAndCallerMutation() throws Exception {
    var file = Path.of(getClass().getResource("/META-INF/ouroboros/metadata/snapshot.menu.json").toURI());
    var content = Files.readString(file);
    var first = new JarMenuModelProvider().getAllMenus().get("snapshot.test");
    ((com.ouroboros.menu.BaseMenuModel) first).setTitle("changed");
    try {
      Files.delete(file);

      var second = new JarMenuModelProvider().getAllMenus().get("snapshot.test");

      assertEquals("Snapshot Menu", second.getTitle());
    } finally {
      Files.writeString(file, content);
    }
  }
}
