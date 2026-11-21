package com.ouroboros.logicflow;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshingEvent;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelBuildReport;
import com.ouroboros.logicflow.model.FlowModelCatalogue;
import com.ouroboros.logicflow.model.FlowModelDefinitions;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelMetaCenter;
import com.ouroboros.logicflow.model.FlowModelProvider;
import com.ouroboros.logicflow.model.FlowModelPublication;
import com.ouroboros.logicflow.model.FlowModelRefreshSupport;
import com.ouroboros.logicflow.model.FlowModelSource;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeReferenceSet;
import com.ouroboros.logicflow.nodes.NodeReferenceSetProvider;
import com.ouroboros.logicflow.support.LogicFlowServices;

@Component
public class DefaultLogicFlowProvider implements FlowModelProvider, FlowModelRefreshSupport {
  private volatile FlowModelPublication published = new FlowModelPublication("unpublished", Map.of(),
      Map.of(), new FlowModelCatalogue(List.of()), Map.of(), false);
  private final Supplier<List<NodeReferenceSet<?>>> referenceSetSupplier;
  private PendingSnapshot pendingSnapshot;
  private volatile SnapshotState snapshotState = SnapshotState.UNPUBLISHED;

  public DefaultLogicFlowProvider() {
    this(DefaultLogicFlowProvider::loadRuntimeReferenceSets);
  }

  DefaultLogicFlowProvider(Supplier<List<NodeReferenceSet<?>>> referenceSetSupplier) {
    this.referenceSetSupplier = referenceSetSupplier;
    SingletonHolder.INSTANCE = this;
  }

  @EventListener(OuroborosMetadataRefreshingEvent.class)
  @Order(100)
  public synchronized void onMetadataRefreshing(OuroborosMetadataRefreshingEvent event) {
    pendingSnapshot = null;
    snapshotState = SnapshotState.REFRESHING;
  }

  @EventListener(OuroborosModelRefreshingEvent.class)
  @Order(100)
  public void onModelRefreshing(OuroborosModelRefreshingEvent event) {
    snapshotState = SnapshotState.REFRESHING;
    try {
      var catalogue = FlowModelMetaCenter.observe();
      var factory = FlowModelFactory.prepare(FlowModelDefinitions.thaw(catalogue.definitions()));
      synchronized (this) {
        pendingSnapshot = new PendingSnapshot(factory, catalogue);
      }
    } catch (RuntimeException | Error failure) {
      synchronized (this) {
        pendingSnapshot = null;
        snapshotState = SnapshotState.FAILED;
      }
      throw failure;
    }
  }

  @EventListener(OuroborosModelRefreshedEvent.class)
  @Order(100)
  public void onModelRefreshed(OuroborosModelRefreshedEvent event) {
    final PendingSnapshot candidate;
    synchronized (this) {
      candidate = pendingSnapshot;
      pendingSnapshot = null;
    }
    if (candidate == null) {
      return;
    }
    try {
      var models = candidate.factory().initialize(buildRuntimeReferences());
      var managedNames = new LinkedHashSet<String>();
      candidate.catalogue().sources().stream().filter(FlowModelSource::runtimeOverride)
          .forEach(source -> managedNames.addAll(source.definitions().keySet()));
      var report = candidate.factory().report();
      if (!report.valid(report.checkScope(managedNames))) {
        throw new IllegalStateException("持久逻辑流覆盖及其影响范围检查失败");
      }
      var completed = FlowModelPublication.completed(models, candidate.catalogue());
      synchronized (this) {
        published = completed;
        snapshotState = SnapshotState.READY;
      }
    } catch (RuntimeException | Error failure) {
      snapshotState = SnapshotState.FAILED;
      throw failure;
    }
  }

  @Override
  public Candidate prepare(List<FlowModelSource> overrides, Set<String> changedNames) {
    return OuroborosContextRefreshLifecycle.withStableContext(() -> {
      var expected = published;
      if (snapshotState != SnapshotState.READY) {
        throw new IllegalStateException("尚无可独立刷新的完整逻辑流快照");
      }
      long epoch = OuroborosContextRefreshLifecycle.currentEpoch();
      var catalogue = expected.catalogue().withOverrides(overrides);
      if (catalogue.sources().stream().anyMatch(source -> !source.complete())) {
        throw new IllegalStateException("逻辑流来源不完整，不能构建覆盖候选");
      }
      var factory = FlowModelFactory.prepare(FlowModelDefinitions.thaw(catalogue.definitions()));
      var models = factory.initialize(buildRuntimeReferences());
      var roots = new LinkedHashSet<>(changedNames);
      overrides.forEach(source -> roots.addAll(source.definitions().keySet()));
      var scope = factory.report().checkScope(roots);
      var completed = FlowModelPublication.completed(models, catalogue);
      return newPreparedCandidate(epoch, expected, completed, factory.report(), scope);
    });
  }

  private record PreparedCandidate(long epoch, FlowModelPublication expected,
      FlowModelPublication completed, FlowModelBuildReport report, Set<String> checkScope,
      DefaultLogicFlowProvider owner) implements Candidate {
    @Override public String basePublicationId() { return expected.id(); }
    @Override public FlowModelCatalogue catalogue() { return completed.catalogue(); }
    @Override public ApplyResult publish(Duration timeout) {
      if (!report.valid(checkScope)) {
        return ApplyResult.INVALID;
      }
      return ApplyResult.valueOf(OuroborosContextRefreshLifecycle.publishIfCurrent(epoch, timeout,
          () -> owner.published == expected && owner.snapshotState == SnapshotState.READY,
          () -> owner.published = completed).name());
    }
  }

  private Candidate newPreparedCandidate(long epoch, FlowModelPublication expected,
      FlowModelPublication completed, FlowModelBuildReport report, Set<String> scope) {
    return new PreparedCandidate(epoch, expected, completed, report, scope, this);
  }

  @Override
  public Optional<FlowModel> getFlowModel(String flowName) {
    var flowModel = findFlowModel(published.models(), flowName);
    if (flowModel.isPresent() || snapshotState != SnapshotState.READY || StringUtils.isBlank(flowName)) {
      return flowModel;
    }
    return OuroborosContextRefreshLifecycle.withStableContext(() -> buildMissingFlowModels(flowName));
  }

  private synchronized Optional<FlowModel> buildMissingFlowModels(String requestedFlowName) {
    if (snapshotState != SnapshotState.READY) {
      return Optional.empty();
    }
    var previous = published;
    var catalogue = FlowModelMetaCenter.observe();
    // Pending or rejected persisted targets may only enter through a complete publication.
    if (!overrideStamps(previous.catalogue()).equals(overrideStamps(catalogue))) {
      return Optional.empty();
    }
    var currentMetas = FlowModelDefinitions.thaw(catalogue.definitions());
    var missing = new HashMap<String, com.ouroboros.logicflow.model.FlowModelMeta>();
    currentMetas.forEach((name, meta) -> {
      if (findFlowModel(previous.models(), name).isEmpty()) {
        missing.put(name, meta);
      }
    });
    if (missing.keySet().stream().noneMatch(name -> name.equalsIgnoreCase(requestedFlowName))) {
      return findFlowModel(previous.models(), requestedFlowName);
    }
    var newModels = FlowModelFactory.prepare(missing).initialize(buildRuntimeReferences(), previous.models());
    var models = new HashMap<>(previous.models());
    models.putAll(newModels);
    var definitions = new HashMap<>(previous.definitions());
    definitions.putAll(FlowModelDefinitions.freeze(missing));
    var stamps = new HashMap<>(previous.modelSourceStamps());
    missing.keySet().forEach(name -> stamps.put(name, catalogue.stamp()));
    published = new FlowModelPublication(UUID.randomUUID().toString(), models, definitions,
        previous.catalogue(), stamps, false);
    return findFlowModel(published.models(), requestedFlowName);
  }

  private static Map<String, String> overrideStamps(FlowModelCatalogue catalogue) {
    var stamps = new HashMap<String, String>();
    catalogue.sources().stream().filter(FlowModelSource::runtimeOverride)
        .forEach(source -> stamps.put(source.id(), source.stamp()));
    return stamps;
  }

  private static Optional<FlowModel> findFlowModel(Map<String, FlowModel> models, String name) {
    if (StringUtils.isBlank(name)) {
      return Optional.empty();
    }
    return models.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue).findFirst();
  }

  private NodeReferenceCatalog buildRuntimeReferences() {
    var referenceSets = referenceSetSupplier.get();
    var incompleteKindNames = referenceSets.stream().filter(set -> !set.isComplete())
        .map(set -> set.getKind().name()).sorted().toList();
    if (!incompleteKindNames.isEmpty()) {
      throw new IllegalStateException("节点引用目录不完整: " + String.join(", ", incompleteKindNames));
    }
    return NodeReferenceCatalog.of(referenceSets);
  }

  private static List<NodeReferenceSet<?>> loadRuntimeReferenceSets() {
    return LogicFlowServices.cachedSortedServiceStream(NodeReferenceSetProvider.class)
        .map(NodeReferenceSetProvider::getReferenceSet).toList();
  }

  @Override public Map<String, FlowModel> getFlowModelMap() { return published.models(); }
  @Override public Optional<FlowModelPublication> publication() { return Optional.of(published); }
  @Override public Optional<FlowModelRefreshSupport> refreshSupport() { return Optional.of(this); }

  private record PendingSnapshot(FlowModelFactory factory, FlowModelCatalogue catalogue) { }
  private enum SnapshotState { UNPUBLISHED, REFRESHING, READY, FAILED }

  @Priority(10)
  public static class SingletonHolder implements FlowModelProvider {
    static volatile DefaultLogicFlowProvider INSTANCE;
    @Override public Optional<FlowModel> getFlowModel(String name) { return INSTANCE.getFlowModel(name); }
    @Override public Map<String, FlowModel> getFlowModelMap() { return INSTANCE.getFlowModelMap(); }
    @Override public Optional<FlowModelPublication> publication() {
      return Optional.ofNullable(INSTANCE).flatMap(DefaultLogicFlowProvider::publication);
    }
    @Override public Optional<FlowModelRefreshSupport> refreshSupport() {
      return Optional.ofNullable(INSTANCE).map(instance -> instance);
    }
  }
}
