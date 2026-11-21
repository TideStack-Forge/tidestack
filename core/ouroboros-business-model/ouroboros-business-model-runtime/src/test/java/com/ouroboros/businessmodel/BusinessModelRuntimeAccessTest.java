package com.ouroboros.businessmodel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import com.ouroboros.businessmodel.action.BusinessActionInput;
import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.businessmodel.event.BusinessEventEmissionContext;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.event.BusinessEventPort;
import com.ouroboros.businessmodel.event.BusinessEventSubject;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.exception.BusinessException;

class BusinessModelRuntimeAccessTest {
  @Test
  void modelAddsItsIdentityAndPassesTheOriginalInputToTheConfiguredRuntime() {
    var model = model();
    var runtime = mock(BusinessActionRuntime.class);
    var input = BusinessActionInput.paramsOnly(Map.of("id", "O-1"));
    when(runtime.invoke("sales.Order", "submit", input)).thenReturn("submitted");
    try (var context = context(runtime, mock(BusinessEventPort.class))) {
      assertEquals("submitted", model.invokeAction("submit", input));
      verify(runtime).invoke("sales.Order", "submit", input);
    }
    assertEquals("sales.Order", model.getFullName());
    assertThrows(BusinessException.class, () -> model.invokeAction("submit", input));
  }

  @Test
  void eventOverloadsKeepExplicitSubjectAndProducerContext() {
    var model = model();
    var events = mock(BusinessEventPort.class);
    var payload = Map.of("version", 1);
    var subject = BusinessEventSubject.instance("sales.Order", "O-1");
    var producer = BusinessEventEmissionContext.fromProducer("importCompletion");
    try (var context = context(mock(BusinessActionRuntime.class), events)) {
      model.emitEvent("reconciled", payload);
      verify(events).emit(new BusinessEventIdentity("sales.Order", "reconciled"),
          BusinessEventSubject.model("sales.Order"), payload, BusinessEventEmissionContext.fromAction());
      model.emitEvent("submitted", subject, payload);
      verify(events).emit(new BusinessEventIdentity("sales.Order", "submitted"),
          subject, payload, BusinessEventEmissionContext.fromAction());
      model.emitEvent("imported", subject, payload, producer);
      verify(events).emit(new BusinessEventIdentity("sales.Order", "imported"), subject, payload, producer);
    }
  }

  @Test
  void sameModelResolvesNewRuntimeAfterContextRestartAndPropagatesExecutionFailures() {
    var model = model();
    var input = BusinessActionInput.empty();
    var first = mock(BusinessActionRuntime.class);
    when(first.invoke("sales.Order", "refresh", input)).thenReturn("first");
    try (var context = context(first, mock(BusinessEventPort.class))) {
      assertEquals("first", model.invokeAction("refresh", input));
    }
    var second = mock(BusinessActionRuntime.class);
    var failure = new BusinessException("Action disabled");
    when(second.invoke("sales.Order", "refresh", input)).thenThrow(failure);
    try (var context = context(second, mock(BusinessEventPort.class))) {
      assertSame(failure, assertThrows(BusinessException.class, () -> model.invokeAction("refresh", input)));
    }
    verify(first, times(1)).invoke("sales.Order", "refresh", input);
  }

  @Test
  void offlineDefinitionIsUsableButExecutionRequiresRegisteredBeans() {
    var model = model();
    assertEquals("sales.Order", model.getFullName());
    assertThrows(BusinessException.class, () -> model.emitEvent("changed", Map.of()));
    try (var context = new GenericApplicationContext()) {
      context.registerBean(SpringContextUtil.class);
      context.refresh();
      assertThrows(BusinessException.class, () -> model.invokeAction("submit", BusinessActionInput.empty()));
      assertThrows(BusinessException.class, () -> model.emitEvent("changed", Map.of()));
    }
  }

  private DefaultBusinessModel model() {
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("sales");
    meta.setName("Order");
    return new DefaultBusinessModel(meta, mock(BusinessModelProvider.class));
  }

  private GenericApplicationContext context(BusinessActionRuntime actions, BusinessEventPort events) {
    var context = new GenericApplicationContext();
    context.registerBean(SpringContextUtil.class);
    context.registerBean(BusinessActionRuntime.class, () -> actions);
    context.registerBean(BusinessEventPort.class, () -> events);
    context.refresh();
    return context;
  }
}
