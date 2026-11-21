package com.ouroboros.webapi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.servlet.function.ServerRequest;

import com.ouroboros.businessmodel.BusinessActionAnnotationCatalog;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.action.*;
import com.ouroboros.businessmodel.annotation.BusinessAction;
import com.ouroboros.businessmodel.annotation.BusinessActionData;
import com.ouroboros.businessmodel.behavior.*;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.logicflow.DefaultLogicFlowExecutionFacade;
import com.ouroboros.logicflow.model.*;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.util.Json;

/** Opt-in CPU/allocation measurement of the real input/Runtime/annotation/Flow paths, excluding storage and sockets. */
@EnabledIfSystemProperty(named = "ouroboros.behavior.performance", matches = "true")
class BusinessActionInputPerformanceTest {
  private static final String MODEL = "performance.Order";
  private static volatile Object consumed;

  @Test
  void measureEntryPathsWithSingleAndBatchPayloads() throws Exception {
    int warmup = Integer.getInteger("ouroboros.behavior.performance.warmup", 500);
    int samples = Integer.getInteger("ouroboros.behavior.performance.samples", 2000);
    assertTrue(warmup > 0 && samples > 0);
    var allocations = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    assertTrue(allocations.isThreadAllocatedMemorySupported());
    allocations.setThreadAllocatedMemoryEnabled(true);
    var rows = new ArrayList<String>();
    rows.add("path,items,sample,nanos,allocatedBytes,bodyParses");
    try (var beans = annotationContext()) {
      beans.registerBean(Receiver.class);
      beans.refresh();
      var catalog = registerBusinessActionAnnotationCatalog(beans);
      catalog.afterSingletonsInstantiated();
      var declared = BusinessActionAnnotationCatalog.getDefinitionContributions().stream()
          .flatMap(contribution -> contribution.getActions().stream()).toList();
      var handler = declared.stream().filter(action -> "handle".equals(action.getName())).findFirst().orElseThrow();
      var flowAction = com.ouroboros.businessmodel.behavior.BehaviorDefinitionCopies.copyAction(handler);
      flowAction.setName("flow");
      var binding = new ActionBinding();
      binding.setKind("logicflow"); binding.setFlowName("performanceFlow"); flowAction.setBinding(binding);
      var behavior = new BusinessModelBehavior(MODEL, List.of(handler, flowAction), List.of(), List.of());
      var provider = new BusinessModelBehaviorProvider() {
        public Optional<BusinessModelBehavior> getBusinessModelBehavior(String name) { return Optional.of(behavior); }
        public Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap() { return Map.of(MODEL, behavior); }
      };
      var runtime = new DefaultBusinessActionRuntime(provider, new DefaultLogicFlowExecutionFacade(), catalog::getHandlers,
          new Transactions(), ignored -> { });
      beans.getBeanFactory().registerSingleton("runtime", runtime);
      var bridge = new SpringContextUtil(); bridge.setApplicationContext(beans);
      var modelMeta = new DefaultBusinessModelMeta(); modelMeta.setNamespace("performance"); modelMeta.setName("Order"); modelMeta.setIsEnabled(true);
      var model = new DefaultBusinessModel(modelMeta, null);
      var node = FlowNodeMeta.builder().name("Echo").type("mapping").inputPort("action")
          .outputPort(FlowNodePortMeta.builder().name("").build()).extraProp("mapping", Map.of("items", "${data.items}")).build();
      var flow = FlowModelFactory.build(FlowModelMeta.builder().name("performanceFlow").entryNode(node).exitNode("Echo").build(),
          com.ouroboros.logicflow.nodes.NodeReferenceCatalog.of(List.of()));
      assertTrue(flow.isValid(), flow.getErrors().toString());
      var parses = new AtomicInteger();
      var converter = new MappingJackson2HttpMessageConverter(Json.mapperCopy()) {
        @Override public Object read(java.lang.reflect.Type type, Class<?> context,
                                     org.springframework.http.HttpInputMessage message) throws java.io.IOException {
          parses.incrementAndGet(); return super.read(type, context, message);
        }
      };
      var httpHandler = BusinessActionWebApiAdapter.buildHandler(route("handle"), runtime, provider);
      var httpFlow = BusinessActionWebApiAdapter.buildHandler(route("flow"), runtime, provider);
      try (var flows = mockStatic(FlowModelCenter.class)) {
        flows.when(() -> FlowModelCenter.getFlowModel("performanceFlow")).thenReturn(Optional.of(flow));
        CurrentSecurityContextHolder.withIdentity(identity(), () -> {
          for (int size : List.of(1, 128)) {
            var items = java.util.stream.IntStream.range(0, size).mapToObj(index -> Map.of("id", "order-" + index)).toList();
            var payload = Map.of("items", items);
            byte[] body = Json.toJsonString(payload).getBytes(StandardCharsets.UTF_8);
            var operations = new java.util.LinkedHashMap<String, Supplier<Object>>();
            operations.put("HTTP_Handler", () -> invokeHttp(httpHandler, request(body, converter)));
            operations.put("HTTP_Flow", () -> invokeHttp(httpFlow, request(body, converter)));
            operations.put("invoke_Flow", () -> runtime.invoke(MODEL, "flow", BusinessActionInput.dataOnly(payload)));
            operations.put("model_Handler", () -> model.invokeAction("handle", BusinessActionInput.dataOnly(payload)));
            operations.put("invoke_Handler", () -> runtime.invoke(MODEL, "handle", BusinessActionInput.dataOnly(payload)));
            operations.forEach((path, operation) -> {
              for (int index = 0; index < warmup; index++) consumed = operation.get();
              long[] durations = new long[samples]; long bytes = 0;
              for (int index = 0; index < samples; index++) {
                int beforeParses = parses.get(); long beforeBytes = allocations.getCurrentThreadAllocatedBytes(); long start = System.nanoTime();
                consumed = operation.get();
                long elapsed = System.nanoTime() - start; long allocated = allocations.getCurrentThreadAllocatedBytes() - beforeBytes;
                int count = parses.get() - beforeParses;
                assertEquals(path.startsWith("HTTP_") ? 1 : 0, count);
                rows.add(path + "," + size + "," + index + "," + elapsed + "," + allocated + "," + count);
                durations[index] = elapsed; bytes += allocated;
              }
              long total = Arrays.stream(durations).sum(); Arrays.sort(durations);
              System.out.printf(java.util.Locale.ROOT, "INPUT_PERF %s items=%d n=%d p50_us=%.3f p95_us=%.3f ops_s=%.1f bytes_op=%.1f%n",
                  path, size, samples, durations[samples / 2] / 1000.0, durations[(int) (samples * 0.95)] / 1000.0,
                  samples * 1_000_000_000.0 / total, bytes / (double) samples);
            });
          }
          var unused = request("{invalid".getBytes(StandardCharsets.UTF_8), converter);
          var noInput = new BusinessActionWebApiAdapter.HttpBusinessActionInput(unused, new BusinessActionHttpInputPlan(Map.of(), List.of()));
          int before = parses.get();
          assertSame(noInput, PreparedBusinessActionInput.prepare(noInput, Map.of()));
          assertFalse(noInput.hasParams()); assertFalse(noInput.hasData()); assertEquals(before, parses.get());
          return null;
        });
      } finally { bridge.destroy(); }
    } finally {
      try (var empty = annotationContext()) {
        empty.refresh(); registerBusinessActionAnnotationCatalog(empty).afterSingletonsInstantiated();
      }
    }
    Path output = Path.of(System.getProperty("ouroboros.behavior.performance.output"));
    Files.createDirectories(output.getParent()); Files.write(output, rows);
    System.out.printf("INPUT_PERF warmup=%d samples=%d java=%s os=%s arch=%s raw=%s%n", warmup, samples,
        System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.arch"), output);
  }

  private static Object invokeHttp(org.springframework.web.servlet.function.HandlerFunction<?> handler, ServerRequest request) {
    try { return handler.handle(request); } catch (Exception failure) { throw new IllegalStateException(failure); }
  }

  private static SimpleWebAPIModel route(String action) {
    return SimpleWebAPIModel.builder().post("/performance/" + action).handlerType(HandlerType.BUSINESS_ACTION)
        .extraProp("businessModel", MODEL).extraProp("action", action).extraProp("actionInputMapping", Map.of("data", "$.body")).build();
  }

  private static ServerRequest request(byte[] body, MappingJackson2HttpMessageConverter converter) {
    var servlet = new MockHttpServletRequest("POST", "/performance"); servlet.setContentType("application/json"); servlet.setContent(body);
    return ServerRequest.create(servlet, List.of(converter));
  }

  private static SecurityIdentity identity() {
    var identity = mock(SecurityIdentity.class, org.mockito.Mockito.CALLS_REAL_METHODS); when(identity.getPrincipal()).thenReturn("performance-user");
    var role = mock(Role.class); var grants = new ArrayList<OuroborosGrantedAuthority>();
    for (String action : List.of("handle", "flow")) {
      var grant = mock(OuroborosGrantedAuthority.class); when(grant.getAuthority()).thenReturn(MODEL + "." + action);
      when(grant.getAccessControlParams()).thenReturn(com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()); grants.add(grant);
    }
    doReturn(grants).when(role).getAuthorities(); when(identity.getRoles()).thenReturn(List.of(role)); return identity;
  }

  public static class Receiver {
    @BusinessAction(modelFullName = MODEL, name = "handle", handlerName = "performanceHandler", outputSchema = "{}",
        inputSchema = "{\"type\":\"object\",\"required\":[\"data\"],\"properties\":{\"data\":{\"type\":\"object\",\"required\":[\"items\"],\"properties\":{\"items\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}}}}}}")
    public Object handle(@BusinessActionData Map<String, Object> data) { return data; }
  }

  private static class Transactions extends AbstractPlatformTransactionManager {
    protected Object doGetTransaction() { return new Object(); }
    protected void doBegin(Object transaction, TransactionDefinition definition) { }
    protected void doCommit(DefaultTransactionStatus status) { }
    protected void doRollback(DefaultTransactionStatus status) { }
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
