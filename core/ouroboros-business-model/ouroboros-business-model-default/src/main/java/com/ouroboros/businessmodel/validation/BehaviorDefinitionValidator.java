package com.ouroboros.businessmodel.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.EventDefinition;
import com.ouroboros.businessmodel.behavior.EventReference;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessObjectReferenceSchemas;
import com.ouroboros.businessmodel.event.BusinessEventAuthorities;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineFeatureError;
import com.ouroboros.util.JsonSchemas;

/**
 * Validates the dynamic Action/Event contract before projection.
 */
public class BehaviorDefinitionValidator implements ModelDetailValidator<DefaultBusinessModel> {
  private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^{}]+)}");

  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    DefaultBusinessModel model = context.getModel();
    model.getBehavior().getDefinitionConflicts().forEach(code -> addError(context, code));
    validateActions(context, model);
    validateCustomHttpMappings(context, model);
    Set<String> eventNames = validateEvents(context, model);
    validateAuthorityNames(context, model);
    validateSubscriptions(context, model);
    validateEmittedEvents(context, model, eventNames);
  }

  private void validateCustomHttpMappings(ValidationContext<DefaultBusinessModel> context, DefaultBusinessModel model) {
    for (var config : model.getCustomConfigs()) {
      if (!(config.getExtraProps().get("api") instanceof Map<?, ?>)) { continue; }
      String name = config.getName();
      config.getApi().definitionErrors().forEach(error -> addError(context, "custom." + name + ".api." + error));
      if (config.getApi().isActionReference()) {
        if (!model.getBehavior().getActions().containsKey(config.getApi().getActionName(name))) {
          addError(context, "custom." + name + ".api.action-not-found");
        }
        for (String key : List.of("inputSchema", "outputSchema", "binding", "precondition", "authorization", "readOnly", "emits")) {
          if (config.getExtraProps().containsKey(key)) {
            addError(context, "custom." + name + "." + key + "-inline-definition-not-allowed");
          }
        }
      }
      if (!config.getApi().getIsEnabled() || !config.getApi().isActionBindingEnabled()) { continue; }
      var mapping = config.getApi().getActionInputMapping();
      if (mapping.isEmpty()) {
        addError(context, "actions." + name + ".api.actionInputMapping-required");
        continue;
      }
      try {
        com.ouroboros.webapi.BusinessActionWebApiContract.validateMapping(mapping.get(), config.getApi().getUrl(),
            config.getApi().getActionInputMappingFormatVersion());
      } catch (IllegalArgumentException invalid) {
        addError(context, "actions." + name + ".api.actionInputMapping-invalid");
      }
    }
  }

  private void validateAuthorityNames(ValidationContext<DefaultBusinessModel> context, DefaultBusinessModel model) {
    Set<String> reservedAuthorities = Set.of(identityKey(com.ouroboros.businessmodel.behavior.BusinessBehaviorAuthorities.inspect(model.getFullName())),
        identityKey(BusinessEventAuthorities.INSPECT_DELIVERIES),
        identityKey(BusinessEventAuthorities.REDRIVE_DELIVERIES));
    Set<String> eventAuthorities = new HashSet<>();
    model.getEvents().stream().filter(event -> event != null && StringUtils.isNotBlank(event.getName())).forEach(event -> {
      var identity = new BusinessEventIdentity(model.getFullName(), event.getName());
      eventAuthorities.add(identityKey(BusinessEventAuthorities.emit(identity)));
      eventAuthorities.add(identityKey(BusinessEventAuthorities.read(identity)));
    });
    model.getActions().stream().filter(action -> action != null && StringUtils.isNotBlank(action.getName())).forEach(action -> {
      if (reservedAuthorities.contains(identityKey(model.getFullName() + "." + action.getName()))) {
        addError(context, "actions." + action.getName() + ".reserved-operation-authority-conflict");
      }
      if (eventAuthorities.contains(identityKey(model.getFullName() + "." + action.getName()))) {
        addError(context, "actions." + action.getName() + ".event-authority-conflict");
      }
    });
  }

  private void validateActions(ValidationContext<DefaultBusinessModel> context,
                               DefaultBusinessModel model) {
    Set<String> names = new HashSet<>();
    for (ActionDefinition action : model.getActions()) {
      if (action == null || StringUtils.isBlank(action.getName())) {
        addError(context, "actions.name-required");
        continue;
      }
      if (!names.add(identityKey(action.getName()))) {
        addError(context, "actions." + action.getName() + ".duplicate");
      }
      if (action.getAuthorization() != null) {
        addError(context, "actions." + action.getName() + ".authorization-unsupported-use-authority");
      }
      if (!model.getBehavior().preconditionPlan(action.getName()).isValid()) {
        addError(context, "actions." + action.getName() + ".precondition-invalid");
      }
      validateBinding(context, action);
      try {
        com.ouroboros.businessmodel.BusinessActionAnnotationCatalog.validateBinding(action);
      } catch (IllegalStateException invalid) {
        addError(context, "actions." + action.getName() + ".binding.signature-invalid: " + invalid.getMessage());
      }
      if (action.getInputSchema().get("properties") instanceof Map<?, ?> partitions
          && partitions.keySet().stream().anyMatch(key -> !"params".equals(key) && !"data".equals(key))) {
        addError(context, "actions." + action.getName() + ".inputSchema.partitions-invalid");
      }
      validateObjectRootSchema(context, "actions." + action.getName() + ".inputSchema", action.getInputSchema());
      validateSchemaObjectReferences(context, model, "actions." + action.getName() + ".inputSchema",
          action.getInputSchema());
      validateInputIdentities(context, "actions." + action.getName() + ".inputSchema", action.getInputSchema(), action.getInputSchema());
      validateSchema(context, "actions." + action.getName() + ".outputSchema", action.getOutputSchema());
    }
  }

  private void validateInputIdentities(ValidationContext<DefaultBusinessModel> context, String path,
                                       Map<String, Object> root, Object value) {
    if (value instanceof Map<?, ?> schema) {
      if (schema.containsKey("x-business-object-identity")
          && !BusinessObjectReferenceSchemas.isObjectIdentityPointer(root, schema.get("x-business-object-identity"))) {
        addError(context, path + ".x-business-object-identity-invalid");
      }
      schema.forEach((key, nested) -> validateInputIdentities(context, path + "." + key, root, nested));
    } else if (value instanceof List<?> values) {
      values.forEach(nested -> validateInputIdentities(context, path, root, nested));
    }
  }

  private void validateSchemaObjectReferences(ValidationContext<DefaultBusinessModel> context,
                                             DefaultBusinessModel model,
                                             String path,
                                             Object schemaValue) {
    BusinessObjectReferenceSchemas.unsupportedReferencePaths(schemaValue).forEach(unsupported ->
        addError(context, path + unsupported + ".object-reference-layout-unsupported"));
    validateReferencedModels(context, model, path, schemaValue);
  }

  private void validateReferencedModels(ValidationContext<DefaultBusinessModel> context,
                                        DefaultBusinessModel model, String path, Object schemaValue) {
    if (schemaValue instanceof java.util.Map<?, ?> schema) {
      if (schema.containsKey("x-business-relation")) {
        String relationPath = path + ".x-business-relation";
        if (!path.startsWith("actions.") || !(schema.get("x-business-relation") instanceof Map<?, ?> relation)
            || !(relation.get("modelFullName") instanceof String ownerName)
            || !(relation.get("relationName") instanceof String relationName)) {
          addError(context, relationPath + "-invalid-action-relation");
        } else {
          resolveModel(context, model, ownerName, relationPath + "-model-not-found", relationPath + "-model-unsupported")
              .ifPresent(owner -> {
                var resolved = owner.getRelation(relationName);
                if (resolved.isEmpty() || (!"master".equalsIgnoreCase(resolved.get().getType())
                    && !"reference".equalsIgnoreCase(resolved.get().getType()))) {
                  addError(context, relationPath + "-relation-unavailable");
                }
              });
        }
      }
      if (schema.containsKey("x-business-object-ref")) {
        Object modelNameValue = schema.get("x-business-object-ref");
        String referencePath = path + ".x-business-object-ref";
        if (!(modelNameValue instanceof String modelFullName) || StringUtils.isBlank(modelFullName)) {
          addError(context, referencePath + "-invalid");
        } else {
          resolveModel(context, model, modelFullName,
              referencePath + "-model-not-found", referencePath + "-model-does-not-support-business-model")
              .ifPresent(referencedModel -> {
                List<BusinessModelField> primaryKeys = referencedModel.getFields().stream()
                    .filter(field -> Boolean.TRUE.equals(field.getIsPrimaryKey()))
                    .toList();
                if (primaryKeys.size() != 1 || !sameName(primaryKeys.get(0).getName(), "id")) {
                  addError(context, referencePath + "-requires-single-id-primary-key");
                }
              });
        }
      }
      schema.forEach((key, value) -> {
        if (key instanceof String name) {
          validateReferencedModels(context, model, path + "." + name, value);
        }
      });
    } else if (schemaValue instanceof List<?> schemaValues) {
      for (int index = 0; index < schemaValues.size(); index++) {
        validateReferencedModels(context, model, path + "[" + index + "]", schemaValues.get(index));
      }
    }
  }

  private void validateBinding(ValidationContext<DefaultBusinessModel> context, ActionDefinition action) {
    ActionBinding binding = action.getBinding();
    if (binding == null) {
      if (Boolean.TRUE.equals(action.getIsEnabled())) {
        addError(context, "actions." + action.getName() + ".binding-required");
      }
      return;
    }
    boolean logicFlow = "logicflow".equalsIgnoreCase(binding.getKind());
    boolean handler = "handler".equalsIgnoreCase(binding.getKind());
    if (!logicFlow && !handler) {
      addError(context, "actions." + action.getName() + ".binding.kind-invalid");
      return;
    }
    if (logicFlow && (StringUtils.isBlank(binding.getFlowName()) || StringUtils.isNotBlank(binding.getHandlerName()))) {
      addError(context, "actions." + action.getName() + ".binding.flow-invalid");
    }
    if (handler && (StringUtils.isBlank(binding.getHandlerName()) || StringUtils.isNotBlank(binding.getFlowName()))) {
      addError(context, "actions." + action.getName() + ".binding.handler-invalid");
    }
  }

  private Set<String> validateEvents(ValidationContext<DefaultBusinessModel> context,
                                     DefaultBusinessModel model) {
    Set<String> names = new HashSet<>();
    for (EventDefinition event : model.getEvents()) {
      if (event == null || StringUtils.isBlank(event.getName())) {
        addError(context, "events.name-required");
        continue;
      }
      if (!names.add(identityKey(event.getName()))) {
        addError(context, "events." + event.getName() + ".duplicate");
      }
      if (StringUtils.isBlank(event.getOccurrenceCondition())) {
        addError(context, "events." + event.getName() + ".occurrenceCondition-required");
      }
      if (StringUtils.isBlank(event.getSubject())
          || !("model".equalsIgnoreCase(event.getSubject()) || "instance".equalsIgnoreCase(event.getSubject()))) {
        addError(context, "events." + event.getName() + ".subject-invalid");
      }
      if (event.getAllowedProducerKinds().isEmpty()) {
        addError(context, "events." + event.getName() + ".allowedProducerKinds-required");
      }
      if (event.getAllowedProducerKinds().stream().anyMatch(StringUtils::isBlank)) {
        addError(context, "events." + event.getName() + ".allowedProducerKinds-invalid");
      }
      if ("instance".equalsIgnoreCase(event.getSubject())
          && (model.getFields().stream().filter(field -> field.getIsPrimaryKey()
              && "id".equalsIgnoreCase(field.getName())).count() != 1
              || model.getFields().stream().filter(field -> field.getIsPrimaryKey()).count() != 1)) {
        addError(context, "events." + event.getName() + ".instance-subject-requires-single-id-primary-key");
      }
      validateObjectRootSchema(context, "events." + event.getName() + ".payloadSchema", event.getPayloadSchema());
      validateSchemaObjectReferences(context, model, "events." + event.getName() + ".payloadSchema", event.getPayloadSchema());
    }
    return names;
  }

  private void validateSubscriptions(ValidationContext<DefaultBusinessModel> context,
                                     DefaultBusinessModel model) {
    Set<String> names = new HashSet<>();
    for (EventSubscriptionDefinition subscription : model.getEventSubscriptions()) {
      if (subscription == null || StringUtils.isBlank(subscription.getName())) {
        addError(context, "eventSubscriptions.name-required");
        continue;
      }
      if (!names.add(identityKey(subscription.getName()))) {
        addError(context, "eventSubscriptions." + subscription.getName() + ".duplicate");
      }
      if (subscription.getSourceEvent() == null || StringUtils.isBlank(subscription.getSourceEvent().getModelFullName())
          || StringUtils.isBlank(subscription.getSourceEvent().getEventName())) {
        addError(context, "eventSubscriptions." + subscription.getName() + ".source-required");
        continue;
      }
      if (subscription.getExecutionMode() == null) {
        addError(context, "eventSubscriptions." + subscription.getName() + ".executionMode-required");
      }
      var target = subscription.getTarget();
      if (target == null) {
        addError(context, "eventSubscriptions." + subscription.getName() + ".target-required");
        continue;
      }
      boolean actionTarget = "action".equals(target.getKind());
      boolean listenerTarget = "listener".equals(target.getKind());
      if (!actionTarget && !listenerTarget || actionTarget && (StringUtils.isBlank(target.getModelFullName())
          || StringUtils.isBlank(target.getActionName()) || target.getListenerName() != null || target.getConfig() != null)
          || listenerTarget && (StringUtils.isBlank(target.getListenerName())
              || target.getModelFullName() != null || target.getActionName() != null)) {
        addError(context, "eventSubscriptions." + subscription.getName() + ".target-exclusive");
      }
      if (actionTarget && subscription.getExecutionMode() == com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_INDEPENDENT) {
        addError(context, "eventSubscriptions." + subscription.getName() + ".executionMode-incompatible");
      }
      if (subscription.getFilter() != null && !(subscription.getFilter() instanceof Boolean)
          && !(subscription.getFilter() instanceof String expression
              && com.ouroboros.expression.EL.validatePredicate(expression).isRight())) {
        addError(context, "eventSubscriptions." + subscription.getName() + ".filter-invalid");
      }
      validateSubscriptionReferences(context, model, subscription);
    }
  }

  private void validateSubscriptionReferences(ValidationContext<DefaultBusinessModel> context,
                                             DefaultBusinessModel model,
                                             EventSubscriptionDefinition subscription) {
    String path = "eventSubscriptions." + subscription.getName();
    Optional<DefaultBusinessModel> sourceModel = Optional.empty();
    EventDefinition sourceEvent = null;
    if (StringUtils.isNotBlank(subscription.getSourceEvent().getModelFullName())
        && StringUtils.isNotBlank(subscription.getSourceEvent().getEventName())) {
      sourceModel = resolveModel(context, model, subscription.getSourceEvent().getModelFullName(),
          path + ".source-event-model-not-found", path + ".source-event-model-does-not-support-behavior");
      if (sourceModel.isPresent()) {
        sourceEvent = sourceModel.get().getEvents().stream()
            .filter(event -> sameName(event.getName(), subscription.getSourceEvent().getEventName()))
            .findFirst().orElse(null);
        if (sourceEvent == null) {
          addError(context, path + ".source-event-not-found");
        }
      }
    }
    if (StringUtils.isNotBlank(subscription.getTarget().getModelFullName())
        && StringUtils.isNotBlank(subscription.getTarget().getActionName())) {
      resolveModel(context, model, subscription.getTarget().getModelFullName(),
          path + ".target-action-model-not-found", path + ".target-action-model-does-not-support-behavior")
          .ifPresent(targetModel -> {
            ActionDefinition targetAction = targetModel.getActions().stream()
                .filter(action -> sameName(action.getName(), subscription.getTarget().getActionName()))
                .findFirst().orElse(null);
            if (targetAction == null) {
              addError(context, path + ".target-action-not-found");
            } else if (com.ouroboros.businessmodel.action.BusinessActionOutputs.isFile(targetAction.getOutputSchema())) {
              addError(context, path + ".target-action-file-output-not-consumable");
            }

          });
    }
  }

  private void validateEmittedEvents(ValidationContext<DefaultBusinessModel> context,
                                     DefaultBusinessModel model,
                                     Set<String> eventNames) {
    for (ActionDefinition action : model.getActions()) {
      if (action == null) {
        continue;
      }
      List<EventReference> emits = action.getEmits();
      for (EventReference event : emits) {
        if (event == null || StringUtils.isBlank(event.getModelFullName())
            || StringUtils.isBlank(event.getEventName())) {
          addError(context, "actions." + action.getName() + ".emits.reference-invalid");
        } else if (StringUtils.equalsIgnoreCase(model.getFullName(), event.getModelFullName())) {
          if (!eventNames.contains(identityKey(event.getEventName()))) {
            addError(context, "actions." + action.getName() + ".emits.event-not-found");
          }
        } else {
          resolveModel(context, model, event.getModelFullName(),
              "actions." + action.getName() + ".emits.model-not-found",
              "actions." + action.getName() + ".emits.model-does-not-support-behavior")
              .ifPresent(eventModel -> {
                if (eventModel.getEvents().stream().noneMatch(
                    definition -> sameName(definition.getName(), event.getEventName()))) {
                  addError(context, "actions." + action.getName() + ".emits.event-not-found");
                }
              });
        }
      }
    }
  }

  private Optional<DefaultBusinessModel> resolveModel(ValidationContext<DefaultBusinessModel> context,
                                                      DefaultBusinessModel model,
                                                      String modelFullName,
                                                      String notFoundCode,
                                                      String unsupportedCode) {
    Optional<BusinessModel> resolved = StringUtils.equalsIgnoreCase(model.getFullName(), modelFullName)
        ? Optional.of(model)
        : Optional.ofNullable(model.getBusinessModelProvider())
            .flatMap(provider -> provider.getBusinessModel(modelFullName));
    if (resolved.isEmpty()) {
      addError(context, notFoundCode);
      return Optional.empty();
    }
    if (!(resolved.get() instanceof DefaultBusinessModel defaultModel)) {
      addError(context, unsupportedCode);
      return Optional.empty();
    }
    return Optional.of(defaultModel);
  }

  private boolean validateSchema(ValidationContext<DefaultBusinessModel> context,
                              String path,
                              java.util.Map<String, Object> schema) {
    if (!JsonSchemas.isValidSchema(schema)) {
      addError(context, path + "-invalid");
      return false;
    }
    return true;
  }

  private void validateObjectRootSchema(ValidationContext<DefaultBusinessModel> context,
                                        String path, Map<String, Object> schema) {
    if (!validateSchema(context, path, schema)) {
      return;
    }
    if (!ObjectRootSchemaSupport.allowsObject(schema)) {
      addError(context, path + "-requires-object");
    }
  }

  private static String identityKey(String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  private static boolean sameName(String left, String right) {
    return left != null && right != null && left.equalsIgnoreCase(right);
  }

  private void addError(ValidationContext<DefaultBusinessModel> context, String code) {
    context.addError(new BusinessDefineFeatureError("业务行为定义错误：" + code));
  }
}
