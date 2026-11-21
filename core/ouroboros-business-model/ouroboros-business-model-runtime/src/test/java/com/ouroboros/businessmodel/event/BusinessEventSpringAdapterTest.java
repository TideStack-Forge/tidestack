package com.ouroboros.businessmodel.event;

import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.IDENTITY;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.MODEL;
import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.SCHEMA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.SimpleApplicationEventMulticaster;

import com.ouroboros.exception.BusinessException;

class BusinessEventSpringAdapterTest {
  @Test
  void synchronousSpringChannelInvokesTheSelectedListenerOnceAndPropagatesFailure() {
    var target = mock(BusinessEventListener.class);
    when(target.getName()).thenReturn("reserveListener"); when(target.getDeliveryMode()).thenCallRealMethod();
    var multicaster = new SimpleApplicationEventMulticaster();
    multicaster.addApplicationListener((ApplicationListener<BusinessEventSpringAdapter.DispatchSignal>) signal -> {
      signal.dispatch();
      signal.dispatch();
    });
    var adapter = new BusinessEventSpringAdapter(target, event -> multicaster.multicastEvent((ApplicationEvent) event));
    assertEquals("reserveListener", adapter.getName());
    var schema = Map.<String, Object>of("type", "object");
    when(target.getListenerConfigSchema()).thenReturn(schema);
    assertEquals(schema, adapter.getListenerConfigSchema());
    when(target.getListenerInputSchema()).thenReturn(schema);
    assertEquals(schema, adapter.getListenerInputSchema());
    var config = Map.<String, Object>of("mode", "configured");
    when(target.getListenerInputSchema(config)).thenReturn(Map.of("type", "object", "maxProperties", 0));
    assertEquals(0, adapter.getListenerInputSchema(config).get("maxProperties"));
    when(target.getDeliveryMode()).thenReturn(BusinessEventDeliveryMode.INDEPENDENT);
    assertEquals(BusinessEventDeliveryMode.INDEPENDENT, adapter.getDeliveryMode());
    var fact = fact();
    var delivery = delivery();
    adapter.onEvent(fact, delivery);
    verify(target).onEvent(fact, delivery);
    org.mockito.Mockito.doThrow(new IllegalStateException("listener failed")).when(target).onEvent(any(), any());
    assertThrows(IllegalStateException.class, () -> adapter.onEvent(fact, delivery));
  }

  @Test
  void asynchronousSpringBroadcastCannotInvokeOrConfirmTheTargetLater() throws Exception {
    var target = mock(BusinessEventListener.class);
    var queued = new ArrayList<Runnable>();
    var multicaster = new SimpleApplicationEventMulticaster();
    multicaster.setTaskExecutor(queued::add);
    multicaster.addApplicationListener((ApplicationListener<BusinessEventSpringAdapter.DispatchSignal>) signal -> signal.dispatch());
    var adapter = new BusinessEventSpringAdapter(target, event -> multicaster.multicastEvent((ApplicationEvent) event));
    assertThrows(BusinessException.class, () -> adapter.onEvent(fact(), delivery()));
    assertEquals(1, queued.size());
    assertThrows(BusinessException.class, queued.get(0)::run);
    try (var executor = Executors.newSingleThreadExecutor()) {
      executor.submit(() -> assertThrows(BusinessException.class, queued.get(0)::run)).get();
    }
    verify(target, never()).onEvent(any(), any());
  }

  private BusinessEventOccurrence<?> fact() {
    return BusinessEventOccurrence.create("event-1", IDENTITY, "revision-1", BusinessEventSubject.model(MODEL),
        SCHEMA, Map.of("orderId", "order-1"), Instant.now(), "scheduler", "actor", null, null, null);
  }

  private BusinessEventDeliveryContext delivery() {
    return new BusinessEventDeliveryContext("delivery-1", "inventory.Stock", "reserve", "stock-service", 1, null, null);
  }
}
