package com.ouroboros.logicflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshingEvent;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceIndeterminateReason;
import com.ouroboros.logicflow.nodes.NodeReferenceKind;
import com.ouroboros.logicflow.nodes.NodeReferenceSet;

class DefaultLogicFlowProviderSnapshotTest {
  private AppLogicModelMetaProvider metaProvider;
  private DefaultLogicFlowProvider provider;

  @BeforeEach
  void setUp() {
    metaProvider = new AppLogicModelMetaProvider(() -> "", false);
    provider = new DefaultLogicFlowProvider();
  }

  @Test
  void queriesBeforeFirstRefreshReturnAnEmptyImmutableSnapshot() {
    setModelMetas(simpleMeta("loaded-flow", "loaded"));

    assertTrue(provider.getFlowModel("loaded-flow").isEmpty());
    assertTrue(provider.getFlowModelMap().isEmpty());
    assertThrows(UnsupportedOperationException.class,
        () -> provider.getFlowModelMap().put("unexpected", null));
  }

  @Test
  void refreshPublishesOnlyTheCompletedCandidateSnapshot() {
    setModelMetas(simpleMeta("shared-flow", "old"));
    provider.onModelRefreshing(new OuroborosModelRefreshingEvent(this));

    assertTrue(provider.getFlowModel("shared-flow").isEmpty());

    provider.onModelRefreshed(new OuroborosModelRefreshedEvent(this));
    FlowModel original = provider.getFlowModel("shared-flow").orElseThrow();
    Map<String, FlowModel> originalSnapshot = provider.getFlowModelMap();
    assertEquals("old", original.getDescription());
    assertDoesNotThrow(() -> original.execute());

    provider.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this));
    setModelMetas(simpleMeta("shared-flow", "new"));
    provider.onModelRefreshing(new OuroborosModelRefreshingEvent(this));

    assertSame(original, provider.getFlowModel("shared-flow").orElseThrow());
    assertSame(originalSnapshot, provider.getFlowModelMap());

    provider.onModelRefreshed(new OuroborosModelRefreshedEvent(this));
    FlowModel refreshed = provider.getFlowModel("shared-flow").orElseThrow();
    assertNotSame(original, refreshed);
    assertEquals("new", refreshed.getDescription());
    assertDoesNotThrow(() -> refreshed.execute());
    assertThrows(UnsupportedOperationException.class,
        () -> provider.getFlowModelMap().put("unexpected", refreshed));
  }

  @Test
  void metadataRefreshKeepsNewModelsHiddenUntilTheCompletedSnapshotIsPublished() {
    setModelMetas(simpleMeta("existing-flow", "existing"));
    publishPendingSnapshot();
    FlowModel existing = provider.getFlowModel("existing-flow").orElseThrow();
    Map<String, FlowModel> existingSnapshot = provider.getFlowModelMap();

    provider.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this));
    setModelMetas(
        simpleMeta("existing-flow", "updated"),
        simpleMeta("new-flow", "new")
    );

    assertSame(existing, provider.getFlowModel("existing-flow").orElseThrow());
    assertTrue(provider.getFlowModel("new-flow").isEmpty());
    assertSame(existingSnapshot, provider.getFlowModelMap());

    publishPendingSnapshot();
    assertEquals("updated", provider.getFlowModel("existing-flow").orElseThrow().getDescription());
    assertEquals("new", provider.getFlowModel("new-flow").orElseThrow().getDescription());
  }

  @Test
  void onDemandModelsUseCopyOnWriteSnapshotPublication() {
    setModelMetas(simpleMeta("existing-flow", "existing"));
    publishPendingSnapshot();
    Map<String, FlowModel> originalSnapshot = provider.getFlowModelMap();

    setModelMetas(
        simpleMeta("existing-flow", "existing"),
        simpleMeta("on-demand-flow", "on-demand")
    );

    assertTrue(provider.getFlowModel("on-demand-flow").isPresent());
    assertFalse(originalSnapshot.containsKey("on-demand-flow"));
    assertTrue(provider.getFlowModelMap().containsKey("on-demand-flow"));
    assertNotSame(originalSnapshot, provider.getFlowModelMap());
  }

  @Test
  void onDemandBatchResolvesOtherModelsAddedSinceThePublishedSnapshot() {
    setModelMetas(simpleMeta("existing-flow", "existing"));
    publishPendingSnapshot();

    FlowNodeMeta callChild = FlowNodeMeta.builder()
        .name("call-child")
        .type("callFlow")
        .extraProp("flowName", "NEW-CHILD")
        .build();
    FlowModelMeta parent = FlowModelMeta.builder()
        .name("new-parent")
        .entryNode("call-child")
        .exitNode("call-child")
        .nodes(List.of(callChild))
        .build();
    setModelMetas(
        simpleMeta("existing-flow", "existing"),
        parent,
        simpleMeta("new-child", "child")
    );

    FlowModel parentModel = provider.getFlowModel("NEW-PARENT").orElseThrow();

    assertTrue(provider.getFlowModelMap().containsKey("new-child"));
    assertTrue(parentModel.getErrors().stream()
        .noneMatch(error -> error.getMessage().contains("引用的流程不存在或未启用")));
  }

  @Test
  void failedCandidateInitializationKeepsThePreviousCompletedSnapshot() {
    setModelMetas(simpleMeta("shared-flow", "old"));
    publishPendingSnapshot();
    FlowModel original = provider.getFlowModel("shared-flow").orElseThrow();
    Map<String, FlowModel> originalSnapshot = provider.getFlowModelMap();

    FlowModelMeta broken = FlowModelMeta.builder()
        .name("shared-flow")
        .description("broken")
        .nodes(null)
        .build();
    FlowModelMeta newFlow = simpleMeta("new-flow", "new");
    provider.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this));
    setModelMetas(broken, newFlow);
    assertThrows(IllegalArgumentException.class,
        () -> provider.onModelRefreshing(new OuroborosModelRefreshingEvent(this)));
    assertSame(original, provider.getFlowModel("shared-flow").orElseThrow());
    assertSame(originalSnapshot, provider.getFlowModelMap());
    assertTrue(provider.getFlowModel("new-flow").isEmpty());

    setModelMetas(simpleMeta("shared-flow", "recovered"), newFlow);
    publishPendingSnapshot();
    assertEquals("recovered", provider.getFlowModel("shared-flow").orElseThrow().getDescription());
    assertEquals("new", provider.getFlowModel("new-flow").orElseThrow().getDescription());
  }

  @Test
  void incompleteReferenceCatalogRejectsTheCandidateAndKeepsThePreviousSnapshot() {
    var referenceSets = new AtomicReference<List<NodeReferenceSet<?>>>(List.of());
    provider = new DefaultLogicFlowProvider(referenceSets::get);
    setModelMetas(simpleMeta("shared-flow", "old"));
    publishPendingSnapshot();
    FlowModel original = provider.getFlowModel("shared-flow").orElseThrow();
    Map<String, FlowModel> originalSnapshot = provider.getFlowModelMap();

    var testKind = new NodeReferenceKind<>("test-reference", String.class);
    referenceSets.set(List.of(NodeReferenceSet.indeterminate(
        testKind,
        Map.of(),
        NodeReferenceIndeterminateReason.NOT_READY
    )));
    setModelMetas(simpleMeta("shared-flow", "new"));
    provider.onModelRefreshing(new OuroborosModelRefreshingEvent(this));

    assertThrows(IllegalStateException.class,
        () -> provider.onModelRefreshed(new OuroborosModelRefreshedEvent(this)));
    assertSame(original, provider.getFlowModel("shared-flow").orElseThrow());
    assertSame(originalSnapshot, provider.getFlowModelMap());
  }

  @Test
  void readersKeepTheOldSnapshotWhileCandidateInitializationIsBlocked() throws Exception {
    var initializationStarted = new CountDownLatch(1);
    var continueInitialization = new CountDownLatch(1);
    var block = new java.util.concurrent.atomic.AtomicBoolean();
    provider = new DefaultLogicFlowProvider(() -> {
      if (block.get()) {
        initializationStarted.countDown();
        try {
          assertTrue(continueInitialization.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(failure);
        }
      }
      return List.of();
    });
    setModelMetas(simpleMeta("shared-flow", "old"));
    publishPendingSnapshot();
    var original = provider.getFlowModel("shared-flow").orElseThrow();
    setModelMetas(simpleMeta("shared-flow", "new"));
    provider.onModelRefreshing(new OuroborosModelRefreshingEvent(this));
    block.set(true);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var refresh = executor.submit(
          () -> provider.onModelRefreshed(new OuroborosModelRefreshedEvent(this)));
      assertTrue(initializationStarted.await(5, TimeUnit.SECONDS));
      try {
        var reader = executor.submit(() -> provider.getFlowModel("shared-flow").orElseThrow());
        assertSame(original, reader.get(1, TimeUnit.SECONDS));
      } finally {
        continueInitialization.countDown();
      }
      refresh.get(5, TimeUnit.SECONDS);
      assertEquals("new", provider.getFlowModel("shared-flow").orElseThrow().getDescription());
      assertEquals("old", original.getDescription());
    } finally {
      continueInitialization.countDown();
    }
  }

  @Test
  void candidateInitializationCanResolveAChildFlowCreatedLaterInTheSnapshot() {
    FlowNodeMeta callChild = FlowNodeMeta.builder()
        .name("call-child")
        .type("callFlow")
        .extraProp("flowName", "Z-CHILD")
        .build();
    FlowModelMeta parent = FlowModelMeta.builder()
        .name("a-parent")
        .entryNode("call-child")
        .exitNode("call-child")
        .nodes(List.of(callChild))
        .build();
    FlowModelMeta child = simpleMeta("z-child", "child");
    setModelMetas(parent, child);

    publishPendingSnapshot();

    FlowModel parentModel = provider.getFlowModel("a-parent").orElseThrow();
    assertNotNull(parentModel.getNode("call-child"));
    assertTrue(parentModel.getErrors().stream()
        .noneMatch(error -> error.getMessage().contains("引用的流程不存在或未启用")));

    int nodeCount = parentModel.getNodes().size();
    provider.onModelRefreshed(new OuroborosModelRefreshedEvent(this));
    assertEquals(nodeCount, parentModel.getNodes().size());
  }

  private void publishPendingSnapshot() {
    provider.onModelRefreshing(new OuroborosModelRefreshingEvent(this));
    provider.onModelRefreshed(new OuroborosModelRefreshedEvent(this));
  }

  private void setModelMetas(FlowModelMeta... modelMetas) {
    Map<String, FlowModelMeta> updatedMetas = new TreeMap<>(String::compareToIgnoreCase);
    for (FlowModelMeta modelMeta : modelMetas) {
      updatedMetas.put(modelMeta.getName(), modelMeta);
    }
    metaProvider.modelMetas = updatedMetas;
  }

  private FlowModelMeta simpleMeta(String name, String description) {
    return FlowModelMeta.builder()
        .name(name)
        .description(description)
        .entryNode("missing-entry")
        .build();
  }

}
