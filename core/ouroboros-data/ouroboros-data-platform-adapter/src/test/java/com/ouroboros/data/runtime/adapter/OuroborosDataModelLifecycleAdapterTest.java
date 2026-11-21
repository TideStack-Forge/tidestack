package com.ouroboros.data.runtime.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;
import com.ouroboros.lifecycle.event.OuroborosEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshingEvent;

class OuroborosDataModelLifecycleAdapterTest {
  @Test
  void refreshingEventDelegatesToLifecycleBridge() {
    var calls = new AtomicInteger();
    var adapter = new OuroborosDataModelLifecycleAdapter(calls::incrementAndGet);

    adapter.onOuroborosModelRefreshing(null);

    assertEquals(1, calls.get());
  }

  @Test
  void dataInitializationRunsAfterDataModelMigrationPhase() throws Exception {
    List<OuroborosEvent> events = new ArrayList<>();
    OuroborosContextRefreshLifecycle.publish(this, events::add);

    int modelRefreshingIndex = indexOf(events, OuroborosModelRefreshingEvent.class);
    int modelRefreshedIndex = indexOf(events, OuroborosModelRefreshedEvent.class);
    assertTrue(modelRefreshingIndex >= 0);
    assertTrue(modelRefreshedIndex >= 0);
    assertTrue(modelRefreshingIndex < modelRefreshedIndex);
  }

  private int indexOf(List<OuroborosEvent> events, Class<? extends OuroborosEvent> eventType) {
    for (int i = 0; i < events.size(); i++) {
      if (eventType.isInstance(events.get(i))) {
        return i;
      }
    }
    return -1;
  }
}
