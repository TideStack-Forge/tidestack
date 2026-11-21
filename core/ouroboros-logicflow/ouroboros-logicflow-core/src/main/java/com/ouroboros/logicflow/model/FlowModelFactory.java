package com.ouroboros.logicflow.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeReferenceSet;

public final class FlowModelFactory {
  private final Map<String, DefaultFlowModel> preparedModels;
  private boolean initialized;
  private FlowModelBuildReport buildReport;

  private FlowModelFactory(Map<String, FlowModelMeta> modelMetas) {
    var models = new LinkedHashMap<String, DefaultFlowModel>();
    modelMetas.forEach((name, meta) -> models.put(
        Objects.requireNonNull(name, "flow model name"),
        new DefaultFlowModel(Objects.requireNonNull(meta, "flow model metadata"))
    ));
    preparedModels = Collections.unmodifiableMap(models);
  }

  public static FlowModelFactory prepare(Map<String, FlowModelMeta> modelMetas) {
    return new FlowModelFactory(Objects.requireNonNull(modelMetas, "modelMetas"));
  }

  public static DefaultFlowModel build(FlowModelMeta modelMeta, NodeReferenceCatalog baseReferences) {
    return build(modelMeta, baseReferences, Collections.emptyMap());
  }

  public static DefaultFlowModel build(
      FlowModelMeta modelMeta,
      NodeReferenceCatalog baseReferences,
      Map<String, ? extends FlowModel> existingFlowModels
  ) {
    Objects.requireNonNull(modelMeta, "modelMeta");
    var modelName = modelMeta.getName() == null ? "" : modelMeta.getName();
    var factory = prepare(Map.of(modelName, modelMeta));
    return (DefaultFlowModel) factory.initialize(baseReferences, existingFlowModels).get(modelName);
  }

  public Map<String, FlowModel> initialize(NodeReferenceCatalog baseReferences) {
    return initialize(baseReferences, Collections.emptyMap());
  }

  public Map<String, FlowModel> initialize(
      NodeReferenceCatalog baseReferences,
      Map<String, ? extends FlowModel> existingFlowModels
  ) {
    Objects.requireNonNull(existingFlowModels, "existingFlowModels");
    return initialize(
        baseReferences,
        NodeReferenceSet.lazyComplete(
            FlowModelReferences.KIND,
            name -> findEnabledFlowModel(existingFlowModels, name),
            String::compareToIgnoreCase
        )
    );
  }

  private static FlowModel findEnabledFlowModel(Map<String, ? extends FlowModel> models, String name) {
    return models.entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue)
        .filter(model -> model != null && model.isEnabled())
        .findFirst()
        .orElse(null);
  }

  public Map<String, FlowModel> initialize(
      NodeReferenceCatalog baseReferences,
      NodeReferenceSet<FlowModel> existingFlowModels
  ) {
    if (initialized) {
      throw new IllegalStateException("FlowModelFactory 已初始化");
    }
    Objects.requireNonNull(baseReferences, "baseReferences");
    Objects.requireNonNull(existingFlowModels, "existingFlowModels");
    initialized = true;

    var enabledModels = new LinkedHashMap<String, FlowModel>();
    var disabledModelNames = new ArrayList<String>();
    preparedModels.forEach((name, model) -> {
      if (model.isEnabled()) {
        enabledModels.put(name, model);
      } else {
        disabledModelNames.add(name);
      }
    });

    var references = baseReferences.with(existingFlowModels.overlay(enabledModels, disabledModelNames));
    var dependencies = new LinkedHashMap<String, java.util.Set<String>>();
    preparedModels.forEach((name, model) -> {
      var targets = new java.util.LinkedHashSet<String>();
      dependencies.put(FlowModelDefinitions.nameKey(name), targets);
      model.initialize(references.observing((kind, target) -> {
        if (FlowModelReferences.KIND.equals(kind) && target != null) {
          targets.add(FlowModelDefinitions.nameKey(target));
        }
      }));
    });
    preparedModels.forEach((name, model) -> {
      var key = FlowModelDefinitions.nameKey(name);
      boolean cyclic = dependencies.get(key).stream().anyMatch(target ->
          FlowModelBuildReport.closure(java.util.Set.of(target), dependencies).contains(key));
      if (cyclic) {
        model.rejectReferenceCycle();
      }
    });
    var errors = new LinkedHashMap<String, java.util.List<String>>();
    preparedModels.forEach((name, model) -> errors.put(FlowModelDefinitions.nameKey(name),
        model.getErrors().stream().map(Throwable::getMessage).toList()));
    buildReport = new FlowModelBuildReport(dependencies, errors);
    return Collections.unmodifiableMap(new LinkedHashMap<>(preparedModels));
  }
  public FlowModelBuildReport report() {
    if (buildReport == null) {
      throw new IllegalStateException("构建尚未完成");
    }
    return buildReport;
  }

}
