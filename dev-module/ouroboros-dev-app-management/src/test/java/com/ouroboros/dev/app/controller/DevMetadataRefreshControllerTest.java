package com.ouroboros.dev.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.ouroboros.lifecycle.event.OuroborosContextRefreshEvent;

class DevMetadataRefreshControllerTest {

  @Test
  void refreshPublishesCanonicalContextRefreshEvent() {
    AtomicReference<Object> published = new AtomicReference<>();
    DevMetadataRefreshController controller = new DevMetadataRefreshController(published::set);

    var result = controller.refresh("test-app");

    assertEquals(0, result.getCode());
    assertEquals(Boolean.TRUE, result.getData());
    assertTrue(published.get() instanceof OuroborosContextRefreshEvent);
    assertSame(controller, ((OuroborosContextRefreshEvent) published.get()).getSource());
  }
}
