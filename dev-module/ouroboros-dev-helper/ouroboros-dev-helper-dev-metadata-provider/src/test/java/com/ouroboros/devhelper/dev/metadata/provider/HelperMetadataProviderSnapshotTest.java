package com.ouroboros.devhelper.dev.metadata.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.dev.event.CreateAppEvent;
import com.ouroboros.dev.event.DeleteAppEvent;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataSnapshotStatus;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.metadata.HelperRuntimeActivatedEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeRetiredEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeTarget;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshRequestedEvent;
import com.ouroboros.devhelper.metadata.MetadataRefreshSnapshot;
import com.ouroboros.devhelper.metadata.MetadataSnapshotPublishedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotRejectedEvent;

class HelperMetadataProviderSnapshotTest {
  private final AtomicReference<Consumer<CommandResult>> refreshListener = new AtomicReference<>();
  private final List<Object> publishedEvents = new CopyOnWriteArrayList<>();
  private DevHelperCommandService commandService;
  private HelperMetadataProvider provider;

  @BeforeEach
  void setUp() {
    CacheManager.getCacheManager().clear();
    commandService = mock(DevHelperCommandService.class);
    doAnswer(invocation -> {
      if (Commands.REFRESH_METADATA.getCommand().equals(invocation.getArgument(0))) {
        refreshListener.set(invocation.getArgument(1));
      }
      return null;
    }).when(commandService).registerCommandResultListener(any(String.class), any());
    provider = new HelperMetadataProvider(commandService, publishedEvents::add);
    online("crm", "helper-a", 1000L);
  }

  @Test
  void providerShouldOnlyObserveRefreshResultsFromTheCommandService() {
    verify(commandService).registerCommandResultListener(
        org.mockito.ArgumentMatchers.eq(Commands.REFRESH_METADATA.getCommand()),
        any()
    );
    verify(commandService).registerCommandResultListener(
        org.mockito.ArgumentMatchers.eq(Commands.READ_METADATA.getCommand()), any());
    verifyNoMoreInteractions(commandService);
  }

  @Test
  void applicationSnapshotShouldExposeUnavailableCurrentAndPendingStates() {
    assertEquals(MetadataSnapshotStatus.UNAVAILABLE,
        provider.getApplicationMetadataSnapshot("crm").getStatus());

    emit(snapshotResult("crm", 1L, 1L, modules(metadata("data-model", "Customer"))));
    assertEquals(MetadataSnapshotStatus.CURRENT,
        provider.getApplicationMetadataSnapshot("crm").getStatus());

    request(snapshotResult("crm", 1L, 2L, modules(metadata("data-model", "Order"))));
    var pending = provider.getApplicationMetadataSnapshot("crm");
    assertEquals(MetadataSnapshotStatus.STALE, pending.getStatus());
    assertEquals("Customer", pending.getModule("data-model").get(0).getName());
  }

  @Test
  void rejectedRefreshShouldKeepLastSuccessfulApplicationSnapshotStale() {
    emit(snapshotResult("crm", 1L, 1L, modules(metadata("data-model", "Customer"))));

    var failed = snapshotResult("crm", 1L, 2L, modules(metadata("data-model", "Order")))
        .setSuccess(false)
        .setData(null)
        .setMessage("refresh failed");
    emit(failed);

    var snapshot = provider.getApplicationMetadataSnapshot("crm");
    assertEquals(MetadataSnapshotStatus.STALE, snapshot.getStatus());
    assertEquals("Customer", snapshot.getModule("data-model").get(0).getName());

    emit(snapshotResult("crm", 1L, 3L, modules(metadata("data-model", "Order"))));
    assertEquals(MetadataSnapshotStatus.CURRENT,
        provider.getApplicationMetadataSnapshot("crm").getStatus());
  }

  @Test
  void applicationSnapshotViewShouldRetainThePublishedVersionAfterANewRefresh() {
    emit(snapshotResult("crm", 1L, 1L, modules(metadata("data-model", "Customer"))));
    var firstSnapshot = provider.getApplicationMetadataSnapshot("crm");

    emit(snapshotResult("crm", 1L, 2L, modules(metadata("data-model", "Order"))));

    assertEquals("Customer", firstSnapshot.getModule("data-model").get(0).getName());
    assertEquals("Order", provider.getApplicationMetadataSnapshot("crm")
        .getModule("data-model").get(0).getName());
  }

  @AfterEach
  void tearDown() {
    CacheManager.getCacheManager().clear();
  }

  @Test
  void authoritativeEmptySnapshotShouldClearPreviousModules() {
    emit(snapshotResult("crm", 1L, 1L, modules(
        metadata("data-model", "Customer"),
        metadata("logic-flow", "flow-a")
    )));

    emit(snapshotResult("crm", 1L, 2L, Map.of("data-model", List.of())));

    assertTrue(provider.getAppAllMetadataByModule("crm", "data-model").isEmpty());
    assertTrue(provider.getAppAllMetadataByModule("crm", "logic-flow").isEmpty());
    assertEquals(2, publishedEvents.stream().filter(MetadataSnapshotPublishedEvent.class::isInstance).count());
  }

  @Test
  void absentOrNullModulesShouldBeRejectedButExplicitEmptyModulesShouldPublish() {
    emit(snapshotResult("crm", 1L, 1L, modules(metadata("data-model", "Customer"))));

    emit(wireSnapshotResult(false, null, 2L));
    assertEquals("Customer", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());

    emit(wireSnapshotResult(true, null, 3L));
    assertEquals("Customer", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());

    emit(wireSnapshotResult(true, Map.of(), 4L));
    assertTrue(provider.getAppAllMetadataByModule("crm", "data-model").isEmpty());
  }

  @Test
  void malformedSnapshotShouldPreserveLastSuccessfulSnapshot() {
    emit(snapshotResult("crm", 1L, 1L, modules(metadata("data-model", "Customer"))));
    var invalid = metadata("wrong-type", "Order");

    emit(snapshotResult("crm", 1L, 2L, Map.of("data-model", List.of(invalid))));

    assertEquals("Customer", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
    assertEquals(1L, provider.getAppMetadataSnapshot("crm").orElseThrow().getRefreshRevision());
  }

  @Test
  void failedRefreshShouldPreserveLastSuccessfulSnapshot() {
    emit(snapshotResult("crm", 1L, 1L, modules(metadata("data-model", "Customer"))));
    emit(new CommandResult()
        .setAppName("crm")
        .setCommand(Commands.REFRESH_METADATA.getCommand())
        .setRuntimeGeneration(1L)
        .setSuccess(false));

    assertEquals("Customer", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
  }

  @Test
  void nullRefreshPayloadShouldPreserveLastSuccessfulSnapshot() {
    emit(snapshotResult("crm", 1L, 1L, modules(metadata("data-model", "Customer"))));

    emit(new CommandResult()
        .setAppName("crm")
        .setCommand(Commands.REFRESH_METADATA.getCommand())
        .setRuntimeGeneration(1L)
        .setSuccess(true));

    assertEquals("Customer", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
  }

  @Test
  void malformedSuccessfulSnapshotShouldPublishTupleBoundRejection() {
    emit(wireSnapshotResult(true, null, 7L));

    MetadataSnapshotRejectedEvent rejected = publishedEvents.stream()
        .filter(MetadataSnapshotRejectedEvent.class::isInstance)
        .map(MetadataSnapshotRejectedEvent.class::cast)
        .findFirst()
        .orElseThrow();
    assertEquals("crm", rejected.getAppName());
    assertEquals(Long.valueOf(1L), rejected.getRuntimeGeneration());
    assertEquals("helper-a", rejected.getHelperInstanceId());
    assertEquals(1000L, rejected.getHelperStartedAtEpochMillis());
    assertEquals("incarnation-crm", rejected.getAppIncarnationId());
    assertEquals("refresh-7", rejected.getRefreshOperationId());
  }

  @Test
  void nullGenerationShouldOnlyPublishBeforeAGenerationIsKnown() {
    emit(snapshotResult("crm", null, 1L, modules(metadata("data-model", "Initial"))));
    assertEquals("Initial", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());

    emit(snapshotResult("crm", 2L, 1L, modules(metadata("data-model", "Managed"))));
    emit(snapshotResult("crm", null, 100L, modules(metadata("data-model", "Unknown"))));

    assertEquals("Managed", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
    assertEquals(2L, provider.getAppMetadataSnapshot("crm").orElseThrow().getRuntimeGeneration());
  }

  @Test
  void concurrentOutOfOrderResultsShouldKeepNewestGenerationAndRevision() throws Exception {
    var executor = Executors.newFixedThreadPool(8);
    try {
      var results = new ArrayList<CommandResult>();
      var futures = new ArrayList<Future<?>>();
      for (long revision = 1; revision <= 30; revision++) {
        results.add(snapshotResult(
            "crm",
            5L,
            revision,
            modules(metadata("data-model", "model-" + revision))
        ));
      }
      results.forEach(this::request);
      java.util.Collections.shuffle(results);
      for (var result : results) {
        futures.add(executor.submit(() -> emitResult(result)));
      }
      for (var future : futures) {
        future.get();
      }
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    var snapshot = provider.getAppMetadataSnapshot("crm").orElseThrow();
    assertEquals(30L, snapshot.getRefreshRevision());
    assertEquals("model-30", snapshot.getModules().get("data-model").get(0).getName());

    emit(snapshotResult("crm", 4L, 100L, modules(metadata("data-model", "old-runtime"))));
    assertEquals("model-30", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
  }

  @Test
  void supersededOperationShouldNotOverwriteNewerSuccessfulRequest() {
    CommandResult operationA = snapshotResult(
        "crm",
        1L,
        2L,
        modules(metadata("data-model", "LateA")),
        "helper-a",
        1000L,
        "operation-a"
    );
    CommandResult operationB = snapshotResult(
        "crm",
        1L,
        1L,
        modules(metadata("data-model", "CurrentB")),
        "helper-a",
        1000L,
        "operation-b"
    );
    request(operationA);
    request(operationB);

    emitResult(operationB);
    emitResult(operationA);

    assertEquals("CurrentB", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
    assertEquals("operation-b", provider.getAppMetadataSnapshot("crm").orElseThrow().getRefreshOperationId());
  }

  @Test
  void supersededOperationShouldNotPublishAfterCurrentRequestFails() {
    emit(snapshotResult("crm", 1L, 1L, modules(metadata("data-model", "Baseline"))));
    CommandResult operationA = snapshotResult(
        "crm",
        1L,
        3L,
        modules(metadata("data-model", "LateA")),
        "helper-a",
        1000L,
        "operation-a"
    );
    CommandResult operationB = snapshotResult(
        "crm",
        1L,
        2L,
        modules(metadata("data-model", "RejectedB")),
        "helper-a",
        1000L,
        "operation-b"
    );
    request(operationA);
    request(operationB);

    emitResult(new CommandResult()
        .setAppName("crm")
        .setCommand(Commands.REFRESH_METADATA.getCommand())
        .setRuntimeGeneration(1L)
        .setHelperInstanceId("helper-a")
        .setHelperStartedAtEpochMillis(1000L)
        .setAppIncarnationId("incarnation-crm")
        .setRefreshOperationId("operation-b")
        .setSuccess(false));
    emitResult(operationA);

    assertEquals("Baseline", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
    assertEquals("refresh-1", provider.getAppMetadataSnapshot("crm").orElseThrow().getRefreshOperationId());
  }

  @Test
  void failedCommandResultShouldPublishTupleBoundRejection() {
    CommandResult failed = snapshotResult(
        "crm",
        3L,
        1L,
        modules(metadata("data-model", "Rejected")),
        "helper-a",
        1000L,
        "operation-failed"
    ).setSuccess(false).setMessage("collector failed");
    request(failed);

    emitResult(failed);

    MetadataSnapshotRejectedEvent rejection = publishedEvents.stream()
        .filter(MetadataSnapshotRejectedEvent.class::isInstance)
        .map(MetadataSnapshotRejectedEvent.class::cast)
        .findFirst()
        .orElseThrow();
    assertEquals(Long.valueOf(3L), rejection.getRuntimeGeneration());
    assertEquals("operation-failed", rejection.getRefreshOperationId());
    assertEquals("collector failed", rejection.getReason());
    assertEquals(runtimeTarget("crm"), rejection.getRuntimeTarget());
  }

  @Test
  void mismatchedRejectionShouldNotClearCurrentRequest() {
    CommandResult current = snapshotResult(
        "crm",
        3L,
        1L,
        modules(metadata("data-model", "Current")),
        "helper-a",
        1000L,
        "operation-current"
    );
    request(current);

    provider.onMetadataSnapshotRejected(new MetadataSnapshotRejectedEvent(
        "crm",
        Long.valueOf(3L),
        "helper-a",
        1000L,
        "incarnation-crm",
        "operation-other",
        "other operation failed",
        runtimeTarget("crm")
    ));
    provider.onMetadataSnapshotRejected(new MetadataSnapshotRejectedEvent(
        "crm",
        Long.valueOf(3L),
        "helper-a",
        1000L,
        "incarnation-crm",
        "operation-current",
        "other target failed",
        new HelperRuntimeTarget("DOCKER", "dev-helper", "runtime-other")
    ));
    emitResult(current);

    assertEquals("Current", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
  }

  @Test
  void newerHelperInstanceShouldNotResetGenerationWithinSameAppIncarnation() {
    emit(snapshotResult("crm", 7L, 20L, modules(metadata("data-model", "Old"))));

    online("crm", "helper-b", 2000L);
    emit(snapshotResult(
        "crm",
        1L,
        1L,
        modules(metadata("data-model", "New")),
        "helper-b",
        2000L
    ));

    assertEquals("Old", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
    assertEquals("helper-a", provider.getAppMetadataSnapshot("crm").orElseThrow().getHelperInstanceId());

    emit(snapshotResult(
        "crm",
        8L,
        1L,
        modules(metadata("data-model", "New")),
        "helper-b",
        2000L
    ));

    assertEquals("New", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
    assertEquals("helper-b", provider.getAppMetadataSnapshot("crm").orElseThrow().getHelperInstanceId());
    assertEquals(Long.valueOf(8L), provider.getAppMetadataSnapshot("crm").orElseThrow().getRuntimeGeneration());
  }

  @Test
  void sameHelperInstanceIdShouldKeepItsOriginalStartIdentity() {
    emit(snapshotResult("crm", 1L, 1L, modules(metadata("data-model", "Original"))));

    online("crm", "helper-a", 2000L);
    emit(snapshotResult(
        "crm",
        1L,
        2L,
        modules(metadata("data-model", "Mismatched")),
        "helper-a",
        2000L
    ));
    emit(snapshotResult("crm", 1L, 2L, modules(metadata("data-model", "Current"))));

    var snapshot = provider.getAppMetadataSnapshot("crm").orElseThrow();
    assertEquals("Current", snapshot.getModules().get("data-model").get(0).getName());
    assertEquals(1000L, snapshot.getHelperStartedAtEpochMillis());
    MetadataSnapshotPublishedEvent event = publishedEvents.stream()
        .filter(MetadataSnapshotPublishedEvent.class::isInstance)
        .map(MetadataSnapshotPublishedEvent.class::cast)
        .reduce((left, right) -> right)
        .orElseThrow();
    assertEquals(1000L, event.getHelperStartedAtEpochMillis());
  }

  @Test
  void retiredHelperShouldNotReactivateOrPublishAgain() {
    emit(snapshotResult("crm", 1L, 1L, modules(metadata("data-model", "Original"))));
    provider.onHelperRuntimeRetired(new HelperRuntimeRetiredEvent(
        "crm",
        "helper-a",
        1000L,
        "incarnation-crm",
        runtimeTarget("crm")
    ));

    online("crm", "helper-a", 1000L);
    emit(snapshotResult("crm", 2L, 2L, modules(metadata("data-model", "LateOld"))));

    assertEquals("Original", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
    assertEquals(1L, provider.getAppMetadataSnapshot("crm").orElseThrow().getRuntimeGeneration());
  }

  @Test
  void delayedRetirementShouldNotDetachTheCurrentHelperTarget() {
    online("crm", "helper-b", 2000L);

    provider.onHelperRuntimeRetired(new HelperRuntimeRetiredEvent(
        "crm",
        "helper-a",
        1000L,
        "incarnation-crm",
        runtimeTarget("crm")
    ));
    emit(snapshotResult(
        "crm",
        2L,
        1L,
        modules(metadata("data-model", "Current")),
        "helper-b",
        2000L
    ));

    MetadataRefreshSnapshot snapshot = provider.getAppMetadataSnapshot("crm").orElseThrow();
    assertEquals("helper-b", snapshot.getHelperInstanceId());
    assertEquals("Current", snapshot.getModules().get("data-model").get(0).getName());
  }

  @Test
  void mismatchedRefreshOperationShouldBeRejected() {
    CommandResult result = snapshotResult(
        "crm",
        1L,
        1L,
        modules(metadata("data-model", "WrongOperation"))
    ).setRefreshOperationId("another-operation");

    emit(result);

    assertFalse(provider.getAppMetadataSnapshot("crm").isPresent());
  }

  @Test
  void returnedMetadataShouldNotMutatePublishedSnapshot() {
    var original = metadata("data-model", "Customer");
    original.setDetails(new LinkedHashMap<>(Map.of("label", "Customer")));
    emit(snapshotResult("crm", 1L, 1L, modules(original)));

    var returned = provider.getAppAllMetadataByModule("crm", "data-model");
    assertThrows(UnsupportedOperationException.class, () -> returned.add(metadata("data-model", "Order")));
    returned.get(0).setName("Changed");
    returned.get(0).getDetails().put("label", "Changed");

    var reread = provider.getAppAllMetadataByModule("crm", "data-model").get(0);
    assertEquals("Customer", reread.getName());
    assertEquals("Customer", reread.getDetails().get("label"));
  }

  @Test
  void snapshotsShouldBeIsolatedByApplicationAndEvictedOnDelete() {
    online("erp", "helper-erp", 1000L);
    emit(snapshotResult("crm", 1L, 1L, modules(metadata("data-model", "Customer"))));
    emit(snapshotResult(
        "erp",
        1L,
        1L,
        modules(metadata("data-model", "Invoice")),
        "helper-erp",
        1000L
    ));

    assertEquals("Customer", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
    assertEquals("Invoice", provider.getAppAllMetadataByModule("erp", "data-model").get(0).getName());

    provider.onDeleteApp(new DeleteAppEvent(this, "crm"));
    emit(snapshotResult("crm", 1L, 2L, modules(metadata("data-model", "LateCustomer"))));

    assertFalse(provider.getAppMetadataSnapshot("crm").isPresent());
    assertTrue(provider.getAppMetadataSnapshot("erp").isPresent());

    provider.onCreateApp(new CreateAppEvent(this, "crm"));
    emit(snapshotResult("crm", 1L, 3L, modules(metadata("data-model", "StillLate"))));
    online("crm", "helper-a", 1000L);
    emit(snapshotResult("crm", 1L, 4L, modules(metadata("data-model", "LateAfterRecreate"))));
    assertFalse(provider.getAppMetadataSnapshot("crm").isPresent());

    online("crm", "helper-new-app", 3000L);
    emit(snapshotResult(
        "crm",
        1L,
        1L,
        modules(metadata("data-model", "NewCustomer")),
        "helper-new-app",
        3000L
    ));
    assertEquals("NewCustomer", provider.getAppAllMetadataByModule("crm", "data-model").get(0).getName());
  }

  private void emit(CommandResult result) {
    request(result);
    emitResult(result);
  }

  private void emitResult(CommandResult result) {
    refreshListener.get().accept(result);
  }

  private void request(CommandResult result) {
    if (result.getHelperInstanceId() == null
        || result.getHelperStartedAtEpochMillis() == null
        || result.getAppIncarnationId() == null
        || result.getRefreshOperationId() == null) {
      return;
    }
    provider.onMetadataRefreshRequested(new MetadataRefreshRequestedEvent(
        result.getAppName(),
        result.getRuntimeGeneration(),
        new MetadataRefreshProtocol.HelperIdentity(
            result.getHelperInstanceId(),
            result.getHelperStartedAtEpochMillis().longValue(),
            result.getAppIncarnationId()
        ),
        result.getRefreshOperationId(),
        runtimeTarget(result.getAppName())
    ));
  }

  private void online(String appName, String helperInstanceId, long startedAtEpochMillis) {
    provider.onHelperRuntimeActivated(new HelperRuntimeActivatedEvent(
        appName,
        new MetadataRefreshProtocol.HelperIdentity(
            helperInstanceId,
            startedAtEpochMillis,
            "incarnation-" + appName
        ),
        runtimeTarget(appName)
    ));
  }

  private static HelperRuntimeTarget runtimeTarget(String appName) {
    return new HelperRuntimeTarget("DOCKER", "dev-helper", "runtime-" + appName);
  }

  private static CommandResult snapshotResult(
      String appName,
      Long runtimeGeneration,
      long revision,
      Map<String, List<Metadata>> modules
  ) {
    return snapshotResult(appName, runtimeGeneration, revision, modules, "helper-a", 1000L);
  }

  private static CommandResult snapshotResult(
      String appName,
      Long runtimeGeneration,
      long revision,
      Map<String, List<Metadata>> modules,
      String helperInstanceId,
      long helperStartedAtEpochMillis
  ) {
    return snapshotResult(
        appName,
        runtimeGeneration,
        revision,
        modules,
        helperInstanceId,
        helperStartedAtEpochMillis,
        "refresh-" + revision
    );
  }

  private static CommandResult snapshotResult(
      String appName,
      Long runtimeGeneration,
      long revision,
      Map<String, List<Metadata>> modules,
      String helperInstanceId,
      long helperStartedAtEpochMillis,
      String refreshOperationId
  ) {
    var snapshot = new MetadataRefreshSnapshot()
        .setSchemaVersion(MetadataRefreshProtocol.CURRENT_SCHEMA_VERSION)
        .setAppName(appName)
        .setRuntimeGeneration(runtimeGeneration)
        .setHelperInstanceId(helperInstanceId)
        .setHelperStartedAtEpochMillis(helperStartedAtEpochMillis)
        .setAppIncarnationId("incarnation-" + appName)
        .setRefreshOperationId(refreshOperationId)
        .setRefreshRevision(revision)
        .setCollectedAtEpochMillis(System.currentTimeMillis())
        .setModules(modules);
    return new CommandResult()
        .setAppName(appName)
        .setCommand(Commands.REFRESH_METADATA.getCommand())
        .setRuntimeGeneration(runtimeGeneration)
        .setHelperInstanceId(helperInstanceId)
        .setHelperStartedAtEpochMillis(Long.valueOf(helperStartedAtEpochMillis))
        .setAppIncarnationId("incarnation-" + appName)
        .setRefreshOperationId(refreshOperationId)
        .setSuccess(true)
        .setData(snapshot);
  }

  private static CommandResult wireSnapshotResult(boolean includeModules, Object modules, long revision) {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("schemaVersion", Integer.valueOf(MetadataRefreshProtocol.CURRENT_SCHEMA_VERSION));
    data.put("appName", "crm");
    data.put("runtimeGeneration", Long.valueOf(1L));
    data.put("helperInstanceId", "helper-a");
    data.put("helperStartedAtEpochMillis", Long.valueOf(1000L));
    data.put("appIncarnationId", "incarnation-crm");
    data.put("refreshOperationId", "refresh-" + revision);
    data.put("refreshRevision", Long.valueOf(revision));
    data.put("collectedAtEpochMillis", Long.valueOf(System.currentTimeMillis()));
    if (includeModules) {
      data.put("modules", modules);
    }
    return new CommandResult()
        .setAppName("crm")
        .setCommand(Commands.REFRESH_METADATA.getCommand())
        .setRuntimeGeneration(Long.valueOf(1L))
        .setHelperInstanceId("helper-a")
        .setHelperStartedAtEpochMillis(Long.valueOf(1000L))
        .setAppIncarnationId("incarnation-crm")
        .setRefreshOperationId("refresh-" + revision)
        .setSuccess(true)
        .setData(data);
  }

  private static Map<String, List<Metadata>> modules(Metadata... metadata) {
    var modules = new LinkedHashMap<String, List<Metadata>>();
    for (var item : metadata) {
      modules.computeIfAbsent(item.getType(), ignored -> new ArrayList<>()).add(item);
    }
    return modules;
  }

  private static Metadata metadata(String type, String name) {
    var metadata = new Metadata();
    metadata.setType(type);
    metadata.setName(name);
    return metadata;
  }
}
