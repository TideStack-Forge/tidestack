package com.ouroboros.businessmodel.event;

import static com.ouroboros.businessmodel.event.BusinessEventTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.tools.ToolProvider;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.BusinessEventAnnotationCatalog;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.action.BusinessActionContext;
import com.ouroboros.businessmodel.action.BusinessActionDescriptionController;
import com.ouroboros.businessmodel.action.BusinessActionHandler;
import com.ouroboros.businessmodel.action.BusinessActionInvocation;
import com.ouroboros.businessmodel.action.DefaultBusinessActionRuntime;
import com.ouroboros.businessmodel.action.audit.DataModelBusinessActionExecutionAuditRecorder;
import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.EventReference;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.codegen.BusinessBehaviorTypeGenerator;
import com.ouroboros.businessmodel.event.store.BusinessEventDeliveryProperties;
import com.ouroboros.businessmodel.event.store.BusinessEventOutbox;
import com.ouroboros.businessmodel.event.store.DataModelBusinessEventOccurrenceRecorder;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.DefaultLogicFlowExecutionFacade;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelCenter;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.model.FlowNodePortMeta;
import com.ouroboros.logicflow.nodes.DataModelReferences;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeReferenceSet;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.util.Json;

/** BAE-001..007: real MVC, business projection, Action execution, JDBC transactions and Outbox recovery. */
class BusinessBehaviorJourneyIntegrationTest {
  private static final String STOCK = "inventory.Stock";
  @TempDir Path temporary;

  @Test
  @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "ouroboros.behavior.performance", matches = "true")
  void measureDefaultFactStorageWithBoundedChunksAndSubscriptionFanout() throws Exception {
    int warmup = Integer.getInteger("ouroboros.behavior.performance.warmup", 3);
    int samples = Integer.getInteger("ouroboros.behavior.performance.samples", 10);
    assertTrue(warmup > 0 && samples > 0);
    var allocations = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    assertTrue(allocations.isThreadAllocatedMemorySupported());
    allocations.setThreadAllocatedMemoryEnabled(true);
    var rows = new ArrayList<String>();
    var heapPools = java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream()
        .filter(pool -> pool.getType() == java.lang.management.MemoryType.HEAP).toList();
    rows.add("events,subscribers,items,sample,nanos,allocatedBytes,heapUsedBytes,heapPoolPeakUsedBytes,facts,deliveries");
    var sizes = java.util.Arrays.stream(System.getProperty("ouroboros.behavior.performance.sizes", "1,128").split(","))
        .map(Integer::parseInt).toList();
    assertTrue(sizes.stream().allMatch(size -> size > 0));
    for (int size : sizes) {
      for (int subscriberCount : List.of(-1, 0, 1, 8)) {
        var fixture = new Fixture(true);
        fixture.withModels(() -> {
          var published = fixture.behaviors.get(MODEL);
          if (subscriberCount == -1) {
            fixture.behaviors.put(MODEL, new BusinessModelBehavior(MODEL, published.getActionDefinitions(),
                published.getEventDefinitions(), published.getEventSubscriptionDefinitions(), List.of(), published.getModelRevision()));
          }
          var subscriptions = new ArrayList<EventSubscriptionDefinition>();
          for (int index = 0; index < subscriberCount; index++) {
            var subscription = fixture.subscription();
            subscription.setName("observer" + index);
            subscription.getSourceEvent().setEventName("recordsDeleted");
            subscription.getTarget().setModelFullName(null);
            subscription.getTarget().setKind("action"); subscription.getTarget().setActionName(null);
            subscription.getTarget().setInputMapping(Map.of());
            subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName("performanceObserver");
            subscriptions.add(subscription);
          }
          fixture.behaviors.put(STOCK, new BusinessModelBehavior(STOCK, List.of(), List.of(), subscriptions));
          var limits = new com.ouroboros.businessmodel.action.BusinessMutationEventProperties();
          limits.setMaxIds(32);
          fixture.actions.setMutationEvents(new com.ouroboros.businessmodel.action.BusinessActionMutationEventEmitter(() -> fixture.port, limits));
          long[] durations = new long[samples];
          long totalAllocated = 0;
          long peakHeap = 0;
          for (int sample = -warmup; sample < samples; sample++) {
            var records = new ArrayList<Map<String, Object>>();
            for (int index = 0; index < size; index++) records.add(Map.of("id", "sample-" + sample + "-" + index, "state", "ready"));
            fixture.orders().batchInsert(records).get();
            var input = com.ouroboros.businessmodel.action.BusinessActionInput.paramsOnly(Map.of("records",
                records.stream().map(record -> Map.of("id", record.get("id"))).toList()));
            long beforeFacts = fixture.facts().count(Map.of()).get();
            long beforeDeliveries = fixture.deliveries().count(Map.of()).get();
            heapPools.forEach(java.lang.management.MemoryPoolMXBean::resetPeakUsage);
            long beforeAllocation = allocations.getCurrentThreadAllocatedBytes();
            long start = System.nanoTime();
            withPermissions(List.of(MODEL + ".batchDelete", MODEL + ".search"),
                () -> fixture.actions.invoke(MODEL, "batchDelete", input));
            long elapsed = System.nanoTime() - start;
            long allocated = allocations.getCurrentThreadAllocatedBytes() - beforeAllocation;
            long heap = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long heapPoolPeak = heapPools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
            long facts = fixture.facts().count(Map.of()).get() - beforeFacts;
            long deliveries = fixture.deliveries().count(Map.of()).get() - beforeDeliveries;
            int chunks = subscriberCount == -1 ? 0 : (size + 31) / 32;
            assertEquals(chunks, facts);
            assertEquals(chunks * Math.max(subscriberCount, 0), deliveries);
            assertEquals(0, fixture.orders().count(Map.of()).get());
            if (sample >= 0) {
              rows.add((subscriberCount >= 0) + "," + Math.max(subscriberCount, 0) + "," + size + "," + sample
                  + "," + elapsed + "," + allocated + "," + heap + "," + heapPoolPeak + "," + facts + "," + deliveries);
              durations[sample] = elapsed;
              totalAllocated += allocated;
              peakHeap = Math.max(peakHeap, heapPoolPeak);
            }
          }
          long totalNanos = java.util.Arrays.stream(durations).sum();
          java.util.Arrays.sort(durations);
          System.out.printf(java.util.Locale.ROOT,
              "MUTATION_PERF events=%s subscribers=%d items=%d n=%d p50_ms=%.3f p95_ms=%.3f ops_s=%.1f bytes_op=%.1f heap_pool_peak_used_max=%d%n",
              subscriberCount >= 0, Math.max(subscriberCount, 0), size, samples, durations[samples / 2] / 1_000_000.0,
              durations[(int) (samples * 0.95)] / 1_000_000.0, samples * 1_000_000_000.0 / totalNanos,
              totalAllocated / (double) samples, peakHeap);
          return null;
        });
      }
    }
    Path output = Path.of(System.getProperty("ouroboros.behavior.performance.output"));
    Files.createDirectories(output.getParent());
    Files.write(output, rows);
    System.out.printf("MUTATION_PERF warmup=%d samples=%d mysql=%s java=%s raw=%s%n", warmup, samples,
        System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL") != null, System.getProperty("java.version"), output);
  }

  @Test
  void readOnlyActionsPersistTheirAuditButCannotWriteBusinessDataOrFacts() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      fixture.orders().insert(Map.of("id", "read-only", "state", "DRAFT")).get();
      var inspect = Fixture.action("inspect", "inspectHandler");
      inspect.setReadOnly(true);
      var forbidden = Fixture.action("forbidden", "submitHandler");
      forbidden.setReadOnly(true);
      fixture.behaviors.put(MODEL, new BusinessModelBehavior(MODEL, List.of(inspect, forbidden), List.of(), List.of(),
          List.of(), fixture.behaviors.get(MODEL).getModelRevision()));
      var input = com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("orderId", "read-only"));

      assertEquals(Map.of("orderId", "read-only"), withPermissions(List.of(MODEL + ".inspect"),
          () -> fixture.actions.invoke(MODEL, "inspect", input)));
      assertEquals("COMMITTED", fixture.audit().query().execute().get().getFirst().get("transactionResult"));
      assertThrows(BusinessException.class, () -> withPermissions(List.of(MODEL + ".forbidden"),
          () -> fixture.actions.invoke(MODEL, "forbidden", input)));
      assertEquals("DRAFT", fixture.orders().get("read-only").get().get("state"));
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertEquals(2, fixture.audit().count(Map.of()).get());
      assertEquals(1, fixture.audit().count(Map.of("transactionResult", "ROLLED_BACK")).get());
      return null;
    });
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "OUROBOROS_BEHAVIOR_MYSQL_URL", matches = ".+")
  void mysqlMutationObservationFollowsTheConfiguredMatchedOrChangedRowCount() {
    String url = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL");
    for (boolean changedRows : List.of(false, true)) {
      var fixture = new Fixture(false, false, url + (url.contains("?") ? "&" : "?") + "useAffectedRows=" + changedRows);
      fixture.withModels(() -> {
        var model = fixture.orders().withoutPlugins("UpdateWhenDataChange");
        model.insert(Map.of("id", "same-value", "state", "DRAFT")).get();
        var mutations = new ArrayList<com.ouroboros.data.model.DataModelMutation>();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(fixture.manager);
        Long affected = transaction.execute(status -> com.ouroboros.data.model.DataModelExecutionContext.withExecution(
            false, mutations::add, () -> model.update("same-value", Map.of("state", "DRAFT")).get()));
        assertEquals(changedRows ? 0L : 1L, affected.longValue());
        assertEquals(changedRows ? 0 : 1, mutations.size());
        assertEquals("DRAFT", model.get("same-value").get().get("state"));
        model.insert(Map.of("id", "different-value", "state", "APPROVED")).get();
        mutations.clear();
        Supplier<Long> mixedUpdate = () -> transaction.execute(status ->
            com.ouroboros.data.model.DataModelExecutionContext.withExecution(false, mutations::add,
                () -> model.update(Map.of(), Map.of("state", "DRAFT")).get()));
        if (changedRows) {
          assertThrows(com.ouroboros.data.exception.DataModelException.class, mixedUpdate::get);
          assertTrue(mutations.isEmpty());
          assertEquals("APPROVED", model.get("different-value").get().get("state"));
        } else {
          assertEquals(2L, mixedUpdate.get());
          assertEquals(2L, mutations.getFirst().affectedRows());
        }
        return null;
      });
    }
  }

  @Test
  void concurrentActionsRecheckLockedStateAndCommitOnlyOneApprovalFact() throws Exception {
    var fixture = new Fixture();
    fixture.withModels(() -> fixture.orders().insert(Map.of("id", "concurrent", "state", "DRAFT")).get());
    var firstEntered = new java.util.concurrent.CountDownLatch(1);
    var releaseFirst = new java.util.concurrent.CountDownLatch(1);
    var secondObservedDraft = new java.util.concurrent.CountDownLatch(1);
    fixture.approvalGate = () -> { firstEntered.countDown(); await(releaseFirst); };
    var input = com.ouroboros.businessmodel.action.BusinessActionInput.paramsOnly(Map.of("order", Map.of("id", "concurrent")));
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> fixture.withModels(() -> withPermissions(List.of(MODEL + ".approve", MODEL + ".search"),
          () -> fixture.actions.invoke(MODEL, "approve", input))));
      assertTrue(firstEntered.await(15, java.util.concurrent.TimeUnit.SECONDS));
      var second = pool.submit(() -> fixture.withModels(() -> withPermissions(List.of(MODEL + ".approve", MODEL + ".search"), () -> {
        assertEquals(com.ouroboros.businessmodel.action.BusinessActionAvailability.Status.EXECUTABLE,
            fixture.actions.checkAvailability(new BusinessActionInvocation(MODEL, "approve", input)).status());
        secondObservedDraft.countDown();
        return assertThrows(com.ouroboros.businessmodel.action.BusinessActionPreconditionException.class,
            () -> fixture.actions.invoke(MODEL, "approve", input));
      })));
      try { assertTrue(secondObservedDraft.await(15, java.util.concurrent.TimeUnit.SECONDS)); }
      finally { releaseFirst.countDown(); }
      assertEquals(Map.of("orderId", "concurrent"), first.get(15, java.util.concurrent.TimeUnit.SECONDS));
      assertNotNull(second.get(15, java.util.concurrent.TimeUnit.SECONDS));
    } finally { releaseFirst.countDown(); }
    fixture.withModels(() -> {
      assertEquals("APPROVED", fixture.orders().get("concurrent").get().get("state"));
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertEquals(1, fixture.deliveries().count(Map.of()).get());
      return null;
    });
  }

  private static void await(java.util.concurrent.CountDownLatch latch) {
    try {
      if (!latch.await(15, java.util.concurrent.TimeUnit.SECONDS)) { throw new IllegalStateException("Timed out waiting for transaction gate"); }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted transaction gate", error);
    }
  }

  @Test
  void projectedCrudWithSnowflakePrimaryKeyCommitsItsGeneratedEvent() {
    new com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig();
    var fixture = new Fixture(meta -> meta.getFields().getFirst().setType("雪花主键"));
    fixture.withModels(() -> {
      withPermissions(List.of(MODEL + ".add"), () -> fixture.actions.invoke(MODEL, "add",
          com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("state", "draft"))));
      String id = assertInstanceOf(String.class, fixture.orders().query().execute().get().getFirst().get("id"));
      assertEquals("draft-prepared", fixture.orders().get(id).get().get("state"));
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertEquals("recordsCreated", fixture.fact().get("eventName"));
      assertEquals(Map.of("operation", "INSERT", "objectIds", List.of(id), "affectedCount", 1),
          Json.toMap(fixture.fact().get("occurrenceJson").toString()).get("payload"));

      withPermissions(List.of(MODEL + ".update", MODEL + ".search"), () -> fixture.actions.invoke(MODEL, "update",
          com.ouroboros.businessmodel.action.BusinessActionInput.of(Map.of("record", Map.of("id", id)),
              Map.of("id", id, "state", "ready"))));
      assertEquals("ready-updated", fixture.orders().get(id).get().get("state"));
      withPermissions(List.of(MODEL + ".delete", MODEL + ".search"), () -> fixture.actions.invoke(MODEL, "delete",
          com.ouroboros.businessmodel.action.BusinessActionInput.paramsOnly(Map.of("record", Map.of("id", id)))));
      assertNull(fixture.orders().get(id).get());
      var facts = fixture.facts().query().execute().get();
      assertEquals(3, facts.size());
      var operations = Map.of("recordsCreated", "INSERT", "recordsUpdated", "UPDATE", "recordsDeleted", "DELETE");
      assertEquals(operations.keySet(), facts.stream().map(fact -> fact.get("eventName")).collect(java.util.stream.Collectors.toSet()));
      for (var fact : facts) {
        assertEquals(Map.of("operation", operations.get(fact.get("eventName")), "objectIds", List.of(id), "affectedCount", 1),
            Json.toMap(fact.get("occurrenceJson").toString()).get("payload"));
      }
      return null;
    });
  }

  @Test
  void defaultCrudFactsFollowActualWritesAndDeletedObjectsCanReachAnAuthorizedConsumer() {
    var fixture = new Fixture(true);
    fixture.withModels(() -> {
      var deleted = new ArrayList<BusinessEventOccurrence<?>>();
      fixture.listeners = List.of(new BusinessEventListener() {
        public String getName() { return "deletionListener"; }
        public void onEvent(BusinessEventOccurrence<?> fact, BusinessEventDeliveryContext delivery) { deleted.add(fact); }
      });
      var subscription = fixture.subscription();
      subscription.getSourceEvent().setEventName("recordsDeleted");
      subscription.getTarget().setModelFullName(null);
      subscription.getTarget().setKind("action"); subscription.getTarget().setActionName(null);
      subscription.getTarget().setInputMapping(Map.of());
      subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName("deletionListener");
      fixture.behaviors.put(STOCK, new BusinessModelBehavior(STOCK, List.of(fixture.reserveAction), List.of(), List.of(subscription)));
      withPermissions(List.of(MODEL + ".add"), () -> fixture.actions.invoke(MODEL, "add",
          com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("id", "gone", "state", "draft"))));
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertEquals("recordsCreated", fixture.fact().get("eventName"));
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      withPermissions(List.of(MODEL + ".delete", MODEL + ".search"), () -> fixture.actions.invoke(MODEL, "delete",
          com.ouroboros.businessmodel.action.BusinessActionInput.paramsOnly(Map.of("record", Map.of("id", "gone")))));
      assertNull(fixture.orders().get("gone").get());
      assertEquals(2, fixture.facts().count(Map.of()).get());
      assertEquals(1, fixture.deliveries().count(Map.of()).get());
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery().get("status"));
      assertEquals(1, deleted.size());
      assertEquals(Map.of("operation", "DELETE", "objectIds", List.of("gone"), "affectedCount", 1), deleted.get(0).payloadFields());
      assertTrue(deleted.get(0).requireUnfilteredModelRead());
      assertFalse(deleted.get(0).subject().isInstance());
      assertEquals(2, fixture.audit().count(Map.of()).get());
      assertTrue(fixture.audit().query().execute().get().stream().allMatch(row ->
          Json.tryToBean(List.class, String.valueOf(row.get("eventIdsJson"))).get().size() == 1));
      return null;
    });
  }

  @Test
  void defaultFactChunksCommitIndependentlyAndRedriveKeepsSuccessfulChunksConfirmed() {
    var fixture = new Fixture(true);
    fixture.withModels(() -> {
      var failMiddle = new AtomicBoolean(true);
      var attempts = new java.util.HashMap<String, Integer>();
      fixture.listeners = List.of(new BusinessEventListener() {
        public String getName() { return "chunkListener"; }
        public void onEvent(BusinessEventOccurrence<?> fact, BusinessEventDeliveryContext delivery) {
          var ids = (List<?>) fact.payloadFields().get("objectIds");
          assertEquals(1, ids.size());
          assertFalse(fact.payloadFields().containsKey("chunkIndex"));
          assertFalse(fact.payloadFields().containsKey("chunkCount"));
          String id = String.valueOf(ids.get(0));
          attempts.merge(id, 1, Integer::sum);
          fixture.stock().insert(Map.of("id", id, "state", "observed")).get();
          if ("middle".equals(id) && failMiddle.get()) { throw new IllegalStateException("temporary chunk failure"); }
        }
      });
      var subscription = fixture.subscription();
      subscription.getSourceEvent().setEventName("recordsDeleted");
      subscription.getTarget().setModelFullName(null);
      subscription.getTarget().setKind("action"); subscription.getTarget().setActionName(null);
      subscription.getTarget().setInputMapping(Map.of());
      subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName("chunkListener");
      fixture.behaviors.put(STOCK, new BusinessModelBehavior(STOCK, List.of(fixture.reserveAction), List.of(), List.of(subscription)));
      var limits = new com.ouroboros.businessmodel.action.BusinessMutationEventProperties();
      limits.setMaxIds(1);
      fixture.actions.setMutationEvents(new com.ouroboros.businessmodel.action.BusinessActionMutationEventEmitter(() -> fixture.port, limits));
      var ids = List.of("first", "middle", "last");
      ids.forEach(id -> fixture.orders().insert(Map.of("id", id, "state", "ready")).get());
      withPermissions(List.of(MODEL + ".batchDelete", MODEL + ".search"), () -> fixture.actions.invoke(MODEL, "batchDelete",
          com.ouroboros.businessmodel.action.BusinessActionInput.paramsOnly(Map.of("records",
              ids.stream().map(id -> Map.of("id", id)).toList()))));
      assertEquals(0, fixture.orders().count(Map.of()).get());
      assertEquals(3, fixture.facts().count(Map.of()).get());
      assertEquals(3, fixture.deliveries().count(Map.of()).get());
      fixture.worker().sweep();
      assertEquals(2, fixture.stock().count(Map.of()).get());
      assertNull(fixture.stock().get("middle").get());
      var failed = fixture.deliveries().query().where(Map.of("status", "DEAD")).execute().get();
      assertEquals(1, failed.size());
      String deliveryId = String.valueOf(failed.get(0).get("id"));
      Object eventId = failed.get(0).get("eventId");
      failMiddle.set(false);
      assertTrue(fixture.worker().redrive(deliveryId));
      fixture.worker().sweep();
      fixture.worker().sweep();
      assertEquals(3, fixture.stock().count(Map.of()).get());
      assertEquals(Map.of("first", 1, "middle", 2, "last", 1), attempts);
      assertEquals(3, fixture.deliveries().count(Map.of("status", "SUCCESS")).get());
      assertEquals(eventId, fixture.deliveries().get(deliveryId).get().get("eventId"));
      assertEquals(3, fixture.facts().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void logicalDeleteRemainsADeletionFactAndSkippedSameValueUpdatesProduceNoFact() {
    var fixture = new Fixture(true, true);
    fixture.withModels(() -> {
      fixture.orders().insert(Map.of("id", "logical", "state", "ready-updated")).get();
      var observed = new ArrayList<com.ouroboros.data.model.DataModelMutation>();
      Long matched = new org.springframework.transaction.support.TransactionTemplate(fixture.manager).execute(status ->
          com.ouroboros.data.model.DataModelExecutionContext.withExecution(false, observed::add, () ->
              fixture.orders().update("logical", Map.of("state", "ready-updated")).get()));
      assertEquals(0L, matched.longValue());
      assertTrue(observed.isEmpty());
      withPermissions(List.of(MODEL + ".update", MODEL + ".search"), () -> fixture.actions.invoke(MODEL, "update",
          com.ouroboros.businessmodel.action.BusinessActionInput.of(Map.of("record", Map.of("id", "logical")),
              Map.of("id", "logical", "state", "ready"))));
      assertEquals("ready-updated", fixture.orders().get("logical").get().get("state"));
      assertEquals(0, fixture.facts().count(Map.of()).get());
      withPermissions(List.of(MODEL + ".update", MODEL + ".search"), () -> fixture.actions.invoke(MODEL, "update",
          com.ouroboros.businessmodel.action.BusinessActionInput.of(Map.of("record", Map.of("id", "logical")),
              Map.of("id", "logical", "state", "changed"))));
      assertEquals("changed-updated", fixture.orders().get("logical").get().get("state"));
      assertEquals(1, fixture.facts().count(Map.of()).get(), fixture.audit().query().execute().get().toString());
      assertEquals("recordsUpdated", fixture.fact().get("eventName"));
      withPermissions(List.of(MODEL + ".delete", MODEL + ".search"), () -> fixture.actions.invoke(MODEL, "delete",
          com.ouroboros.businessmodel.action.BusinessActionInput.paramsOnly(Map.of("record", Map.of("id", "logical")))));
      assertNull(fixture.orders().get("logical").get());
      assertTrue(fixture.orders().getField("isDeleted").isPresent());
      var facts = fixture.facts().query().execute().get();
      assertEquals(2, facts.size());
      assertEquals(1, facts.stream().filter(fact -> "recordsDeleted".equals(fact.get("eventName"))).count());
      return null;
    });
  }

  @Test
  void visibleDeleteAndHistoryFeaturesKeepOnlyTheActualSourceMutationFacts() {
    for (boolean archive : List.of(false, true)) {
      var fixture = new Fixture(order -> snapshotFeatures(order, archive));
      fixture.withAuditedModels(() -> {
        var grants = List.of(MODEL + ".add", MODEL + ".update", MODEL + ".delete", MODEL + ".search", MODEL + ".searchDeleted");
        withPermissions(grants, () -> fixture.actions.invoke(MODEL, "add",
            com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("id", "snapshot", "state", "draft"))));
        assertNotNull(fixture.orders().get("snapshot").get().get("createdAt"));
        withPermissions(grants, () -> fixture.actions.invoke(MODEL, "update",
            com.ouroboros.businessmodel.action.BusinessActionInput.of(Map.of("record", Map.of("id", "snapshot")), Map.of("id", "snapshot", "state", "changed"))));
        withPermissions(grants, () -> fixture.actions.invoke(MODEL, "delete",
            com.ouroboros.businessmodel.action.BusinessActionInput.paramsOnly(Map.of("record", Map.of("id", "snapshot")))));
        var facts = fixture.facts().query().execute().get();
        assertEquals(3, facts.size());
        assertEquals(java.util.Set.of("recordsCreated", "recordsUpdated", "recordsDeleted"),
            facts.stream().map(fact -> fact.get("eventName")).collect(java.util.stream.Collectors.toSet()));
        for (var fact : facts) {
          var payload = (Map<?, ?>) Json.toMap(fact.get("occurrenceJson").toString()).get("payload");
          assertEquals(List.of("snapshot"), payload.get("objectIds")); assertEquals(1, ((Number) payload.get("affectedCount")).intValue());
        }
        assertEquals(3, fixture.models.get(MODEL + "History").count(Map.of()).get());
        if (archive) {
          assertEquals(1, fixture.models.get(MODEL + "Archive").count(Map.of()).get());
          assertNull(fixture.orders().get("snapshot").get());
        } else {
          assertEquals(true, fixture.orders().withoutPlugins().get("snapshot").get().get("isDeleted"));
        }
        return null;
      });
    }
  }

  @Test
  void archiveAndHistoryWriteFailuresRollBackTheSourceActionWithoutDefaultFacts() {
    var historyFixture = new Fixture(order -> snapshotFeatures(order, true));
    historyFixture.withAuditedModels(() -> {
      var history = spy(historyFixture.models.get(MODEL + "History"));
      var physical = spy(history.withoutPlugins());
      var historyFailure = new IllegalStateException("history write failure");
      doReturn(io.vavr.control.Try.failure(historyFailure)).when(physical).insert(anyMap());
      doReturn(physical).when(history).withoutPlugins(); historyFixture.models.put(MODEL + "History", history);
      var error = assertThrows(BusinessException.class, () -> withPermissions(List.of(MODEL + ".add"), () -> historyFixture.actions.invoke(MODEL, "add",
          com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("id", "failed-history", "state", "draft")))));
      assertSame(historyFailure, org.apache.commons.lang3.exception.ExceptionUtils.getRootCause(error));
      assertEquals(0, historyFixture.orders().count(Map.of()).get()); assertEquals(0, historyFixture.facts().count(Map.of()).get());
      assertEquals(0, history.count(Map.of()).get()); return null;
    });
    var archiveFixture = new Fixture(order -> snapshotFeatures(order, true));
    archiveFixture.withAuditedModels(() -> {
      new org.springframework.transaction.support.TransactionTemplate(archiveFixture.manager).executeWithoutResult(status ->
          archiveFixture.orders().insert(Map.of("id", "failed-archive", "state", "draft")).get());
      var archive = spy(archiveFixture.models.get(MODEL + "Archive"));
      var archiveFailure = new IllegalStateException("archive write failure");
      doReturn(io.vavr.control.Try.failure(archiveFailure)).when(archive).batchInsert(anyList());
      archiveFixture.models.put(MODEL + "Archive", archive);
      var error = assertThrows(BusinessException.class, () -> withPermissions(List.of(MODEL + ".delete", MODEL + ".search"), () -> archiveFixture.actions.invoke(MODEL, "delete",
          com.ouroboros.businessmodel.action.BusinessActionInput.paramsOnly(Map.of("record", Map.of("id", "failed-archive"))))));
      assertSame(archiveFailure, org.apache.commons.lang3.exception.ExceptionUtils.getRootCause(error));
      assertNotNull(archiveFixture.orders().get("failed-archive").get()); assertEquals(0, archiveFixture.facts().count(Map.of()).get());
      assertEquals(0, archive.count(Map.of()).get()); assertEquals(1, archiveFixture.models.get(MODEL + "History").count(Map.of()).get());
      return null;
    });
  }

  @Test
  void defaultFactFailureRollsBackArchiveHistoryAndTheSourceTogether() {
    var fixture = new Fixture(order -> snapshotFeatures(order, true));
    fixture.withAuditedModels(() -> {
      new org.springframework.transaction.support.TransactionTemplate(fixture.manager).executeWithoutResult(status ->
          fixture.orders().insert(Map.of("id", "failed-fact", "state", "draft")).get());
      var limits = new com.ouroboros.businessmodel.action.BusinessMutationEventProperties(); limits.setMaxPayloadBytes(1);
      fixture.actions.setMutationEvents(new com.ouroboros.businessmodel.action.BusinessActionMutationEventEmitter(() -> fixture.port, limits));
      var error = assertThrows(BusinessException.class, () -> withPermissions(List.of(MODEL + ".delete", MODEL + ".search"), () -> fixture.actions.invoke(MODEL, "delete",
          com.ouroboros.businessmodel.action.BusinessActionInput.paramsOnly(Map.of("record", Map.of("id", "failed-fact"))))));
      assertTrue(error.getMessage().contains("载荷上限"), error.getMessage());
      assertNotNull(fixture.orders().get("failed-fact").get()); assertEquals(0, fixture.facts().count(Map.of()).get());
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      assertEquals(0, fixture.models.get(MODEL + "Archive").count(Map.of()).get());
      assertEquals(1, fixture.models.get(MODEL + "History").count(Map.of()).get());
      return null;
    });
  }

  @Test
  void commitPreparationFailureRollsBackCompletedSourceSnapshotsAndDefaultFact() {
    var fixture = new Fixture(order -> snapshotFeatures(order, true));
    fixture.withAuditedModels(() -> {
      var transaction = new org.springframework.transaction.support.TransactionTemplate(fixture.manager);
      transaction.executeWithoutResult(status -> fixture.orders().insert(Map.of("id", "commit-failure", "state", "draft")).get());
      var commitFailure = new IllegalStateException("commit preparation failure");
      var error = assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
        withPermissions(List.of(MODEL + ".delete", MODEL + ".search"), () -> fixture.actions.invoke(MODEL, "delete",
            com.ouroboros.businessmodel.action.BusinessActionInput.paramsOnly(Map.of("record", Map.of("id", "commit-failure")))));
        assertNull(fixture.orders().get("commit-failure").get());
        assertEquals(1, fixture.facts().count(Map.of()).get());
        assertEquals(1, fixture.models.get(MODEL + "Archive").count(Map.of()).get());
        assertEquals(2, fixture.models.get(MODEL + "History").count(Map.of()).get());
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void beforeCommit(boolean readOnly) { throw commitFailure; }
            });
      }));
      assertSame(commitFailure, error);
      assertNotNull(fixture.orders().get("commit-failure").get());
      assertEquals("ROLLED_BACK", fixture.audit().query().execute().get().getFirst().get("transactionResult"));
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      assertEquals(0, fixture.models.get(MODEL + "Archive").count(Map.of()).get());
      assertEquals(1, fixture.models.get(MODEL + "History").count(Map.of()).get());
      return null;
    });
  }

  private static void snapshotFeatures(DefaultBusinessModelMeta order, boolean archive) {
    var features = new java.util.LinkedHashMap<String, Object>();
    order.getExtraProp("features").filter(Map.class::isInstance).map(Map.class::cast)
        .ifPresent(values -> values.forEach((key, value) -> features.put(String.valueOf(key), value)));
    features.put("isEnabledSoftDelete", true); features.put("isEnabledAuditFields", true); order.setExtraProp("features", features);
    order.setEnableArchiveDelete(archive); order.setEnableScd2History(true);
  }

  @Test
  void defaultFactFailureAndOutputFailureBothRollBackTheSourceWrite() {
    var fixture = new Fixture(true);
    fixture.withModels(() -> {
      var limits = new com.ouroboros.businessmodel.action.BusinessMutationEventProperties();
      limits.setMaxPayloadBytes(1);
      fixture.actions.setMutationEvents(new com.ouroboros.businessmodel.action.BusinessActionMutationEventEmitter(() -> fixture.port, limits));
      assertThrows(BusinessException.class, () -> withPermissions(List.of(MODEL + ".add"), () -> fixture.actions.invoke(MODEL, "add",
          com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("id", "failed", "state", "draft")))));
      assertEquals(0, fixture.orders().count(Map.of()).get());
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      fixture.actions.setMutationEvents(new com.ouroboros.businessmodel.action.BusinessActionMutationEventEmitter(
          () -> fixture.port, new com.ouroboros.businessmodel.action.BusinessMutationEventProperties()));
      var original = fixture.behaviors.get(MODEL);
      var definitions = new ArrayList<>(original.getActions().values());
      definitions.stream().filter(action -> "add".equals(action.getName())).findFirst().orElseThrow()
          .setOutputSchema(Map.of("type", "object", "required", List.of("requiredResult")));
      fixture.behaviors.put(MODEL, new BusinessModelBehavior(MODEL, definitions, new ArrayList<>(original.getEvents().values()),
          List.of(), List.of(), "invalid-output", Map.of("add", original.mutationEventPlan("add"))));
      assertThrows(BusinessException.class, () -> withPermissions(List.of(MODEL + ".add"), () -> fixture.actions.invoke(MODEL, "add",
          com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("id", "failed-output", "state", "draft")))));
      assertEquals(0, fixture.orders().count(Map.of()).get());
      assertEquals(0, fixture.facts().count(Map.of()).get());
      return null;
    });
  }

  @Test
  void projectedCrudKeepsRequestDataForBeforeAfterScriptsAndIsolatesTheCaller() {
    var fixture = new Fixture(true);
    fixture.withModels(() -> {
      var data = new java.util.LinkedHashMap<String, Object>(Map.of("id", "script-order", "state", "draft"));
      Object added = withPermissions(List.of(MODEL + ".add"), () -> fixture.actions.invoke(MODEL, "add",
          com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(data)));
      assertEquals(Map.of("id", "script-order", "state", "draft"), data);
      assertEquals("draft-prepared", fixture.orders().get("script-order").get().get("state"));
      assertEquals("script-order", ((Map<?, ?>) added).get("requestId"));
      var update = new java.util.LinkedHashMap<String, Object>(Map.of("id", "script-order", "state", "ready"));
      Object updated = withPermissions(List.of(MODEL + ".update", MODEL + ".search"), () -> fixture.actions.invoke(MODEL, "update",
          com.ouroboros.businessmodel.action.BusinessActionInput.of(Map.of("record", Map.of("id", "script-order")), update)));
      assertEquals("ready-updated", fixture.orders().get("script-order").get().get("state"));
      assertEquals("script-order", ((Map<?, ?>) updated).get("requestId"));
      assertEquals("ready", update.get("state"));
      assertThrows(BusinessException.class, () -> withPermissions(List.of(MODEL + ".update", MODEL + ".search"),
          () -> fixture.actions.invoke(MODEL, "update", com.ouroboros.businessmodel.action.BusinessActionInput.of(
              Map.of("record", Map.of("id", "script-order")), Map.of("id", "another", "state", "conflict")))));
      assertEquals("ready-updated", fixture.orders().get("script-order").get().get("state"));
      return null;
    });
  }

  @Test
  void projectedBatchDeleteActionDeletesOnlyRequestedObjectsThroughTheOfficialFlow() {
    var fixture = new Fixture(true);
    fixture.withModels(() -> {
      fixture.orders().insert(Map.of("id", "one", "state", "keep")).get();
      fixture.orders().insert(Map.of("id", "two", "state", "keep")).get();
      withPermissions(List.of(MODEL + ".batchDelete", MODEL + ".search"), () -> fixture.actions.invoke(
          new BusinessActionInvocation(MODEL, "batchDelete", com.ouroboros.businessmodel.action.BusinessActionInput.paramsOnly(Map.of("records", List.of(Map.of("id", "one")))))));
      assertNull(fixture.orders().get("one").get());
      assertEquals("keep", fixture.orders().get("two").get().get("state"));
      assertEquals(1, fixture.orders().count(Map.of()).get());
      assertEquals("COMMITTED", fixture.audit().query().execute().get().get(0).get("transactionResult"));
      return null;
    });
  }

  @Test
  void projectedImportActionReadsNativeMultipartXlsAndXlsxWithoutSerializingTheFile() throws Exception {
    for (boolean xls : List.of(false, true)) {
      var fixture = new Fixture(true);
      var file = new com.ouroboros.logicflow.adapter.RequestInfoMap.PartMultipartFileAdapter(
          new org.springframework.mock.web.MockPart("file", "orders." + (xls ? "xls" : "xlsx"), workbook(xls)));
      fixture.withModels(() -> {
        withPermissions(List.of(MODEL + ".import"), () -> fixture.actions.invoke(
            new BusinessActionInvocation(MODEL, "import", com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("file", file)))));
        assertEquals(2, fixture.orders().count(Map.of()).get());
        assertEquals("imported", fixture.orders().get("excel-1").get().get("state"));
        assertEquals("imported", fixture.orders().get("excel-2").get().get("state"));
        assertEquals("COMMITTED", fixture.audit().query().execute().get().get(0).get("transactionResult"));
        return null;
      });
    }
  }

  @Test
  void generatedJavaMapClientPreservesNativeImportResources() throws Exception {
    var fixture = new Fixture(true);
    var sources = BusinessBehaviorTypeGenerator.generate(fixture.behaviors.get(MODEL), "example.feature", "OrderFeatures");
    var file = new com.ouroboros.logicflow.adapter.RequestInfoMap.PartMultipartFileAdapter(
        new org.springframework.mock.web.MockPart("file", "orders.xlsx", workbook(false)));
    try (var loader = compile(sources.javaSources())) {
      var type = loader.loadClass("example.feature.OrderFeatures");
      var client = type.getConstructor(com.ouroboros.businessmodel.action.BusinessActionRuntime.class).newInstance(fixture.actions);
      fixture.withModels(() -> withPermissions(List.of(MODEL + ".import"), () -> {
        try {
          type.getMethod("_import", com.ouroboros.businessmodel.action.BusinessActionInput.class).invoke(client, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("file", file)));
        } catch (ReflectiveOperationException error) {
          throw new IllegalStateException(error);
        }
        assertEquals(2, fixture.orders().count(Map.of()).get());
        return null;
      }));
    }
  }

  private byte[] workbook(boolean xls) throws Exception {
    try (org.apache.poi.ss.usermodel.Workbook book = xls ? new org.apache.poi.hssf.usermodel.HSSFWorkbook()
        : new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var bytes = new java.io.ByteArrayOutputStream()) {
      var sheet = book.createSheet("Orders");
      var header = sheet.createRow(0);
      header.createCell(0).setCellValue("id");
      header.createCell(1).setCellValue("state");
      for (int i = 1; i <= 2; i++) {
        var row = sheet.createRow(i);
        row.createCell(0).setCellValue("excel-" + i);
        row.createCell(1).setCellValue("imported");
      }
      book.write(bytes);
      return bytes.toByteArray();
    }
  }

  @Test
  void mvcHandlerCommitsBusinessFactThenANewWorkerRecoversTheSubscriptionActionAndAudits() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      var mvc = MockMvcBuilders.standaloneSetup(new BusinessActionDescriptionController(fixture.actions)).build();
      withPermissions(List.of(MODEL + ".submit"), () -> {
        try {
          mvc.perform(post("/api/business-model-behaviors/" + MODEL + "/actions/submit/invoke")
              .contentType(MediaType.APPLICATION_JSON).content("{\"input\":{\"data\":{\"orderId\":\"order-1\"}}}"))
              .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
              .andExpect(jsonPath("$.data.orderId").value("order-1"));
          return null;
        } catch (Exception error) {
          throw new IllegalStateException(error);
        }
      });
      assertEquals(1, fixture.orders().count(Map.of()).get());
      assertEquals(1, fixture.facts().count(Map.of()).get());
      assertEquals("PENDING", fixture.delivery().get("status"));
      assertEquals(0, fixture.stock().count(Map.of()).get());
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery().get("status"));
      assertEquals("reserved", fixture.stock().get("order-1").get().get("state"));
      var audits = fixture.audit().query().execute().get();
      assertEquals(2, audits.size());
      assertTrue(audits.stream().allMatch(row -> "COMMITTED".equals(row.get("transactionResult"))));
      var submitted = audits.stream().filter(row -> "submit".equals(row.get("actionName"))).findFirst().orElseThrow();
      assertEquals(List.of(fixture.eventId()), Json.tryToBean(List.class, String.valueOf(submitted.get("eventIdsJson"))).get());
      assertTrue(Json.tryToBean(List.class, String.valueOf(submitted.get("writeSetJson"))).get().size() > 0);
      assertEquals(submitted.get("id"), Json.tryToMap(String.valueOf(fixture.fact().get("occurrenceJson"))).get().get("executionId"));
      fixture.worker().sweep();
      assertEquals(1, fixture.reserveCalls);
      return null;
    });
  }

  @Test
  void aCompiledGeneratedClientRemainsValidWhenTheSameActionIsReboundToARealLogicFlow() throws Exception {
    var fixture = new Fixture();
    var generated = BusinessBehaviorTypeGenerator.generate(fixture.behaviors.get(MODEL), "journey.generated", "OrderClient");
    try (var loader = compile(generated.javaSources())) {
      Class<?> client = loader.loadClass("journey.generated.OrderClient");
      Class<?> input = loader.loadClass("journey.generated.OrderClient$SubmitInput");
      Object typed = client.getConstructor(com.ouroboros.businessmodel.action.BusinessActionRuntime.class).newInstance(fixture.actions);
      fixture.withModels(() -> {
        fixture.bindFlow();
        withPermissions(List.of(MODEL + ".submit"), () -> {
          try {
            var output = client.getMethod("submit", com.ouroboros.businessmodel.action.BusinessActionInput.class).invoke(typed, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(input.getConstructor(String.class).newInstance("order-1")));
            assertEquals("order-1", output.getClass().getMethod("orderId").invoke(output));
            return null;
          } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
          }
        });
        assertEquals("submitted", fixture.orders().get("order-1").get().get("state"));
        fixture.worker().sweep();
        assertEquals("SUCCESS", fixture.delivery().get("status"));
        assertEquals(1, fixture.stock().count(Map.of()).get());
        assertEquals(0, fixture.submitCalls);
        assertEquals(1, fixture.reserveCalls);
        assertTrue(fixture.audit().query().execute().get().stream().anyMatch(row ->
            String.valueOf(row.get("implementationRef")).startsWith("logicflow:")));
        return null;
      });
    }
  }

  @Test
  void actionSubscriberJoinsTheSourceCommitAndFailureRollsBackBothActions() {
    for (boolean fail : List.of(false, true)) {
      var fixture = new Fixture();
      var consumer = fixture.behaviors.get(STOCK);
      var definitions = consumer.getEventSubscriptionDefinitions();
      definitions.getFirst().setExecutionMode(BusinessEventExecutionMode.JOIN_SOURCE_TRANSACTION);
      fixture.behaviors.put(STOCK, new BusinessModelBehavior(STOCK, consumer.getActionDefinitions(),
          consumer.getEventDefinitions(), definitions, List.of(), consumer.getModelRevision()));
      fixture.failReserve.set(fail);
      fixture.withModels(() -> {
        if (fail) {
          assertThrows(BusinessException.class, fixture::submit);
          assertEquals(0, fixture.orders().count(Map.of()).get());
          assertEquals(0, fixture.stock().count(Map.of()).get());
          assertEquals(0, fixture.facts().count(Map.of()).get());
          assertEquals(0, fixture.deliveries().count(Map.of()).get());
        } else {
          fixture.submit();
          assertEquals(1, fixture.orders().count(Map.of()).get());
          assertEquals(1, fixture.stock().count(Map.of()).get());
          assertEquals("SUCCESS", fixture.delivery().get("status"));
          assertEquals("event-service", fixture.delivery().get("user"));
          fixture.worker().sweep();
          assertEquals(1, fixture.reserveCalls);
        }
        return null;
      });
    }
  }

  @Test
  void failedSourceRollsBackAllFactsAndFailedSubscriptionRollsBackItsEffectsUntilRedrive() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      fixture.failSubmit.set(true);
      assertThrows(BusinessException.class, fixture::submit);
      assertEquals(0, fixture.orders().count(Map.of()).get());
      assertEquals(0, fixture.facts().count(Map.of()).get());
      assertEquals(0, fixture.deliveries().count(Map.of()).get());
      fixture.failSubmit.set(false);
      fixture.submit();
      var original = Map.copyOf(fixture.fact());
      fixture.failReserve.set(true);
      fixture.worker().sweep();
      assertEquals("DEAD", fixture.delivery().get("status"));
      assertEquals(0, fixture.stock().count(Map.of()).get());
      assertEquals(original, Map.copyOf(fixture.fact()));
      fixture.failReserve.set(false);
      assertTrue(fixture.worker().redrive(String.valueOf(fixture.delivery().get("id"))));
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery().get("status"));
      assertEquals(1, fixture.stock().count(Map.of()).get());
      assertEquals(2, fixture.reserveCalls);
      fixture.worker().sweep();
      assertEquals(2, fixture.reserveCalls);
      return null;
    });
  }

  @Test
  void revokedConsumerAuthorityPausesWithoutEffectsAndRequiresExplicitRedriveAfterRestoration() {
    var fixture = new Fixture();
    fixture.withModels(() -> {
      fixture.submit();
      var original = Map.copyOf(fixture.fact());
      fixture.authorized.set(false);
      fixture.worker().sweep();
      assertEquals("AUTHORIZATION_BLOCKED", fixture.delivery().get("status"));
      assertEquals(0, fixture.reserveCalls);
      fixture.authorized.set(true);
      fixture.worker().sweep();
      assertEquals("AUTHORIZATION_BLOCKED", fixture.delivery().get("status"));
      assertTrue(fixture.worker().redrive(String.valueOf(fixture.delivery().get("id"))));
      fixture.worker().sweep();
      assertEquals("SUCCESS", fixture.delivery().get("status"));
      assertEquals(original, Map.copyOf(fixture.fact()));
      assertEquals(1, fixture.reserveCalls);
      return null;
    });
  }

  @Test
  void generatedRecordAndOpenMapProducersRejectAChangedEventContractBeforeRecording() throws Exception {
    var fixture = new Fixture();
    try (var loader = compile(BusinessBehaviorTypeGenerator.generate(fixture.behaviors.get(MODEL),
        "journey.producer", "OrderClient").javaSources())) {
      var payload = loader.loadClass("journey.producer.OrderClient$SubmittedPayload").getConstructor(String.class).newInstance("order-1");
      fixture.withModels(() -> {
        var occurrence = withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () -> fixture.port.inTransaction(() ->
            fixture.port.emit(IDENTITY, BusinessEventSubject.model(MODEL), payload, BusinessEventEmissionContext.fromProducer("scheduler"))));
        assertEquals(Map.of("orderId", "order-1"), occurrence.payload());
        var changed = event();
        changed.setPayloadSchema(Map.of("type", "object"));
        fixture.behaviors.put(MODEL, new BusinessModelBehavior(MODEL, List.of(fixture.submitAction), List.of(changed), List.of()));
        assertThrows(BusinessException.class, () -> withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () ->
            fixture.port.inTransaction(() -> fixture.port.emit(IDENTITY, BusinessEventSubject.model(MODEL), payload,
                BusinessEventEmissionContext.fromProducer("scheduler")))));
        assertThrows(BusinessException.class, () -> withPermissions(List.of(BusinessEventAuthorities.emit(IDENTITY)), () ->
            fixture.port.inTransaction(() -> fixture.port.emit(IDENTITY, BusinessEventSubject.model(MODEL), Map.of("orderId", "order-1"),
                BusinessEventEmissionContext.fromProducer("scheduler").expectingSchema(BusinessBehaviorContracts.schemaFingerprint(SCHEMA))))));
        assertEquals(1, fixture.facts().count(Map.of()).get());
        return null;
      });
    }
  }

  @Test
  void generatedPayloadAnnotationRegistersAListenerAndChecksBothOriginalAndCurrentSchemas() throws Exception {
    var fixture = new Fixture();
    var sources = new java.util.LinkedHashMap<>(BusinessBehaviorTypeGenerator.generate(fixture.behaviors.get(MODEL),
        "journey.listener", "OrderClient").javaSources());
    sources.put("journey/listener/Consumer.java", """
        package journey.listener;
        public class Consumer {
          public String received;
          @com.ouroboros.businessmodel.annotation.BusinessEventSubscription(
            consumerModelFullName="inventory.Stock", subscriptionName="reserve", listenerName="generatedListener",
            executionMode=com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL)
          public void receive(com.ouroboros.businessmodel.event.BusinessEventOccurrence<OrderClient.SubmittedPayload> event,
              com.ouroboros.businessmodel.event.BusinessEventDeliveryContext delivery) { received = event.payload().orderId(); }
        }
        """);
    try (var loader = compile(sources); var context = annotationContext()) {
      Class<?> consumerType = loader.loadClass("journey.listener.Consumer");
      Object consumer = consumerType.getConstructor().newInstance();
      // Catalog scans bean definitions; this matches normal Spring registration of a typed bean.
      context.registerBean("consumerDefinition", Object.class, () -> consumer);
      context.refresh();
      var catalog = registerBusinessEventAnnotationCatalog(context);
      catalog.afterSingletonsInstantiated();
      context.addApplicationListener(signal -> {
        if (signal instanceof BusinessEventSpringAdapter.DispatchSignal dispatch) {
          new BusinessEventSpringDispatcher().dispatch(dispatch);
        }
      });
      fixture.listeners = List.of(new BusinessEventSpringAdapter(catalog.getListeners().get(0), context));
      assertEquals(BusinessBehaviorContracts.schemaFingerprint(SCHEMA), fixture.listeners.get(0).getPayloadSchemaFingerprint());
      fixture.withModels(() -> {
        fixture.submit();
        var subscription = fixture.subscription();
        subscription.getTarget().setModelFullName(null);
        subscription.getTarget().setKind("action"); subscription.getTarget().setActionName(null);
        subscription.getTarget().setInputMapping(Map.of());
        subscription.getTarget().setKind("listener"); subscription.getTarget().setListenerName("generatedListener");
        fixture.behaviors.put(STOCK, new BusinessModelBehavior(STOCK, List.of(fixture.reserveAction), List.of(), List.of(subscription)));
        var changed = event();
        changed.setPayloadSchema(Map.of("type", "object"));
        fixture.behaviors.put(MODEL, new BusinessModelBehavior(MODEL, List.of(fixture.submitAction), List.of(changed), List.of()));
        fixture.worker().sweep();
        assertEquals("CONTRACT_BLOCKED", fixture.delivery().get("status"));
        assertEquals("listener-sdk-contract-incompatible", fixture.delivery().get("lastError"));
        try {
          assertNull(consumerType.getField("received").get(consumer));
        } catch (ReflectiveOperationException error) {
          throw new IllegalStateException(error);
        }
        fixture.behaviors.put(MODEL, new BusinessModelBehavior(MODEL, List.of(fixture.submitAction), List.of(event()), List.of()));
        var oldFact = BusinessEventOccurrence.create("old-schema-fact", IDENTITY, "old", BusinessEventSubject.model(MODEL),
            Map.of("type", "object"), Map.of("orderId", "order-1"), java.time.Instant.now(), "scheduler", "actor", null, null, null);
        assertEquals("listener-sdk-contract-incompatible",
            fixture.subscriptions.deliver(oldFact, STOCK, "reserve", "old-delivery", 1).diagnosticCode());
        assertTrue(fixture.worker().redrive(String.valueOf(fixture.delivery().get("id"))));
        fixture.worker().sweep();
        assertEquals("SUCCESS", fixture.delivery().get("status"));
        try {
          assertEquals("order-1", consumerType.getField("received").get(consumer));
        } catch (ReflectiveOperationException error) {
          throw new IllegalStateException(error);
        }
        return null;
      });
    } finally {
      try (var empty = annotationContext()) {
        empty.refresh();
        registerBusinessEventAnnotationCatalog(empty).afterSingletonsInstantiated();
      }
    }
  }

  private URLClassLoader compile(Map<String, String> sources) throws Exception {
    var args = new ArrayList<String>(List.of("-classpath", System.getProperty("java.class.path"), "-d", temporary.toString()));
    for (var entry : sources.entrySet()) {
      Path source = temporary.resolve(entry.getKey());
      Files.createDirectories(source.getParent());
      Files.writeString(source, entry.getValue());
      args.add(source.toString());
    }
    assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
    return new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, getClass().getClassLoader());
  }

  private static final class Fixture {
    final Map<String, DataModel> models = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final Map<String, BusinessModel> businessModels = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final Map<String, BusinessModelBehavior> behaviors = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final Map<String, FlowModel> flows = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    final AtomicBoolean failSubmit = new AtomicBoolean();
    final AtomicBoolean failReserve = new AtomicBoolean();
    final AtomicBoolean authorized = new AtomicBoolean(true);
    final ActionDefinition submitAction = action("submit", "submitHandler");
    final ActionDefinition reserveAction = action("reserve", "reserveHandler");
    final ActionDefinition approveAction = approvalAction();
    Runnable approvalGate = () -> { };
    final DataSourceTransactionManager manager;
    final DefaultBusinessActionRuntime actions;
    final DefaultBusinessEventPort port;
    final DefaultBusinessEventSubscriptionRuntime subscriptions;
    List<BusinessEventListener> listeners = List.of();
    int submitCalls;
    int reserveCalls;

    Fixture() {
      this(false);
    }

    Fixture(boolean features) {
      this(features, false);
    }

    Fixture(boolean features, boolean logicalDelete) {
      this(features, logicalDelete, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL"));
    }

    Fixture(boolean features, boolean logicalDelete, String mysqlUrl) {
      this(features, logicalDelete, mysqlUrl, meta -> { });
    }

    Fixture(java.util.function.Consumer<DefaultBusinessModelMeta> configure) {
      this(true, false, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL"), configure);
    }

    Fixture(boolean features, boolean logicalDelete, String mysqlUrl,
            java.util.function.Consumer<DefaultBusinessModelMeta> configure) {
      var reference = new EventReference();
      reference.setModelFullName(MODEL);
      reference.setEventName("submitted");
      submitAction.setEmits(List.of(reference));
      var provider = new BusinessModelProvider() {
        public Optional<BusinessModel> getBusinessModel(String name) { return Optional.ofNullable(businessModels.get(name)); }
        public Map<String, BusinessModel> getBusinessModelMap() { return businessModels; }
      };
      var order = meta("sales", "Order", "orders", submitAction);
      order.setActions(List.of(submitAction, approveAction));
      if (features) {
        order.setExtraProp("apis", Map.of(
            "delete", Map.of("isEnabled", true, "isAuto", true),
            "add", Map.of("isEnabled", true, "isAuto", true, "beforeScriptLanguage", "groovy", "afterScriptLanguage", "groovy",
                "beforeScript", "assert $request.body.is($request.data); $data.put('state', $request.body.state + '-prepared'); return $data;",
                "afterScript", "assert $request.body.is($request.data); return [requestId: $request.body.id, state: $data.state];"),
            "update", Map.of("isEnabled", true, "isAuto", true, "beforeScriptLanguage", "groovy", "afterScriptLanguage", "groovy",
                "beforeScript", "assert $request.body.is($request.data); $statement.data.put('state', $request.body.state + '-updated'); return $statement;",
                "afterScript", "assert $request.body.is($request.data); return [requestId: $request.body.id];")));
        order.setExtraProp("features", Map.of("isEnabledBatchDelete", true, "isEnabledImport", true,
            "import", Map.of("dataMapMode", "custom", "dataMap", Map.of("id", "$item.get('id')", "state", "$item.get('state')"))));
      }
      configure.accept(order);
      var submitted = event();
      submitted.setOccurrenceCondition("Order submission committed");
      order.setEvents(List.of(submitted));
      var stock = meta("inventory", "Stock", "stock", reserveAction);
      stock.setEventSubscriptions(List.of(subscription()));
      businessModels.put(MODEL, new DefaultBusinessModel(order, provider));
      businessModels.put(STOCK, new DefaultBusinessModel(stock, provider));
      var metas = new ArrayList<>(TypedModelMetaHelper.scanModelMate("com.ouroboros.businessmodel.event.store").values());
      metas.addAll(TypedModelMetaHelper.scanModelMate("com.ouroboros.businessmodel.action.audit").values());
      var featureFlows = new ArrayList<FlowModelMeta>();
      businessModels.values().forEach(model -> {
        assertTrue(model.getValidationResult().isValid(), model.getValidationResult().getErrors().toString());
        com.ouroboros.businessmodel.projection.ModelBundle bundle;
        try (var config = mockStatic(com.ouroboros.businessmodel.config.BasicAuditFieldsConfiguration.class)) {
          config.when(com.ouroboros.businessmodel.config.BasicAuditFieldsConfiguration::getConfiguration)
              .thenReturn(new com.ouroboros.businessmodel.config.DefaultBasicAuditFieldsConfiguration());
          bundle = model.buildModelBundle();
        }
        var dataMeta = bundle.getModel(DataModelMeta.class, model.getFullName()).orElseThrow();
        if (logicalDelete && MODEL.equals(model.getFullName())) { dataMeta.setExtraProp("enableSoftDelete", true); }
        metas.addAll(bundle.getModelMap(DataModelMeta.class).values());
        behaviors.put(model.getFullName(), bundle.getModel(BusinessModelBehavior.class, model.getFullName()).orElseThrow());
        if (features && MODEL.equals(model.getFullName())) {
          featureFlows.add(bundle.getModel(FlowModelMeta.class, MODEL + ".delete").orElseThrow());
          featureFlows.add(bundle.getModel(FlowModelMeta.class, MODEL + ".batchDelete").orElseThrow());
          featureFlows.add(bundle.getModel(FlowModelMeta.class, MODEL + ".import").orElseThrow());
          featureFlows.add(bundle.getModel(FlowModelMeta.class, MODEL + ".add").orElseThrow());
          featureFlows.add(bundle.getModel(FlowModelMeta.class, MODEL + ".update").orElseThrow());
        }
      });
      javax.sql.DataSource source;
      String dialect;
      if (mysqlUrl == null) {
        var h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:business_journey_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        h2.setUser("sa");
        source = h2;
        dialect = "H2";
      } else {
        source = new org.springframework.jdbc.datasource.DriverManagerDataSource(mysqlUrl,
            System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"), System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD"));
        dialect = "MySQL";
        String suffix = "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        metas.forEach(metadata -> metadata.setRawName(metadata.getRawName() + suffix));
      }
      manager = new DataSourceTransactionManager(source);
      var jdbc = new JdbcTemplate(source);
      var station = new SqlDataStation("journey", source, metas, MigrationStrategy.AUTO, dialect);
      station.getDataModelList().forEach(model -> models.put(model.getFullName(), model));
      station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
      for (var metadata : featureFlows) {
        var flow = FlowModelFactory.build(metadata, NodeReferenceCatalog.of(List.of(
            NodeReferenceSet.complete(DataModelReferences.KIND, models, String::compareToIgnoreCase))));
        assertTrue(flow.isValid(), flow.getErrors().toString());
        flows.put(flow.getName(), flow);
      }
      var contracts = new com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider() {
        public Optional<BusinessModelBehavior> getBusinessModelBehavior(String name) { return Optional.ofNullable(behaviors.get(name)); }
        public Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap() { return behaviors; }
      };
      port = new DefaultBusinessEventPort(contracts, () -> List.of((BusinessEventProducer) () -> "scheduler"),
          new DataModelBusinessEventOccurrenceRecorder(contracts, this::subscriptionRuntime, id -> { }), manager);
      actions = new DefaultBusinessActionRuntime(contracts, new DefaultLogicFlowExecutionFacade(), List.of(
          handler("submitHandler", input -> {
            submitCalls++;
            orders().insert(Map.of("id", input.get("orderId"), "state", "submitted")).get();
            port.emit(IDENTITY, BusinessEventSubject.model(MODEL), input, BusinessEventEmissionContext.fromAction());
            if (failSubmit.get()) { throw new IllegalStateException("source failure"); }
            return input;
          }), handler("inspectHandler", input -> {
            assertTrue(com.ouroboros.data.model.DataModelExecutionContext.isReadOnly());
            assertNotNull(orders().get(input.get("orderId")).get());
            return input;
          }), new BusinessActionHandler() {
            public String getName() { return "approveHandler"; }
            public Object handle(BusinessActionContext context, com.ouroboros.businessmodel.action.BusinessActionInput<?, ?> input) {
              approvalGate.run();
              Object id = context.objectStates().get("/params/order").get("id");
              orders().update(id, Map.of("state", "APPROVED")).get();
              var payload = Map.<String, Object>of("orderId", id);
              port.emit(IDENTITY, BusinessEventSubject.model(MODEL), payload, BusinessEventEmissionContext.fromAction());
              return payload;
            }
          }, handler("reserveHandler", input -> {
            reserveCalls++;
            stock().insert(Map.of("id", input.get("orderId"), "state", "reserved")).get();
            if (failReserve.get()) { throw new IllegalStateException("consumer failure"); }
            return input;
          })), manager, new DataModelBusinessActionExecutionAuditRecorder(manager));
      actions.setMutationEvents(new com.ouroboros.businessmodel.action.BusinessActionMutationEventEmitter(
          () -> port, new com.ouroboros.businessmodel.action.BusinessMutationEventProperties()));
      var users = mock(UserCenter.class);
      when(users.getUser("stock-service")).thenAnswer(lookup -> Optional.of(user(authorized.get()
          ? Map.of(BusinessEventAuthorities.read(IDENTITY), com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), STOCK + ".reserve", com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(),
              MODEL + ".events.recordsDeleted.read", com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all(), MODEL + ".search", com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all())
          : Map.of())));
      subscriptions = new DefaultBusinessEventSubscriptionRuntime(contracts, actions, () -> listeners, identities(users), manager);
    }

    private BusinessEventSubscriptionRuntime subscriptionRuntime() { return subscriptions; }

    void bindFlow() {
      var mapping = FlowNodeMeta.builder().name("Input").type("mapping").inputPort("action")
          .outputPort(FlowNodePortMeta.builder().name("").build())
          .extraProp("mapping", Map.of("id", "${data.orderId}", "state", "${'submitted'}")).build();
      var insert = FlowNodeMeta.builder().name("Insert").type("insertData").inputPort("data")
          .outputPort(FlowNodePortMeta.builder().name("").build()).extraProp("dataModel", MODEL).build();
      var payload = FlowNodeMeta.builder().name("Payload").type("mapping").inputPort("order")
          .outputPort(FlowNodePortMeta.builder().name("").build())
          .extraProp("mapping", Map.of("payload", Map.of("orderId", "${id}"))).build();
      var emit = FlowNodeMeta.builder().name("Emit").type("emitBusinessEvent").inputPort("event")
          .outputPort(FlowNodePortMeta.builder().name("").build())
          .extraProp("modelFullName", MODEL).extraProp("eventName", "submitted").build();
      var metadata = FlowModelMeta.builder().name(MODEL + ".submitFlow").entryNode(mapping)
          .connect(insert).connect(payload).connect(emit).exitNode("Emit").build();
      var flow = FlowModelFactory.build(metadata, NodeReferenceCatalog.of(List.of(
          NodeReferenceSet.complete(DataModelReferences.KIND, models, String::compareToIgnoreCase))));
      assertTrue(flow.isValid(), flow.getErrors().toString());
      flows.put(flow.getName(), flow);
      var action = action("submit", "submitHandler");
      action.setEmits(submitAction.getEmits());
      var binding = new ActionBinding();
      binding.setKind("logicflow");
      binding.setFlowName(flow.getName());
      binding.setOutputMapping("$.payload");
      action.setBinding(binding);
      behaviors.put(MODEL, new BusinessModelBehavior(MODEL, List.of(action), List.of(event()), List.of(), List.of(), "rebound-1"));
    }

    Object submit() { return withPermissions(List.of(MODEL + ".submit"), () -> actions.invoke(new BusinessActionInvocation(MODEL, "submit", com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("orderId", "order-1"))))); }
    BusinessEventOutbox worker() {
      var properties = new BusinessEventDeliveryProperties();
      properties.setMaxRetryCount(0);
      return new BusinessEventOutbox(manager, () -> subscriptions, properties, Clock.systemUTC());
    }
    DataModel orders() { return models.get(MODEL); }
    DataModel stock() { return models.get(STOCK); }
    DataModel facts() { return models.get(DataModelBusinessEventOccurrenceRecorder.MODEL_FULL_NAME); }
    DataModel deliveries() { return models.get(BusinessEventOutbox.MODEL_FULL_NAME); }
    DataModel audit() { return models.get("ouroboros.BusinessActionExecutionAudit"); }
    Record fact() { return facts().query().execute().get().get(0); }
    String eventId() { return String.valueOf(fact().get("id")); }
    Record delivery() { return deliveries().query().execute().get().get(0); }

    <T> T withAuditedModels(Supplier<T> operation) {
      return com.ouroboros.data.model.DataOperationIdentityProviders.withCurrentOperator("event-service", () -> withModels(operation));
    }

    <T> T withModels(Supplier<T> operation) {
      try (var center = mockStatic(DataModelCenter.class); var flow = mockStatic(FlowModelCenter.class);
          var spring = mockStatic(SpringContextUtil.class, CALLS_REAL_METHODS)) {
        center.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(lookup -> Optional.ofNullable(models.get(lookup.getArgument(0))));
        center.when(DataModelCenter::getDataModelMap).thenReturn(models);
        center.when(DataModelCenter::hasSnapshot).thenReturn(true);
        flow.when(() -> FlowModelCenter.getFlowModel(anyString())).thenAnswer(lookup -> Optional.ofNullable(flows.get(lookup.getArgument(0))));
        flow.when(FlowModelCenter::getFlowModelMap).thenReturn(flows);
        spring.when(() -> SpringContextUtil.getBean(BusinessEventPort.class)).thenReturn(port);
        return operation.get();
      }
    }

    private EventSubscriptionDefinition subscription() {
      var subscription = new EventSubscriptionDefinition(); subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL);
      subscription.setName("reserve");
      subscription.getSourceEvent().setModelFullName(MODEL);
      subscription.getSourceEvent().setEventName("submitted");
      subscription.getTarget().setModelFullName(STOCK);
      subscription.getTarget().setKind("action"); subscription.getTarget().setActionName("reserve");
      subscription.getTarget().setInputMapping(Map.of("data.orderId", "${event.payload.orderId}"));
      return subscription;
    }

    private static BusinessActionHandler handler(String name, java.util.function.Function<Map<String, Object>, Object> work) {
      return new BusinessActionHandler() {
        public String getName() { return name; }
        public Object handle(BusinessActionContext context, com.ouroboros.businessmodel.action.BusinessActionInput<?, ?> actionInput) {
      Map<String, Object> input = com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(actionInput.data())); return work.apply(input); }
      };
    }

    private static ActionDefinition action(String name, String handler) {
      var action = new ActionDefinition();
      action.setName(name);
      action.setTitle(name);
      action.setIsEnabled(true);
      action.setReadOnly(false);

      action.setInputSchema(Map.of("type", "object", "additionalProperties", false, "required", List.of("data"), "properties", Map.of("data", SCHEMA)));
      action.setOutputSchema(SCHEMA);
      var binding = new ActionBinding();
      binding.setKind("handler");
      binding.setHandlerName(handler);
      action.setBinding(binding);
      return action;
    }

    private static ActionDefinition approvalAction() {
      var action = action("approve", "approveHandler");
      var referenceSchema = Map.of("type", "object", "x-business-object-ref", MODEL, "required", List.of("id"),
          "properties", Map.of("id", Map.of("type", "string")));
      var params = Map.of("type", "object", "required", List.of("order"), "properties", Map.of("order", referenceSchema));
      action.setInputSchema(Map.of("type", "object", "required", List.of("params"), "properties", Map.of("params", params)));
      action.setPrecondition(Map.of("expression", "${objectStates['/params/order'].state == 'DRAFT'}", "message", "只有草稿订单可以审批"));
      var reference = new EventReference();
      reference.setModelFullName(MODEL);
      reference.setEventName("submitted");
      action.setEmits(List.of(reference));
      return action;
    }

    private static DefaultBusinessModelMeta meta(String namespace, String name, String rawName, ActionDefinition action) {
      var meta = new DefaultBusinessModelMeta();
      meta.setNamespace(namespace);
      meta.setName(name);
      meta.setTitle(name);
      meta.setIsEnabled(true);
      meta.setExtraProp("rawTableName", rawName);
      for (String operation : List.of("add", "update", "delete", "getOne", "search", "download")) {
        meta.setExtraProp(operation, Map.of("isEnabled", false));
      }
      meta.setActions(List.of(action));
      meta.setFields(List.of(field("id", true), field("state", false)));
      return meta;
    }

    private static BusinessModelFieldMeta field(String name, boolean primary) {
      var field = new BusinessModelFieldMeta();
      field.setName(name);
      field.setTitle(name);
      field.setType("单行文本");
      field.setIsPrimaryKey(primary);
      field.setIsRequired(true);
      return field;
    }
  }

  private static com.ouroboros.businessmodel.BusinessEventAnnotationCatalog registerBusinessEventAnnotationCatalog(org.springframework.context.support.GenericApplicationContext context) {
    var catalog = new com.ouroboros.businessmodel.BusinessEventAnnotationCatalog(context.getBeanFactory());
    context.getBeanFactory().registerSingleton(BusinessEventAnnotationCatalog.class.getName(), catalog);
    return catalog;
  }
  private static org.springframework.context.support.GenericApplicationContext annotationContext() {
    var context = new org.springframework.context.support.GenericApplicationContext();
    context.registerBean(com.ouroboros.common.app.util.SpringContextUtil.class);
    return context;
  }
  private static com.ouroboros.businessmodel.event.BusinessEventConsumerIdentityResolver identities(UserCenter users) {
    var settings = new com.ouroboros.businessmodel.event.BusinessEventConsumerProperties();
    settings.setPrincipals(Map.of("inventory.Stock", "stock-service"));
    return new com.ouroboros.businessmodel.event.BusinessEventConsumerIdentityResolver(settings, users);
  }
}
