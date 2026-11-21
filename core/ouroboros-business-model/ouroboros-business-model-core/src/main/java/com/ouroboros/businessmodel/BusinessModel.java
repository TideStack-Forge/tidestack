package com.ouroboros.businessmodel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.businessmodel.action.BusinessActionInput;
import com.ouroboros.businessmodel.event.BusinessEventEmissionContext;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.event.BusinessEventOccurrence;
import com.ouroboros.businessmodel.event.BusinessEventSubject;
import com.ouroboros.businessmodel.validation.ValidationResult;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Maps;
import com.ouroboros.util.SPI;

public interface BusinessModel {
  default List<? extends BusinessFieldView> getFields() { return List.of(); }

  default List<? extends BusinessRelationView> getRelations() { return List.of(); }

  default Optional<? extends BusinessRelationView> getRelation(String name) {
    return getRelations().stream().filter(relation -> java.util.Objects.equals(name, relation.getName())).findFirst();
  }

  default com.ouroboros.businessmodel.behavior.BusinessModelBehavior getBehavior() {
    throw new BusinessException("当前模型类型未提供业务行为契约");
  }

  default Optional<com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta> compositeView() {
    return Optional.empty();
  }

  default Optional<com.ouroboros.businessmodel.meta.DashboardBusinessModelMeta> dashboard() {
    return Optional.empty();
  }

  /** Invokes the current published Action through the governed runtime. */
  default Object invokeAction(String actionName, BusinessActionInput<?, ?> input) {
    return runtimeAccess().actions().invoke(getFullName(), actionName, input);
  }

  /** Records a model-scoped fact in the current Action's source transaction. */
  default BusinessEventOccurrence<Map<String, Object>> emitEvent(String eventName, Object payload) {
    return emitEvent(eventName, BusinessEventSubject.model(getFullName()), payload);
  }

  default BusinessEventOccurrence<Map<String, Object>> emitEvent(
      String eventName, BusinessEventSubject subject, Object payload) {
    return emitEvent(eventName, subject, payload, BusinessEventEmissionContext.fromAction());
  }

  /** Records a fact; delivery is governed by Outbox after the source transaction commits. */
  default BusinessEventOccurrence<Map<String, Object>> emitEvent(
      String eventName, BusinessEventSubject subject, Object payload, BusinessEventEmissionContext context) {
    return runtimeAccess().events().emit(new BusinessEventIdentity(getFullName(), eventName), subject, payload, context);
  }

  private static BusinessModelRuntimeAccess runtimeAccess() {
    var providers = SPI.getCachedSortedServiceStream(BusinessModelRuntimeAccess.class).toList();
    if (providers.size() != 1) {
      throw new BusinessException("业务模型运行时访问器未注册或存在冲突");
    }
    return providers.get(0);
  }

  ValidationResult getValidationResult();

  Boolean isValid();

  ValidationResult getRelationValidationResult();

  Boolean isRelationValid();

  ModelBundle buildModelBundle();

  @SuppressWarnings("unused")
  String getFormatVersion();

  String getName();

  String getNamespace();

  String getFullName();

  String getTitle();

  String getDescription();

  String getIcon();

  Boolean isEnabled();

  Boolean isAttachment();

  Map<String, Object> getExtraProps();

  Optional<Object> getExtraProp(String key);

  default <T> Optional<T> getExtraProp(Class<T> clazz, String key) {
    return getExtraProp(key).map(v -> clazz.isInstance(v) ? clazz.cast(v) : null);
  }

  default Optional<Object> getExtraPropDeep(List<String> path) {
    var keys = path
        .stream()
        .flatMap(k -> Arrays.asList(k.split("\\.")).stream())
        .collect(Collectors.toList());
    return Maps.Hierarchical.getValue(getExtraProps(), keys);
  }

  default Optional<Object> getExtraPropDeep(String... pathKey) {
    return getExtraPropDeep(Arrays.asList(pathKey));
  }

  default <T> Optional<T> getExtraPropDeep(Class<T> clazz, String... pathKey) {
    return getExtraPropDeep(clazz, Arrays.asList(pathKey));
  }

  default <T> Optional<T> getExtraPropDeep(Class<T> clazz, List<String> path) {
    var value = getExtraPropDeep(path);
    return value
        .map(v -> {
          if (clazz.isInstance(v)) {
            return clazz.cast(v);
          }
          return null;
        });
  }
}
