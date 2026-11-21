package com.ouroboros.develop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.app.controller.ExceptionControllerAdvice;
import com.ouroboros.cache.MemoryCacheManager;
import com.ouroboros.logicflow.dev.controller.LogicFlowDebugController;
import com.ouroboros.logicflow.dev.service.*;
import com.ouroboros.logicflow.diagnostic.*;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

/** LF-DBG-HTTP-001: MVC binding, production error mapping, owner isolation and disposable history. */
class LogicFlowDebugHttpContractTest {
  private static final String BASE = "/api/dev/crm/logicflow/debug";
  private final AtomicReference<String> owner = new AtomicReference<>("alice");
  private final AtomicBoolean allowed = new AtomicBoolean(true);
  private final String region = "http-it-" + UUID.randomUUID();
  private final MemoryCacheManager cache = new MemoryCacheManager();
  private final LogicFlowDebugEventStore store = new CacheLogicFlowDebugEventStore(() -> cache, region,
      System::currentTimeMillis, CacheLogicFlowDebugEventStore.Limits.defaults(), false);
  private final FlowDebugTarget target = new FlowDebugTarget("crm", "alice", "runtime", 1, "helper", 100, "incarnation");
  private final LogicFlowDebugRuntime runtime = mock(LogicFlowDebugRuntime.class);
  private final MockMvc mvc;

  LogicFlowDebugHttpContractTest() {
    var beans = new StaticListableBeanFactory();
    beans.addBean("runtime", runtime);
    beans.addBean("access", new LogicFlowDebugAccess() {
      @Override public String currentOwner() { return owner.get(); }
      @Override public boolean canDebug(String app, String profile, String principal) {
        return allowed.get() && "crm".equals(app) && principal != null && (profile == null || profile.equals(principal));
      }
    });
    var captures = new LogicFlowDebugCaptureService(store, beans.getBeanProvider(LogicFlowDebugAccess.class),
        beans.getBeanProvider(LogicFlowDebugRuntime.class));
    mvc = MockMvcBuilders.standaloneSetup(new LogicFlowDebugController(store, captures))
        .setControllerAdvice(new ExceptionControllerAdvice(), new WebApiResultHttpStatusAdvice()).build();
    when(runtime.probe(eq("crm"), eq("alice"), eq("runtime"), anyString())).thenReturn(target);
    when(runtime.control(any(), any(), anyLong())).thenAnswer(call -> {
      LogicFlowDebugEventStore.Capture capture = call.getArgument(0);
      boolean accepting = call.getArgument(1) != LogicFlowDebugRuntime.Operation.STOP;
      return new FlowDebugCaptureStatus(capture.captureId(), target, accepting, 10000,
          capture.receivedThrough(), capture.receivedThrough() + 1, capture.receivedThrough(), 0, 0, 0, 0, List.of(), null);
    });
  }

  @Test
  void startSerializesPublicViewAndPagedHistoryWithoutControlOrStorageInternals() throws Exception {
    String id = start();
    store.putGraph("crm", id, new FlowDebugGraph("graph", "flow", "rev", true, "entry", "exit", null, List.of(), List.of()));
    store.append(event(id, 1, "flow.started")); store.append(event(id, 2, "flow.completed"));
    mvc.perform(get(BASE + "/captures/" + id)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.state").value("ACTIVE"))
        .andExpect(jsonPath("$.data.runs[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.target").doesNotExist())
        .andExpect(jsonPath("$.data.staged").doesNotExist());
    mvc.perform(get(BASE + "/captures/" + id + "/runs/run/events?afterSeq=0&limit=1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].seq").value(1))
        .andExpect(jsonPath("$.data.afterSeq").value(1)).andExpect(jsonPath("$.data.hasMore").value(true))
        .andExpect(jsonPath("$.data.complete").value(true));
    mvc.perform(get(BASE + "/captures/" + id + "/graphs/graph"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.exactRevision").value(true));
    mvc.perform(post(BASE + "/captures/" + id + "/stop"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("CLOSED"));
  }

  @Test
  void otherOwnerAndRevokedEditorAccessCannotReadEventsGraphsOrControlCapture() throws Exception {
    String id = start(); owner.set("bob");
    mvc.perform(get(BASE + "/captures")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    for (String path : List.of("", "/graphs/graph", "/runs/run/events")) {
      mvc.perform(get(BASE + "/captures/" + id + path)).andExpect(status().isNotFound())
          .andExpect(jsonPath("$.msg").value("调试记录不存在或已过期"));
    }
    mvc.perform(post(BASE + "/captures/" + id + "/renew")).andExpect(status().isNotFound());
    owner.set("alice"); allowed.set(false);
    mvc.perform(post(BASE + "/captures/" + id + "/stop")).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403));
  }

  @Test
  void invalidCursorsAndLostCacheProduceErrorsWithoutRecreatingCapture() throws Exception {
    mvc.perform(post(BASE + "/captures").contentType(MediaType.APPLICATION_JSON)
        .content("{\"runtimeId\":\"runtime\",\"flowName\":\"flow\",\"operationId\":\"invalid\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("调试操作编号无效"));
    String id = start();
    for (String query : List.of("afterSeq=-1", "limit=201", "limit=0", "afterSeq=oops")) {
      mvc.perform(get(BASE + "/captures/" + id + "/runs/run/events?" + query)).andExpect(status().isBadRequest());
    }
    cache.clear(region);
    mvc.perform(post(BASE + "/captures/" + id + "/renew")).andExpect(status().isNotFound());
    assertTrue(store.getCapture("crm", id).isEmpty());
  }

  @Test
  void legacySessionEndpointsReadTheSameAuthorizedHistoryAndRespectExactEventSequence() throws Exception {
    String id = start();
    store.append(event(id, 1, "flow.started"));
    store.append(event(id, 3, "node.failed"));
    mvc.perform(get(BASE + "/sessions?flowName=flow")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessions[0].sessionId").value("run"));
    mvc.perform(get(BASE + "/sessions?flowName=other")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessions").isEmpty());
    mvc.perform(get(BASE + "/sessions/run")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.flowName").value("flow"));
    mvc.perform(get(BASE + "/sessions/run/events?offset=1&limit=1")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isEmpty());
    mvc.perform(get(BASE + "/sessions/run/events?offset=2&limit=1")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].seq").value(3))
        .andExpect(jsonPath("$.data.items[0].message").value("节点 null 执行失败"));
    mvc.perform(get(BASE + "/sessions/run/events/1")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.seq").value(1));
    mvc.perform(get(BASE + "/sessions/run/events/2")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("调试事件不存在或已过期"));
    mvc.perform(get(BASE + "/sessions/run/events/0")).andExpect(status().isBadRequest());
    mvc.perform(get(BASE + "/sessions/run/events?limit=201")).andExpect(status().isBadRequest());
    owner.set("bob");
    mvc.perform(get(BASE + "/sessions")).andExpect(status().isOk()).andExpect(jsonPath("$.data.sessions").isEmpty());
    mvc.perform(get(BASE + "/sessions/run/events/1")).andExpect(status().isBadRequest());
  }

  @Test
  void mvcRoutesExportDebugRequestSuccessAndOwnerErrorContractsThroughCanonicalWebApiModel() throws Exception {
    var mapping = mvc.getDispatcherServlet().getWebApplicationContext()
        .getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
    var extractor = new com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor();
    var models = new java.util.ArrayList<com.ouroboros.webapi.WebAPIModel>();
    mapping.getHandlerMethods().forEach((info, handler) -> {
      String path = info.getPatternValues().iterator().next();
      String method = info.getMethodsCondition().getMethods().iterator().next().name();
      var model = extractor.extract(com.ouroboros.webapi.SimpleWebAPIModel.builder().api(method, path),
          info, handler.getBeanType(), handler.getMethod()).build();
      assertNotNull(model.getOperationId());
      assertTrue(model.getSecurity().stream().anyMatch(security -> security.getRequirements().containsKey("bearerAuth")));
      assertTrue(model.getResponses().keySet().containsAll(java.util.Set.of("200", "400", "401", "403", "404")));
      if (model.getOperationId().equals("startLogicFlowDebugCapture")) {
        var schema = model.getRequestBody().getContent().get("application/json").getSchema();
        assertTrue(schema.getRequired().containsAll(List.of("runtimeId", "flowName", "operationId")));
        assertEquals("uuid", schema.getProperties().get("operationId").getFormat());
        assertEquals(256, schema.getProperties().get("runtimeId").getMaxLength());
      }
      models.add(model);
    });
    var document = new com.ouroboros.webapi.export.OpenAPIDocumentBuilder().build(models,
        com.ouroboros.webapi.export.OpenAPIExportRequest.builder().title("LogicFlow debugging").version("test").build(), List.of());
    assertEquals("3.1.0", document.getOpenapi());
    assertEquals("startLogicFlowDebugCapture", document.getPaths().get("/api/dev/{appName}/logicflow/debug/captures").getPost().getOperationId());
    java.nio.file.Files.writeString(java.nio.file.Path.of("target", "logicflow-debug-openapi.json"), Json.tryToJsonString(document).get());
  }

  private String start() throws Exception {
    String id = UUID.randomUUID().toString();
    mvc.perform(post(BASE + "/captures").contentType(MediaType.APPLICATION_JSON)
        .content(Json.tryToJsonString(new LogicFlowDebugController.StartRequest("runtime", "flow", id)).get()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.captureId").value(id));
    return id;
  }

  private FlowDebugEvent event(String id, long sequence, String kind) {
    var event = new FlowDebugEvent(); event.setSchemaVersion("2.0"); event.setAppName("crm"); event.setTarget(target);
    event.setCaptureId(id); event.setCaptureSeq(sequence); event.setSeq(sequence); event.setRunId("run");
    event.setFlowInvocationId("run"); event.setGraphId("graph"); event.setFlowName("flow"); event.setScope("main");
    event.setKind(kind); event.setTimestamp(1000 + sequence);
    return event;
  }
}
