package com.ouroboros.businessmodel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ouroboros.model.annotation.LowCodeModel;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.EventDefinition;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.projection.DefaultModelBundleProjector;
import com.ouroboros.businessmodel.projection.custom.CustomConfig;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.businessmodel.projection.ModelBundleProjector;
import com.ouroboros.businessmodel.validation.DefaultModelValidator;
import com.ouroboros.businessmodel.validation.ModelValidator;
import com.ouroboros.businessmodel.validation.ValidationResult;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineError;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineRelationError;

@LowCodeModel
public class DefaultBusinessModel extends AbstractBusinessModel<DefaultBusinessModelMeta, DefaultBusinessModel> {
  private ModelBundle modelBundle;
  private ValidationResult relationValidationResult;
  private final BusinessModelBehavior behavior;
  private final List<CustomConfig> customConfigs;
  private final List<com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlan> featurePlans;

  public DefaultBusinessModel(DefaultBusinessModelMeta meta, BusinessModelProvider businessModelProvider) {
    super(meta, businessModelProvider);
    normalizeWorkflowBindingShape();
    this.customConfigs = CustomConfig.from(meta);
    this.featurePlans = prepareFeatures();
    this.behavior = BusinessModelBehaviorResolver.resolve(
        meta.getFullName(), meta, meta.getActions(), LegacyBusinessActionNormalizer.normalize(meta, this),
        meta.getEvents(), meta.getEventSubscriptions(), featurePlans);
  }

  public DefaultBusinessModel(DefaultBusinessModel model) {
    super(model.meta, model.businessModelProvider);
    normalizeWorkflowBindingShape();
    this.customConfigs = CustomConfig.from(meta);
    this.featurePlans = prepareFeatures();
    this.behavior = BusinessModelBehaviorResolver.resolve(
        meta.getFullName(), meta, meta.getActions(), LegacyBusinessActionNormalizer.normalize(meta, this),
        meta.getEvents(), meta.getEventSubscriptions(), featurePlans);
  }

  public List<CustomConfig> getCustomConfigs() {
    return customConfigs;
  }

  public List<com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlan> getFeaturePlans() { return featurePlans; }

  private List<com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlan> prepareFeatures() {
    var plans = new java.util.ArrayList<com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlan>();
    plans.add(com.ouroboros.businessmodel.projection.data.feature.AuditFieldsMPS.prepare(meta));
    com.ouroboros.businessmodel.plugin.BusinessModelFeaturePluginRegistry.getInstance()
        .getPluginsForModelType(meta.getModelType()).stream().map(plugin -> plugin.prepare(meta, this)).forEach(plans::add);
    return List.copyOf(plans);
  }

  @Override
  public ValidationResult getRelationValidationResult(List<String> path) {
    if (relationValidationResult == null) {
      if (path.contains(getFullName())) {
        return new ValidationResult(Collections.emptyList());
      }

      path.add(getFullName());

      List<BusinessModel> relationModels = getRelations().stream()
          .flatMap(relation -> businessModelProvider.getBusinessModel(relation.getRelatedModelName())
              .map(Stream::of)
              .orElseGet(Stream::empty))
          .filter(AbstractBusinessModel.class::isInstance)
          .map(AbstractBusinessModel.class::cast)
          .filter(model -> model.isEnabled() && model.isValid() && model.getRelationValidationResult(path).isValid())
          .collect(Collectors.toList());

      List<BusinessModelRelation> invalidRelations = getRelations()
          .stream()
          .filter(relation -> relationModels.stream()
              .noneMatch(m -> m.getFullName().equalsIgnoreCase(relation.getRelatedModelName())))
          .collect(Collectors.toList());

      BusinessDefineError exception = new BusinessDefineRelationError(String.format(
          "关联模型定义错误：关联模型不存在、未启用或定义存在问题: %s",
          invalidRelations.stream().map(BusinessModelRelation::getRelatedModelName).collect(Collectors.joining(","))
      ));

      relationValidationResult = new ValidationResult(
          !invalidRelations.isEmpty()
              ? Collections.singletonList(exception)
              : Collections.emptyList()
      );
    }

    return relationValidationResult;
  }

  @Override
  protected ModelBundleProjector<DefaultBusinessModel> getProjector() {
    return new DefaultModelBundleProjector();
  }

  @Override
  protected ModelValidator<DefaultBusinessModel> getValidator() {
    return new DefaultModelValidator();
  }

  @Override
  public ModelBundle buildModelBundle() {
    this.modelBundle = super.buildModelBundle();
    return modelBundle;
  }

  public Integer getOrder() {
    return getExtraProp(Integer.class, "order").orElse(0);
  }

  public List<BusinessModelField> getFields() {
    return meta.getFields().stream()
        .map(fieldMeta -> new BusinessModelField(this, fieldMeta))
        .collect(Collectors.toList());
  }

  public List<BusinessModelRelation> getRelations() {
    return meta.getRelations().stream()
        .map(relationMeta -> new BusinessModelRelation(this, relationMeta, businessModelProvider))
        .collect(Collectors.toList());
  }

  public List<ActionDefinition> getActions() {
    return behavior.getActionDefinitions();
  }

  public List<EventDefinition> getEvents() {
    return behavior.getEventDefinitions();
  }

  public List<EventSubscriptionDefinition> getEventSubscriptions() {
    return behavior.getEventSubscriptionDefinitions();
  }

  public BusinessModelBehavior getBehavior() {
    return behavior;
  }

  public BusinessModelProvider getBusinessModelProvider() {
    return businessModelProvider;
  }

  public Optional<BusinessModelField> getField(String name) {
    if (name == null) {
      return Optional.empty();
    }
    return this.getFields().stream().filter(field -> field.getName().equals(name)).findFirst();
  }

  public Optional<BusinessModelRelation> getRelation(String name) {
    if (name == null) {
      return Optional.empty();
    }
    return this.getRelations().stream().filter(relation -> relation.getName().equals(name)).findFirst();
  }

  private void normalizeWorkflowBindingShape() {
    Object workflowValue = meta.getExtraProp("workflow").orElse(null);
    if (!(workflowValue instanceof Map)) {
      return;
    }

    Map<?, ?> rawWorkflow = (Map<?, ?>) workflowValue;
    if (rawWorkflow.containsKey("bindings")) {
      return;
    }
    if (!rawWorkflow.containsKey("processId")
        && !rawWorkflow.containsKey("statusField")
        && !rawWorkflow.containsKey("statusMap")) {
      return;
    }

    var workflow = new LinkedHashMap<String, Object>();
    rawWorkflow.forEach((key, value) -> {
      if (key instanceof String) {
        workflow.put((String) key, value);
      }
    });

    var binding = new LinkedHashMap<String, Object>();
    binding.put("key", "default");
    binding.put("processId", workflow.remove("processId"));
    binding.put("statusField", workflow.remove("statusField"));
    binding.put("statusMap", workflow.remove("statusMap"));
    binding.put("trigger", "manual");
    workflow.put("bindings", Collections.singletonList(binding));
    meta.setExtraProp("workflow", workflow);
  }

  public List<DefaultBusinessModel> getReferencedModels() {
    return businessModelProvider.getBusinessModelMap().values().stream()
        .filter(DefaultBusinessModel.class::isInstance)
        .map(DefaultBusinessModel.class::cast)
        .filter(model -> model.getRelations()
            .stream()
            .anyMatch(relation -> this.getFullName().equalsIgnoreCase(relation.getRelatedModel().getFullName())))
        .collect(Collectors.toList());
  }

  public static class Builder implements BusinessModelBuilder<DefaultBusinessModelMeta, DefaultBusinessModel> {
    @Override
    public Boolean support(BusinessModelMeta businessModelMeta) {
      return businessModelMeta instanceof DefaultBusinessModelMeta;
    }

    @Override
    public DefaultBusinessModel build(DefaultBusinessModelMeta businessModelMeta, BusinessModelProvider businessModelProvider) {
      return new DefaultBusinessModel(businessModelMeta, businessModelProvider);
    }
  }
}
