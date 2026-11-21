package com.ouroboros.logicflow;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshingEvent;
import com.ouroboros.logicflow.model.FlowConnectionMeta;
import com.ouroboros.logicflow.model.FlowModelDefinitions;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowModelRefreshSupport.ApplyResult;
import com.ouroboros.logicflow.model.FlowModelSource;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;

class LogicFlowIndependentRefreshTest {
  private AppLogicModelMetaProvider metas;
  private DefaultLogicFlowProvider provider;

  @BeforeEach
  void setUp() {
    OuroborosContextRefreshLifecycle.publish(this, event -> { });
    metas = new AppLogicModelMetaProvider(() -> "", false);
    provider = new DefaultLogicFlowProvider(() -> List.of());
  }

  @Test
  void parentAndChildSwitchTogetherAndCapturedParentKeepsOldChild() {
    load(parent("parent", "child"), constant("child", "old"));
    var oldParent = provider.getFlowModel("parent").orElseThrow();
    assertTrue(oldParent.isValid(), () -> oldParent.getErrors().toString());
    assertEquals("old", oldParent.execute("input").get());
    var before = provider.publication().orElseThrow();
    var candidate = provider.prepare(List.of(override(constant("child", "new"))), Set.of("CHILD"));
    assertEquals(Set.of("parent", "child"), candidate.checkScope());
    assertTrue(candidate.report().valid(candidate.checkScope()), () -> candidate.report().errors().toString());
    assertEquals("old", provider.getFlowModel("parent").orElseThrow().execute("input").get());
    assertEquals(ApplyResult.PUBLISHED, candidate.publish(Duration.ofSeconds(1)));
    assertEquals("new", provider.getFlowModel("parent").orElseThrow().execute("input").get());
    assertEquals("old", oldParent.execute("input").get());
    var after = provider.publication().orElseThrow();
    assertNotEquals(before.id(), after.id());
    assertEquals("runtime-test", after.catalogue().origins().get("child"));
    assertEquals("app", after.catalogue().defaults().origins().get("child"));
    assertEquals(FlowModelDefinitions.write(constant("child", "new")), after.definitions().get("child"));
  }

  @Test
  void cyclesMissingReferencesAndInvalidImpactedParentsCannotPublish() {
    load(parent("parent", "child"), constant("child", "old"), constant("unrelated", "okay"));
    var before = provider.publication().orElseThrow();
    var cycle = provider.prepare(List.of(override(parent("child", "parent"))), Set.of("child"));
    assertEquals(Set.of("parent"), cycle.report().dependencies().get("child"));
    assertFalse(cycle.report().valid(cycle.checkScope()));
    assertTrue(cycle.report().errors().get("child").stream().anyMatch(message -> message.contains("环路")));
    assertEquals(ApplyResult.INVALID, cycle.publish(Duration.ZERO));
    var missing = provider.prepare(List.of(override(parent("child", "missing"))), Set.of("child"));
    assertEquals(ApplyResult.INVALID, missing.publish(Duration.ZERO));
    assertSame(before, provider.publication().orElseThrow());
    assertEquals("old", provider.getFlowModel("parent").orElseThrow().execute("input").get());
    assertEquals("ready", OuroborosContextRefreshLifecycle.withStableContext(() -> "ready"));
  }

  @Test
  void unrelatedLegacyErrorsDoNotBlockAValidManagedChangeAndRevokeUsesFrozenDefault() {
    var invalid = FlowModelMeta.builder().name("legacy-invalid").entryNode("absent").build();
    load(invalid, constant("child", "default"));
    var candidate = provider.prepare(List.of(override(constant("child", "override"))), Set.of("child"));
    assertFalse(candidate.report().errors().get("legacy-invalid").isEmpty());
    assertEquals(ApplyResult.PUBLISHED, candidate.publish(Duration.ZERO));
    metas.modelMetas = Map.of("child", constant("child", "unrefreshed-file"));
    var revoke = provider.prepare(List.of(), Set.of("child"));
    assertEquals(ApplyResult.PUBLISHED, revoke.publish(Duration.ZERO));
    assertEquals("default", provider.getFlowModel("child").orElseThrow().execute().get());
  }

  @Test
  void completeContextOrAnotherFlowPublicationInvalidatesAnOldCandidate() {
    load(constant("child", "old"));
    var first = provider.prepare(List.of(override(constant("child", "first"))), Set.of("child"));
    var second = provider.prepare(List.of(override(constant("child", "second"))), Set.of("child"));
    assertEquals(ApplyResult.PUBLISHED, second.publish(Duration.ZERO));
    assertEquals(ApplyResult.STALE, first.publish(Duration.ZERO));
    var third = provider.prepare(List.of(), Set.of("child"));
    OuroborosContextRefreshLifecycle.publish(this, event -> { });
    assertEquals(ApplyResult.STALE, third.publish(Duration.ZERO));
    assertEquals("second", provider.getFlowModel("child").orElseThrow().execute().get());
  }

  @Test
  void candidateBuildDoesNotBlockBusinessReadersAndPublicationWaitsForThem() throws Exception {
    var buildEntered = new CountDownLatch(1);
    var buildRelease = new CountDownLatch(1);
    var block = new AtomicBoolean();
    provider = new DefaultLogicFlowProvider(() -> {
      if (block.get()) {
        buildEntered.countDown();
        await(buildRelease);
      }
      return List.of();
    });
    load(constant("child", "old"));
    block.set(true);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var build = executor.submit(() -> provider.prepare(List.of(override(constant("child", "new"))), Set.of("child")));
      assertTrue(buildEntered.await(3, TimeUnit.SECONDS));
      try {
        assertEquals("old", executor.submit(() -> OuroborosContextRefreshLifecycle.withStableContext(() ->
            provider.getFlowModel("child").orElseThrow().execute().get())).get(1, TimeUnit.SECONDS));
      } finally {
        buildRelease.countDown();
      }
      var candidate = build.get(3, TimeUnit.SECONDS);
      var readerEntered = new CountDownLatch(1);
      var readerRelease = new CountDownLatch(1);
      var reader = executor.submit(() -> OuroborosContextRefreshLifecycle.withStableContext(() -> {
        readerEntered.countDown();
        await(readerRelease);
        return provider.getFlowModel("child").orElseThrow().execute().get();
      }));
      assertTrue(readerEntered.await(3, TimeUnit.SECONDS));
      try {
        assertEquals(ApplyResult.TIMED_OUT, candidate.publish(Duration.ofMillis(30)));
      } finally {
        readerRelease.countDown();
      }
      assertEquals("old", reader.get(3, TimeUnit.SECONDS));
      assertEquals(ApplyResult.PUBLISHED, candidate.publish(Duration.ofSeconds(1)));
      assertEquals("new", provider.getFlowModel("child").orElseThrow().execute().get());
    } finally {
      buildRelease.countDown();
    }
  }

  @Test
  void originalFactoryRejectsTransitiveCyclesAndProducesReverseImpactFromResolution() {
    var factory = FlowModelFactory.prepare(Map.of("a", parent("a", "B"), "b", parent("b", "C"),
        "c", parent("c", "A"), "root", parent("root", "a")));
    var models = factory.initialize(NodeReferenceCatalog.empty());
    assertEquals(Set.of("a", "b", "c", "root"), factory.report().impactedBy(Set.of("B")));
    assertFalse(models.get("a").isValid());
    assertTrue(models.get("a").execute("input").isFailure());
    assertEquals(Set.of("a", "b", "c"), factory.report().dependenciesOf(Set.of("A")));
  }

  private void load(FlowModelMeta... definitions) {
    var map = new java.util.TreeMap<String, FlowModelMeta>(String::compareToIgnoreCase);
    for (var definition : definitions) { map.put(definition.getName(), definition); }
    metas.modelMetas = map;
    provider.onModelRefreshing(new OuroborosModelRefreshingEvent(this));
    provider.onModelRefreshed(new OuroborosModelRefreshedEvent(this));
  }

  private static FlowModelSource override(FlowModelMeta meta) {
    return FlowModelSource.capture("runtime-test", 200, true, true, List.of(), Map.of(meta.getName(), meta));
  }

  private static FlowModelMeta constant(String name, String value) {
    return flow(name, FlowNodeMeta.builder().name("source").type("const").extraProp("value", value).build());
  }

  private static FlowModelMeta parent(String name, String child) {
    return flow(name, FlowNodeMeta.builder().name("source").type("callFlow")
        .inputPort("input").extraProp("flowName", child).build());
  }

  private static FlowModelMeta flow(String name, FlowNodeMeta source) {
    return FlowModelMeta.builder().name(name).entryNode("source").exitNode("result")
        .nodes(List.of(source, FlowNodeMeta.builder().name("result").type("return").inputPort("input").build()))
        .connections(List.of(FlowConnectionMeta.builder().sourceNode("source").sourceOutputPort(0)
            .targetNode("result").targetInputPort(0).build())).build();
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(5, TimeUnit.SECONDS));
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(failure);
    }
  }
}
