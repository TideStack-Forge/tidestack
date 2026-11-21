package com.ouroboros.configuration.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.ouroboros.metadata.resource.MetadataResource;

class JarConfigurationMetadataProviderTest {

  @Test
  void badConfigurationFileDoesNotHideHealthyDefinitions() {
    var content = new java.util.concurrent.atomic.AtomicReference<>("{");
    var provider = new JarConfigurationMetadataProvider(() -> List.of(
        new MetadataResource("good.json", "[{\"key\":\"good\"}]"),
        new MetadataResource("bad.json", content.get())));
    assertEquals("good", provider.getAllConfigurationMetadata().getFirst().getKey());
    content.set("[{\"key\":\"recovered\"}]");
    assertEquals(2, provider.getAllConfigurationMetadata().size());
  }

  @Test
  void sourceFailuresDoNotBlockConfigurationProvidersAndCanRetry() {
    var broken = new java.util.concurrent.atomic.AtomicBoolean(true);
    java.util.function.Supplier<List<MetadataResource>> source = () -> {
      if (broken.get()) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("good.json", "[]"));
    };
    var provider = new JarConfigurationMetadataProvider(source);
    var groupProvider = new JarConfigurationGroupMetadataProvider(source);
    assertTrue(provider.getAllConfigurationMetadata().isEmpty());
    assertTrue(groupProvider.getAllConfigurationGroupMetadata().isEmpty());
    broken.set(false);
    assertTrue(provider.getAllConfigurationMetadata().isEmpty());
    assertTrue(groupProvider.getAllConfigurationGroupMetadata().isEmpty());
  }

  private ListAppender<ILoggingEvent> logAppender;

  @AfterEach
  void tearDownAppender() {
    if (logAppender != null) {
      ((Logger) LoggerFactory.getLogger(JarConfigurationMetadataProvider.class)).detachAppender(logAppender);
      ((Logger) LoggerFactory.getLogger(JarConfigurationGroupMetadataProvider.class)).detachAppender(logAppender);
      logAppender.stop();
      logAppender = null;
    }
  }

  @Test
  void metadataProvider_logsInvalidResourcePathAndSkipsFailedResource() {
    logAppender = attachLogAppender(JarConfigurationMetadataProvider.class);
    JarConfigurationMetadataProvider provider = new JarConfigurationMetadataProvider();

    assertTrue(provider.parseMetadataResource("test-resource/configurations.json", "{").isEmpty());
    ILoggingEvent warning = assertSingleWarningContaining("test-resource/configurations.json");
    assertTrue(warning.getFormattedMessage().contains("解析配置项元数据资源"));
    assertTrue(warning.getFormattedMessage().contains("失败"));
    assertNotNull(warning.getThrowableProxy());
    assertTrue(!warning.getThrowableProxy().getMessage().trim().isEmpty());
  }

  @Test
  void groupProvider_logsInvalidResourcePathAndSkipsFailedResource() {
    logAppender = attachLogAppender(JarConfigurationGroupMetadataProvider.class);
    JarConfigurationGroupMetadataProvider provider = new JarConfigurationGroupMetadataProvider();

    assertTrue(provider.parseGroupMetadataResource("test-resource/configuration-groups.json", "{").isEmpty());
    ILoggingEvent warning = assertSingleWarningContaining("test-resource/configuration-groups.json");
    assertTrue(warning.getFormattedMessage().contains("解析配置分组元数据资源"));
    assertTrue(warning.getFormattedMessage().contains("失败"));
    assertNotNull(warning.getThrowableProxy());
    assertTrue(!warning.getThrowableProxy().getMessage().trim().isEmpty());
  }

  @Test
  void metadataSnapshotSurvivesReadFailureAndCallerMutation() {
    var reads = new AtomicInteger();
    var provider = new JarConfigurationMetadataProvider(() -> {
      if (reads.incrementAndGet() > 1) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("configurations.json", "[{\"key\":\"test\",\"label\":\"original\"}]"));
    });
    var first = provider.getAllConfigurationMetadata();
    first.getFirst().setLabel("changed");
    first.clear();

    assertEquals("original", provider.getAllConfigurationMetadata().getFirst().getLabel());
    assertEquals(1, reads.get());
  }

  @Test
  void groupSnapshotSurvivesReadFailureAndCallerMutation() {
    var reads = new AtomicInteger();
    var provider = new JarConfigurationGroupMetadataProvider(() -> {
      if (reads.incrementAndGet() > 1) {
        throw new IllegalStateException("jar unavailable");
      }
      return List.of(new MetadataResource("groups.json", "[{\"path\":\"test\",\"title\":\"original\"}]"));
    });
    var first = provider.getAllConfigurationGroupMetadata();
    first.getFirst().setTitle("changed");
    first.clear();

    assertEquals("original", provider.getAllConfigurationGroupMetadata().getFirst().getTitle());
    assertEquals(1, reads.get());
  }

  @Test
  void invalidSnapshotCanBeReplacedOnRetry() {
    var reads = new AtomicInteger();
    var provider = new JarConfigurationMetadataProvider(() -> List.of(new MetadataResource("configurations.json",
        reads.incrementAndGet() == 1 ? "{" : "[{\"key\":\"recovered\"}]")));

    assertTrue(provider.getAllConfigurationMetadata().isEmpty());
    assertEquals("recovered", provider.getAllConfigurationMetadata().getFirst().getKey());
    assertEquals(2, reads.get());
  }

  @Test
  void nullMetadataDoesNotBecomeASuccessfulSnapshot() {
    for (var content : List.of("null", "[null]")) {
      assertTrue(JarConfigurationMetadataProvider.parseMetadataResource("null.json", content).isEmpty());
      assertTrue(JarConfigurationGroupMetadataProvider.parseGroupMetadataResource("null.json", content).isEmpty());
    }
  }

  private ListAppender<ILoggingEvent> attachLogAppender(Class<?> loggerClass) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(loggerClass)).addAppender(appender);
    return appender;
  }

  private ILoggingEvent assertSingleWarningContaining(String text) {
    List<ILoggingEvent> warnings = logAppender.list.stream()
        .filter(event -> event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.WARN))
        .filter(event -> event.getFormattedMessage().contains(text))
        .toList();
    assertEquals(1, warnings.size());
    return warnings.get(0);
  }
}
