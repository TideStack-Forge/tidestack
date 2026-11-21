package com.ouroboros.businessmodel.event;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.behavior.BusinessObjectReferenceSchemas;
import com.ouroboros.businessmodel.security.BusinessBehaviorSecurity;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.JsonSchemas;

/** The same complete disclosure check serves subscription consumers and each notification recipient. */
public final class DefaultBusinessEventDisclosure implements BusinessEventDisclosure {
  private final BusinessModelBehaviorProvider behaviors;

  public DefaultBusinessEventDisclosure(BusinessModelBehaviorProvider behaviors) {
    this.behaviors = Objects.requireNonNull(behaviors);
  }

  @Override
  public boolean canRead(BusinessEventOccurrence<?> occurrence) {
    return com.ouroboros.businessmodel.publication.BusinessModelPublication.withPublishedContext(() -> {
      var model = behaviors.getBusinessModelBehavior(occurrence.identity().modelFullName()).orElse(null);
      var event = model == null ? null : model.getEvents().get(occurrence.identity().eventName());
      return event != null && JsonSchemas.validate(event.getPayloadSchema(), occurrence.payloadFields())
          && BusinessObjectReferenceSchemas.unsupportedReferencePaths(occurrence.payloadSchema()).isEmpty()
          && BusinessObjectReferenceSchemas.unsupportedReferencePaths(event.getPayloadSchema()).isEmpty()
          && isDisclosureAuthorized(occurrence, event.isRequireUnfilteredModelRead())
          && areObjectReferencesReadable(occurrence.payloadSchema(), occurrence.payloadFields())
          && areObjectReferencesReadable(event.getPayloadSchema(), occurrence.payloadFields());
    });
  }

  private boolean isDisclosureAuthorized(BusinessEventOccurrence<?> occurrence, boolean currentRequiresModelRead) {
    String readAuthority = BusinessEventAuthorities.read(occurrence.identity());
    if (!BusinessBehaviorSecurity.hasAuthority(readAuthority)) { return false; }
    var readParams = BusinessBehaviorSecurity.accessControlParams(readAuthority);
    if (readParams.getRowConstraint().mode() == com.ouroboros.security.authority.accesscontrol.RowConstraint.Mode.NONE
        || anyField(occurrence.payloadFields(), "", path ->
            !readParams.getFieldConstraint().allows(path))) {
      return false;
    }
    if (occurrence.requireUnfilteredModelRead() || currentRequiresModelRead) {
      String search = occurrence.subject().modelFullName() + ".search";
      if (!BusinessBehaviorSecurity.hasAuthority(search)) { return false; }
      var params = BusinessBehaviorSecurity.accessControlParams(search);
      if (params.getRowConstraint().mode() != com.ouroboros.security.authority.accesscontrol.RowConstraint.Mode.ALL
          || !params.getFieldConstraint().allows("id")) {
        return false;
      }
    }
    if (!occurrence.subject().isInstance()) {
      return readParams.getFilters().isEmpty();
    }
    String searchAuthority = occurrence.subject().modelFullName() + ".search";
    if (!BusinessBehaviorSecurity.hasAuthority(searchAuthority)
        || anyField(occurrence.payloadFields(), "", path -> !BusinessBehaviorSecurity.accessControlParams(searchAuthority)
            .getFieldConstraint().allows(path))) {
      return false;
    }
    return isObjectReadable(occurrence.subject().modelFullName(), occurrence.subject().id(), readParams.getFilters());
  }

  private boolean anyField(Object value, String path, java.util.function.Predicate<String> restricted) {
    if (!path.isEmpty() && restricted.test(path)) {
      return true;
    }
    if (value instanceof Map<?, ?> map) {
      return map.entrySet().stream().anyMatch(entry -> anyField(entry.getValue(),
          path.isEmpty() ? String.valueOf(entry.getKey()) : path + "." + entry.getKey(), restricted));
    }
    if (value instanceof List<?> list) {
      return list.stream().anyMatch(item -> anyField(item, path, restricted));
    }
    return false;
  }

  private boolean areObjectReferencesReadable(Object schemaValue, Object value) {
    if (!(schemaValue instanceof Map<?, ?> schema) || value == null) {
      return true;
    }
    if (schema.get("x-business-object-ref") instanceof String modelFullName) {
      if (!(value instanceof Map<?, ?> reference) || reference.get("id") == null
          || (reference.get("modelFullName") != null
              && !modelFullName.equalsIgnoreCase(String.valueOf(reference.get("modelFullName"))))
          || !BusinessBehaviorSecurity.hasAuthority(modelFullName + ".search")) {
        return false;
      }
      var params = BusinessBehaviorSecurity.accessControlParams(modelFullName + ".search");
      if (anyField(value, "", path -> !params.getFieldConstraint().allows(path))) {
        return false;
      }
      if (!isObjectReadable(modelFullName, reference.get("id"), Map.of())) {
        return false;
      }
    }
    if (schema.get("properties") instanceof Map<?, ?> properties && value instanceof Map<?, ?> values) {
      return values.entrySet().stream().allMatch(entry -> areObjectReferencesReadable(properties.get(entry.getKey()), entry.getValue()));
    }
    if (schema.get("items") instanceof Map<?, ?> items && value instanceof List<?> values) {
      return values.stream().allMatch(item -> areObjectReferencesReadable(items, item));
    }
    return true;
  }

  private boolean isObjectReadable(String modelFullName, Object rawId, Map<String, Object> eventFilters) {
    var model = DataModelCenter.getDataModel(modelFullName).orElse(null);
    if (model == null || model.getPrimaryKeys().size() != 1
        || !"id".equalsIgnoreCase(model.getPrimaryKeys().get(0).getName())) {
      return false;
    }
    Object id;
    try {
      id = model.getPrimaryKeys().get(0).getValueType().convert(rawId);
    } catch (RuntimeException error) {
      return false;
    }
    if (id == null) {
      return false;
    }
    String searchAuthority = modelFullName + ".search";
    var params = BusinessBehaviorSecurity.accessControlParams(searchAuthority);
    if (params.getRowConstraint().mode() == com.ouroboros.security.authority.accesscontrol.RowConstraint.Mode.NONE) { return false; }
    var modelFilters = params.getFilters();
    // The native access-control plugin uses the model's read Authority. Event filters must be explicit.
    return BusinessBehaviorSecurity.withAuthority(searchAuthority, () -> {
      var query = model.query().select("id").where(Map.of("id", id));
      if (!modelFilters.isEmpty()) {
        query.andWhere(modelFilters);
      }
      if (!eventFilters.isEmpty()) {
        query.andWhere(eventFilters);
      }
      return !query.execute().getOrElseThrow(error -> new BusinessException("业务 Event 对象授权查询失败", error)).isEmpty();
    });
  }

}
