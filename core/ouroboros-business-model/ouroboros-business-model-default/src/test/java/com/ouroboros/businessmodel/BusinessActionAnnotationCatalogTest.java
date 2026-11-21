package com.ouroboros.businessmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import com.ouroboros.businessmodel.action.BusinessActionContext;
import com.ouroboros.businessmodel.annotation.BusinessAction;
import com.ouroboros.businessmodel.annotation.BusinessActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;

class BusinessActionAnnotationCatalogTest {
  @Test
  void interfaceProxyRetainsImplementationParameterAnnotationsAndExecutesAdvice() {
    var proxy = new org.springframework.aop.framework.ProxyFactory(new ProxiedActions());
    proxy.setInterfaces(ProxiedActionService.class);
    var intercepted = new java.util.concurrent.atomic.AtomicInteger();
    proxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
      intercepted.incrementAndGet();
      return invocation.proceed();
    });
    try (GenericApplicationContext context = annotationContext()) {
      context.registerBean("actions", ProxiedActionService.class, () -> (ProxiedActionService) proxy.getProxy());
      context.refresh();
      var catalog = registerBusinessActionAnnotationCatalog(context);
      catalog.afterSingletonsInstantiated();
      var definition = BusinessActionAnnotationCatalog.getDefinitionContributions().get(0).getActions().get(0);
      var handler = catalog.getHandlers().get(0);
      handler.validateContract(definition);
      var input = com.ouroboros.businessmodel.action.PreparedBusinessActionInput.prepare(
          com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("id", "order-1")), definition.getInputSchema());
      assertEquals("order-1", handler.handle(null, input));
      assertEquals(1, intercepted.get());
      assertEquals(ProxiedActions.class.getName() + "#approve", handler.getImplementationRef());
    } finally {
      try (GenericApplicationContext empty = annotationContext()) {
        empty.refresh();
        registerBusinessActionAnnotationCatalog(empty).afterSingletonsInstantiated();
      }
    }
  }

  public interface ProxiedActionService {
    String approve(String id);
  }

  public static class ProxiedActions implements ProxiedActionService {
    @BusinessAction(modelFullName = "sales.Order", name = "approve", handlerName = "proxiedApproveHandler",
        outputSchema = "{\"type\":\"string\"}",
        inputSchema = "{\"type\":\"object\",\"properties\":{\"data\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}}}")
    public String approve(@com.ouroboros.businessmodel.annotation.BusinessActionData("id") String id) {
      return id;
    }
  }

  @Test
  void asynchronousAnnotatedActionMethodsCannotBeRegisteredAsSynchronousBindings() {
    for (Class<?> beanType : java.util.List.of(AsynchronousActions.class, FutureActions.class)) {
      try (GenericApplicationContext context = annotationContext()) {
        context.registerBean(beanType);
        context.refresh();
        var catalog = registerBusinessActionAnnotationCatalog(context);
        var error = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, catalog::afterSingletonsInstantiated);
        assertTrue(error.getMessage().contains("must complete synchronously"));
      }
    }
  }

  static class AsynchronousActions {
    @BusinessAction(modelFullName = "sales.Order", name = "async", handlerName = "async-handler")
    @org.springframework.scheduling.annotation.Async
    public Object execute(BusinessActionContext context, Map<String, Object> input) {
      throw new AssertionError("registration must not invoke the handler");
    }
  }

  static class FutureActions {
    @BusinessAction(modelFullName = "sales.Order", name = "future", handlerName = "future-handler")
    public java.util.concurrent.CompletableFuture<Object> execute(BusinessActionContext context, Map<String, Object> input) {
      throw new AssertionError("registration must not invoke the handler");
    }
  }

  @Test
  void shouldRegisterAnnotatedHandlersAndLogicFlowBindingsThroughOneCatalog() {
    try (GenericApplicationContext context = annotationContext()) {
      context.registerBean(AnnotatedActions.class);
      context.refresh();
      BusinessActionAnnotationCatalog catalog = registerBusinessActionAnnotationCatalog(context);

      catalog.afterSingletonsInstantiated();

      assertEquals(1, catalog.getHandlers().size());
      assertEquals("annotated-handler", catalog.getHandlers().get(0).getName());
      assertEquals("logicflow", BusinessActionAnnotationCatalog.getBindingContributions().get(0)
          .getBinding().getKind());
      assertEquals("sales.Order.submit", BusinessActionAnnotationCatalog.getBindingContributions().get(0)
          .getBinding().getFlowName());
      assertTrue(BusinessActionAnnotationCatalog.getDefinitionContributions().stream()
          .flatMap(contribution -> contribution.getActions().stream())
          .anyMatch(action -> "annotated".equals(action.getName())));

      var contributed = BusinessActionAnnotationCatalog.getDefinitionContributions().get(0).getActions().get(0);
      assertEquals(Map.of("expression", "${true}"), contributed.getPrecondition());
      assertEquals("submitted", contributed.getEmits().get(0).getEventName());
      var published = new com.ouroboros.businessmodel.behavior.BusinessModelBehavior("sales.Order",
          java.util.List.of(contributed), java.util.List.of(), java.util.List.of());
      published.preconditionPlan("annotated").requireSatisfied(Map.of(), Map.of());

      DefaultBusinessModelMeta meta = new DefaultBusinessModelMeta();
      meta.setNamespace("sales");
      meta.setName("Order");
      meta.setIsEnabled(true);
      ActionDefinition submit = new ActionDefinition();
      submit.setName("submit");

      submit.setIsEnabled(true);
      submit.setInputSchema(Map.of("type", "object"));
      submit.setOutputSchema(Map.of("type", "object"));
      meta.setActions(java.util.List.of(submit));
      var model = new DefaultBusinessModel(meta, new EmptyBusinessModelProvider());
      assertEquals("logicflow", model.getBehavior().getActions().get("submit").getBinding().getKind());
      assertEquals("sales.Order.submit", model.getBehavior().getActions().get("submit").getBinding().getFlowName());
    } finally {
      try (GenericApplicationContext emptyContext = annotationContext()) {
        emptyContext.refresh();
        registerBusinessActionAnnotationCatalog(emptyContext).afterSingletonsInstantiated();
      }
    }
  }

  private static final class EmptyBusinessModelProvider implements BusinessModelProvider {
    @Override
    public java.util.Optional<com.ouroboros.businessmodel.BusinessModel> getBusinessModel(String fullName) {
      return java.util.Optional.empty();
    }

    @Override
    public java.util.Map<String, com.ouroboros.businessmodel.BusinessModel> getBusinessModelMap() {
      return java.util.Map.of();
    }
  }

  static class AnnotatedActions {
    @BusinessAction(modelFullName = "sales.Order", name = "annotated", handlerName = "annotated-handler",
        precondition = "{\"expression\":\"${true}\"}",
        emits = "[{\"modelFullName\":\"sales.Order\",\"eventName\":\"submitted\"}]")
    public Map<String, Object> annotated(BusinessActionContext context, com.ouroboros.businessmodel.action.BusinessActionInput<?, ?> actionInput) {
      Map<String, Object> input = com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(actionInput.data()));
      return Map.of("ok", true);
    }

    @BusinessActionBinding(modelFullName = "sales.Order", actionName = "submit", flowName = "sales.Order.submit")
    public void submitFlowBinding() {
    }
  }

  private static com.ouroboros.businessmodel.BusinessActionAnnotationCatalog registerBusinessActionAnnotationCatalog(org.springframework.context.support.GenericApplicationContext context) {
    var catalog = new com.ouroboros.businessmodel.BusinessActionAnnotationCatalog(context.getBeanFactory());
    context.getBeanFactory().registerSingleton(BusinessActionAnnotationCatalog.class.getName(), catalog);
    return catalog;
  }
  private static org.springframework.context.support.GenericApplicationContext annotationContext() {
    var context = new org.springframework.context.support.GenericApplicationContext();
    context.registerBean(com.ouroboros.common.app.util.SpringContextUtil.class);
    return context;
  }
}
