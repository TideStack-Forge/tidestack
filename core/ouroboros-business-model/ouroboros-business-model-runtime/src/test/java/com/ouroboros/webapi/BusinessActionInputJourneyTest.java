package com.ouroboros.webapi;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import com.ouroboros.businessmodel.BusinessActionAnnotationCatalog;
import com.ouroboros.businessmodel.action.*;
import com.ouroboros.businessmodel.annotation.BusinessAction;
import com.ouroboros.businessmodel.behavior.*;
import com.ouroboros.logicflow.DefaultLogicFlowExecutionFacade;
import com.ouroboros.logicflow.adapter.RequestInfoMap;
import com.ouroboros.logicflow.model.*;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessActionInputJourneyTest {
  private static final String MODEL = "sales.Order";

  @Test
  void legacyGeneratedHttpApiUsesDefaultMappingThroughTheRealActionAndScriptPath() throws Exception {
    var models = new com.ouroboros.businessmodel.BusinessModelProvider() {
      public Optional<com.ouroboros.businessmodel.BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
      public Map<String, com.ouroboros.businessmodel.BusinessModel> getBusinessModelMap() { return Map.of(); }
    };
    var meta = (com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta) com.ouroboros.businessmodel.BusinessModelMetaParser.parse("""
        {"modelType":"default","formatVersion":"1.0","namespace":"sales","name":"Order",
         "custom":[{"name":"approve","api":{"isEnabled":true,"isGenerateImplementation":true,
         "httpMethod":"POST","url":"approve","readOnly":true,"scriptLanguage":"groovy",
         "script":"if ($request.params.keySet().contains('x-trace')) { throw new IllegalStateException('headers must not be enumerated') }; return [comment: $request.data.comment, bodyComment: $request.body.comment, sameBody: $request.body.is($request.data), notify: $request.params.notify, trace: $request.headers?.get('x-trace') ?: [], paramTrace: $request.params.get('x-trace') ?: [], method: $request.method ?: '', path: $request.path ?: '', pathId: $request.pathVariables?.get('id') ?: '', requestPresent: $request.context.containsKey('request')]"}}]}
        """).get();
    var model = new com.ouroboros.businessmodel.DefaultBusinessModel(meta, models);
    assertTrue(model.getValidationResult().isValid(), model.getValidationResult().getErrors().toString());
    var bundle = model.buildModelBundle();
    var behavior = model.getBehavior();
    var provider = new BusinessModelBehaviorProvider() {
      public Optional<BusinessModelBehavior> getBusinessModelBehavior(String name) { return Optional.of(behavior); }
      public Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap() { return Map.of(MODEL, behavior); }
    };
    var transactions = mock(PlatformTransactionManager.class);
    when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    var runtime = new DefaultBusinessActionRuntime(provider, new DefaultLogicFlowExecutionFacade(), List::of, transactions, ignored -> { });
    var flow = FlowModelFactory.build(bundle.getModel(FlowModelMeta.class, MODEL + ".approve").orElseThrow(),
        com.ouroboros.logicflow.nodes.NodeReferenceCatalog.empty());
    assertTrue(flow.isValid(), flow.getErrors().toString());
    var route = bundle.getModelMap(WebAPIModel.class).values().stream().findFirst().orElseThrow();
    var handler = BusinessActionWebApiAdapter.buildHandler(route, runtime, provider);
    try (var flows = mockStatic(FlowModelCenter.class)) {
      flows.when(() -> FlowModelCenter.getFlowModel(MODEL + ".approve")).thenReturn(Optional.of(flow));
      assertEquals(Map.of("comment", "ok", "bodyComment", "ok", "sameBody", true, "notify", "true",
              "trace", List.of(), "paramTrace", List.of(), "method", "", "path", "", "pathId", "", "requestPresent", false),
          CurrentSecurityContextHolder.withIdentity(identity(), () -> runtime.invoke(MODEL, "approve",
              BusinessActionInput.of(Map.of("notify", "true"), Map.of("comment", "ok")))));
      var request = request("{\"comment\":\"ok\"}", "notify=true", servlet -> {
        servlet.addParameter("notify", "true");
        servlet.addHeader("x-trace", List.of("one", "two"));
      });
      request.attributes().put(org.springframework.web.servlet.function.RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "42"));
      var response = CurrentSecurityContextHolder.withIdentity(identity(), () -> assertDoesNotThrow(() -> handler.handle(request)));
      assertEquals(Map.of("comment", "ok", "bodyComment", "ok", "sameBody", true, "notify", "true",
              "trace", List.of("one", "two"), "paramTrace", List.of("one", "two"), "method", "POST", "path", "/orders/approve", "pathId", "42", "requestPresent", true),
          assertInstanceOf(WebApiResult.class, assertInstanceOf(EntityResponse.class, response).entity()).getData());
      verify(request, times(1)).body(Object.class);
    }
  }

  @Test
  void newScriptActionsUseTheSameBusinessInputForHttpAndInvokeWhileKeepingTheOptionalWholeRequest() throws Exception {
    var models = new com.ouroboros.businessmodel.BusinessModelProvider() {
      public Optional<com.ouroboros.businessmodel.BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
      public Map<String, com.ouroboros.businessmodel.BusinessModel> getBusinessModelMap() { return Map.of(); }
    };
    for (String language : List.of("groovy", "javascript")) {
      var original = (com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta) com.ouroboros.businessmodel.BusinessModelMetaParser.parse("""
          {"modelType":"default","formatVersion":"2.0","namespace":"sales","name":"Order",
           "custom":[{"name":"button","api":{"isEnabled":true,"httpMethod":"POST","url":"approve",
           "implementationMode":"ACTION","actionInputMapping":{"params.notify":"${ request.params.notify }","data":"${ request.body }"}}}]}
          """).get();
      String expression = "comment: $input.data.comment, bodyComment: $input.body.comment, notify: $input.params.notify, requestPresent: $input.context.request != null, "
          + "samePayload: $input.context.request == null || $input.context.request.body.comment == $input.data.comment";
      String script = "return " + ("groovy".equals(language) ? "[" + expression + "]" : "{" + expression + "}");
      var plan = new com.ouroboros.businessmodel.DefaultBusinessModelScriptAction().prepare(original, models, "button", "approve", language, script, true);
      var behavior = new com.ouroboros.businessmodel.DefaultBusinessModel(plan.model(), models).getBehavior();
      var provider = new BusinessModelBehaviorProvider() {
        public Optional<BusinessModelBehavior> getBusinessModelBehavior(String name) { return Optional.of(behavior); }
        public Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap() { return Map.of(MODEL, behavior); }
      };
      var transactions = mock(PlatformTransactionManager.class);
      when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
      var runtime = new DefaultBusinessActionRuntime(provider, new DefaultLogicFlowExecutionFacade(), List::of, transactions, ignored -> { });
      var flow = FlowModelFactory.build(plan.flow(), com.ouroboros.logicflow.nodes.NodeReferenceCatalog.of(List.of()));
      assertTrue(flow.isValid(), flow.getErrors().toString());
      var route = SimpleWebAPIModel.builder().post("/orders/approve").handlerType(HandlerType.BUSINESS_ACTION)
          .extraProp("businessModel", MODEL).extraProp("action", "approve").extraProp("formatVersion", "2.0")
          .extraProp("actionInputMapping", Map.of("params.notify", "${ request.params.notify }", "data", "${ request.body }")).build();
      var handler = BusinessActionWebApiAdapter.buildHandler(route, runtime, provider);
      try (var flows = mockStatic(FlowModelCenter.class)) {
        flows.when(() -> FlowModelCenter.getFlowModel(MODEL + ".approve")).thenReturn(Optional.of(flow));
        assertEquals(Map.of("comment", "ok", "bodyComment", "ok", "notify", "true", "requestPresent", false, "samePayload", true),
            CurrentSecurityContextHolder.withIdentity(identity(), () -> runtime.invoke(MODEL, "approve",
                BusinessActionInput.of(Map.of("notify", "true"), Map.of("comment", "ok")))));
        var request = request("{\"comment\":\"ok\"}", "notify=true");
        ((MockHttpServletRequest) request.servletRequest()).addParameter("notify", "true");
        var response = CurrentSecurityContextHolder.withIdentity(identity(), () -> assertDoesNotThrow(() -> handler.handle(request)));
        assertEquals(Map.of("comment", "ok", "bodyComment", "ok", "notify", "true", "requestPresent", true, "samePayload", true),
            assertInstanceOf(WebApiResult.class, assertInstanceOf(EntityResponse.class, response).entity()).getData());
        verify(request, times(1)).body(Object.class);
      }
    }
  }

  @Test
  void httpAndInvokeUseTheSameContractWithAnnotatedJavaAndRealLogicFlow() throws Exception {
    try (var beans = annotationContext()) {
      var receiver = new Receiver();
      beans.registerBean("receiver", Receiver.class, () -> receiver);
      beans.refresh();
      var catalog = registerBusinessActionAnnotationCatalog(beans);
      catalog.afterSingletonsInstantiated();
      var action = BusinessActionAnnotationCatalog.getDefinitionContributions().get(0).getActions().get(0);
      AtomicReference<BusinessModelBehavior> published = new AtomicReference<>(behavior(action));
      var provider = new BusinessModelBehaviorProvider() {
        public Optional<BusinessModelBehavior> getBusinessModelBehavior(String name) { return Optional.of(published.get()); }
        public Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap() { return Map.of(MODEL, published.get()); }
      };
      var transactions = mock(PlatformTransactionManager.class);
      when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
      var runtime = spy(new DefaultBusinessActionRuntime(provider, new DefaultLogicFlowExecutionFacade(),
          catalog::getHandlers, transactions, ignored -> { }));
      var route = SimpleWebAPIModel.builder().post("/orders/approve").handlerType(HandlerType.BUSINESS_ACTION)
          .extraProp("businessModel", MODEL).extraProp("action", "approve")
          .extraProp("actionInputMapping", Map.of("params.notify", "$.query.notify", "data", "$.body")).build();
      var compiled = new AtomicReference<org.springframework.web.servlet.function.HandlerFunction<ServerResponse>>();
      com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle.publish(this, event -> {
        if (event instanceof com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent) {
          assertThrows(com.ouroboros.exception.BusinessException.class,
              () -> runtime.inspectContractFingerprint(MODEL, "approve"));
          compiled.set(BusinessActionWebApiAdapter.buildHandler(route, runtime, provider));
        }
      });
      var http = compiled.get();
      var expected = Map.of("comment", "ok", "notify", true);
      var flowNode = FlowNodeMeta.builder().name("Approve").type("mapping").inputPort("action")
          .outputPort(FlowNodePortMeta.builder().name("").build())
          .extraProp("mapping", Map.of("comment", "${data.comment}", "notify", "${params.notify}")).build();
      var flow = FlowModelFactory.build(FlowModelMeta.builder().name("approveFlow").entryNode(flowNode).exitNode("Approve").build(), com.ouroboros.logicflow.nodes.NodeReferenceCatalog.of(List.of()));
      assertTrue(flow.isValid(), flow.getErrors().toString());
      try (var flows = mockStatic(FlowModelCenter.class)) {
        flows.when(() -> FlowModelCenter.getFlowModel("approveFlow")).thenReturn(Optional.of(flow));
        for (boolean useFlow : List.of(false, true)) {
          if (useFlow) {
            var binding = new ActionBinding();
            binding.setKind("logicflow");
            binding.setFlowName("approveFlow");
            action.setBinding(binding);
            published.set(behavior(action));
          }
          var input = BusinessActionInput.of(Map.of("notify", "true"), new Comment("ok"));
          assertEquals(expected, CurrentSecurityContextHolder.withIdentity(identity(),
              () -> runtime.invoke(MODEL, "approve", input)));
          var request = request("{\"comment\":\"ok\"}", "notify=true");
          var response = CurrentSecurityContextHolder.withIdentity(identity(),
              () -> assertDoesNotThrow(() -> http.handle(request)));
          assertEquals(expected, assertInstanceOf(WebApiResult.class,
              assertInstanceOf(EntityResponse.class, response).entity()).getData());
          var invocation = org.mockito.ArgumentCaptor.forClass(BusinessActionInvocation.class);
          verify(runtime, atLeastOnce()).invoke(invocation.capture());
          var httpInput = invocation.getAllValues().getLast().input();
          assertInstanceOf(BusinessActionWebApiAdapter.HttpBusinessActionInput.class, httpInput);
          if (!useFlow) {
            assertSame(httpInput, receiver.input);
            assertSame(RequestInfoMap.forRequest(request), receiver.context.request());
          }
          verify(request, times(1)).body(Object.class);
        }
      }
      int calls = receiver.calls;
      for (String body : List.of("{invalid", "{\"comment\":42}", "{\"comment\":\"ok\",\"unknown\":true}")) {
        var response = CurrentSecurityContextHolder.withIdentity(identity(),
            () -> assertDoesNotThrow(() -> http.handle(request(body, "notify=true"))));
        assertNotEquals(0, assertInstanceOf(WebApiResult.class, assertInstanceOf(EntityResponse.class, response).entity()).getCode());
      }
      assertEquals(calls, receiver.calls);
    } finally {
      try (var empty = annotationContext()) {
        empty.refresh();
        registerBusinessActionAnnotationCatalog(empty).afterSingletonsInstantiated();
      }
    }
  }

  @Test
  void currentRequestProviderReusesTheSameBodyAndDoesNotChooseBusinessInput() throws Exception {
    ObjectProvider<RequestMappingHandlerAdapter> adapters = mock();
    var provider = new WebAPIAutoConfiguration().businessActionRequestProvider(adapters);
    var request = request("[1,2]", "notify=false");
    var info = RequestInfoMap.forRequest(request);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request.servletRequest()));
    try {
      assertSame(info, provider.currentRequest());
      assertSame(info.get("body"), provider.currentRequest().get("data"));
      assertEquals(List.of(1, 2), info.get("body"));
      verify(request, times(1)).body(Object.class);
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
    assertNull(provider.currentRequest());
  }

  @Test
  void controllerCanReadContextBodyAfterMvcHasAlreadyConsumedTheRequestStream() throws Exception {
    ObjectProvider<RequestMappingHandlerAdapter> adapters = mock();
    var provider = new WebAPIAutoConfiguration().businessActionRequestProvider(adapters);
    var reads = new java.util.concurrent.atomic.AtomicInteger();
    var converter = new MappingJackson2HttpMessageConverter(Json.mapperCopy()) {
      @Override
      public Object read(java.lang.reflect.Type type, Class<?> context,
                         org.springframework.http.HttpInputMessage message) throws java.io.IOException {
        reads.incrementAndGet();
        return super.read(type, context, message);
      }
    };
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new MvcCaller(provider))
        .setControllerAdvice(new BusinessActionRequestBodyAdvice()).setMessageConverters(converter).build();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/caller")
        .contentType("application/json").content("{\"comment\":\"from-mvc\"}"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("{\"comment\":\"from-mvc\"}"));
    assertEquals(1, reads.get());
  }

  @org.springframework.web.bind.annotation.RestController
  static class MvcCaller {
    private final BusinessActionRequestProvider provider;
    MvcCaller(BusinessActionRequestProvider provider) { this.provider = provider; }
    @org.springframework.web.bind.annotation.PostMapping("/caller")
    public Object call(@org.springframework.web.bind.annotation.RequestBody Comment body) {
      var request = provider.currentRequest();
      assertEquals(Map.of("comment", body.comment()), request.get("body"));
      assertSame(request.get("body"), provider.currentRequest().get("data"));
      return request.get("body");
    }
  }

  @Test
  void routePlansRejectConflictingAndUndeclaredSources() {
    assertThrows(IllegalArgumentException.class, () -> BusinessActionWebApiContract.validateMapping(
        Map.of("data", "$.body", "data.comment", "$.query.comment"), "/orders"));
    assertThrows(IllegalArgumentException.class, () -> BusinessActionWebApiContract.validateMapping(
        Map.of("params.record.id", "$.pathVariables.missing"), "/orders/{id}"));
    assertThrows(IllegalArgumentException.class, () -> BusinessActionWebApiContract.validate(
        SimpleWebAPIModel.builder().get("/orders").handlerType(HandlerType.LOGIC_FLOW)
            .extraProp("businessActionName", "approve").build()));
  }

  private static BusinessModelBehavior behavior(ActionDefinition action) {
    return new BusinessModelBehavior(MODEL, List.of(action), List.of(), List.of());
  }

  private static ServerRequest request(String body, String query) {
    return request(body, query, ignored -> { });
  }

  private static ServerRequest request(String body, String query, java.util.function.Consumer<MockHttpServletRequest> configure) {
    var servlet = new MockHttpServletRequest("POST", "/orders/approve");
    servlet.setQueryString(query);
    servlet.setContentType("application/json");
    servlet.setContent(body.getBytes(StandardCharsets.UTF_8));
    servlet.addHeader("notify", "false");
    configure.accept(servlet);
    return spy(ServerRequest.create(servlet, List.of(new MappingJackson2HttpMessageConverter(Json.mapperCopy()))));
  }

  private static SecurityIdentity identity() {
    var identity = mock(SecurityIdentity.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    when(identity.getPrincipal()).thenReturn("approver");
    var role = mock(Role.class);
    var grant = mock(OuroborosGrantedAuthority.class);
    when(grant.getAuthority()).thenReturn(MODEL + ".approve");
    when(grant.getAccessControlParams()).thenReturn(com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all());
    doReturn(List.of(grant)).when(role).getAuthorities();
    when(identity.getRoles()).thenReturn(List.of(role));
    return identity;
  }

  public record Comment(String comment) { }

  public static class Receiver {
    int calls;
    BusinessActionInput<?, ?> input;
    BusinessActionContext context;

    @BusinessAction(modelFullName = MODEL, name = "approve", handlerName = "approveHandler", isEnabled = true,
        inputSchema = "{\"type\":\"object\",\"required\":[\"params\",\"data\"],\"additionalProperties\":false,\"properties\":{\"params\":{\"type\":\"object\",\"required\":[\"notify\"],\"properties\":{\"notify\":{\"type\":\"boolean\"}}},\"data\":{\"type\":\"object\",\"required\":[\"comment\"],\"additionalProperties\":false,\"properties\":{\"comment\":{\"type\":\"string\"}}}}}")
    public Map<String, Object> approve(BusinessActionContext context, BusinessActionInput<?, ?> input) {
      this.context = context;
      this.input = input;
      calls++;
      return Map.of("comment", ((Map<?, ?>) input.data()).get("comment"), "notify", ((Map<?, ?>) input.params()).get("notify"));
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
