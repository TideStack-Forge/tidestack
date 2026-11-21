package com.ouroboros.businessmodel.plugin;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BehaviorDefinitionCopies;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;

/** One model-owned feature declaration feeds behavior construction and later data projection. */
public final class BusinessModelFeaturePlan {
  private final String featureId;
  private final String configurationPath;
  private final String writeEffect;
  private final List<ActionDefinition> actions;
  private final Consumer<ModelProjectionPipelineContext<?>> projection;

  public BusinessModelFeaturePlan(String featureId, List<ActionDefinition> actions,
                                  Consumer<ModelProjectionPipelineContext<?>> projection) {
    this(featureId, actions, projection, "plugins." + featureId);
  }

  public BusinessModelFeaturePlan(String featureId, List<ActionDefinition> actions,
      Consumer<ModelProjectionPipelineContext<?>> projection, String configurationPath) {
    this(featureId, actions, projection, configurationPath, null);
  }

  public BusinessModelFeaturePlan(String featureId, List<ActionDefinition> actions,
      Consumer<ModelProjectionPipelineContext<?>> projection, String configurationPath, String writeEffect) {
    this.featureId = Objects.requireNonNull(featureId);
    this.configurationPath = Objects.requireNonNull(configurationPath);
    this.writeEffect = writeEffect;
    this.actions = actions.stream().map(BehaviorDefinitionCopies::copyAction).toList();
    this.projection = Objects.requireNonNull(projection);
  }

  public String getConfigurationPath() { return configurationPath; }

  public String getWriteEffect() { return writeEffect; }

  public String getFeatureId() { return featureId; }

  public List<ActionDefinition> getActions() {
    return actions.stream().map(BehaviorDefinitionCopies::copyAction).toList();
  }

  public void project(ModelProjectionPipelineContext<?> context) { projection.accept(context); }
}
