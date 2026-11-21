package com.ouroboros.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.lifecycle.event.OuroborosBootstrapFailedEvent;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;

class AppRuntimeAutoConfigurationLifecycleFailureTest {

  @Test
  void refreshPublishesFailedEventWhenLifecycleListenerFails() {
    AppRuntimeAutoConfiguration configuration = new AppRuntimeAutoConfiguration();
    RuntimeException failure = new IllegalStateException("模型刷新失败");
    List<Object> publishedEvents = new ArrayList<>();
    ApplicationEventPublisher publisher = event -> {
      publishedEvents.add(event);
      if (event instanceof OuroborosMetadataRefreshingEvent) {
        throw failure;
      }
    };
    ReflectionTestUtils.setField(configuration, "publisher", publisher);

    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> configuration.onOuroborosContextRefreshEvent(new EventObject(this))
    );

    assertSame(failure, thrown);
    OuroborosBootstrapFailedEvent failedEvent = publishedEvents.stream()
        .filter(OuroborosBootstrapFailedEvent.class::isInstance)
        .map(OuroborosBootstrapFailedEvent.class::cast)
        .findFirst()
        .orElseThrow(AssertionError::new);
    assertEquals("metadata-refreshing", failedEvent.getPhase());
    assertEquals(null, failedEvent.getModule());
    assertSame(failure, failedEvent.getThrowable());
    assertEquals(true, failedEvent.isFallback());
  }
}
