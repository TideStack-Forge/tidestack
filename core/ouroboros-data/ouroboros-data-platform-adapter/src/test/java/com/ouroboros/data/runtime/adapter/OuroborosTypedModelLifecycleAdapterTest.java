package com.ouroboros.data.runtime.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.model.DeferredModelRequestRegistry;
import com.ouroboros.data.typed.runtime.ModelRefreshedListener;

class OuroborosTypedModelLifecycleAdapterTest {
  @Test
  void refreshedEventDelegatesToTypedModelListener() {
    var calls = new AtomicInteger();
    var listener = new ModelRefreshedListener(new DeferredModelRequestRegistry()) {
      @Override
      public void onModelRefreshed() {
        calls.incrementAndGet();
      }
    };
    var adapter = new OuroborosTypedModelLifecycleAdapter(listener);

    adapter.onOuroborosModelRefreshed(null);

    assertEquals(1, calls.get());
  }
}
