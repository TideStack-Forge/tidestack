package com.ouroboros.businessmodel.action;

import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.BusinessModelCenter;
import com.ouroboros.businessmodel.security.BusinessBehaviorSecurity;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.exception.BusinessException;

/** Resolves projected relation parameters from the canonical relations declaration. */
final class BusinessRelationParameters {
  private BusinessRelationParameters() {
  }

  static Object resolve(Map<?, ?> marker, Object value) {
    var resolved = resolveDetails(marker, value);
    return resolved == null ? null : resolved.state();
  }

  record ResolvedRelation(com.ouroboros.data.model.DataModel model, String key, Object value,
                          com.ouroboros.data.record.Record state) { }

  static ResolvedRelation resolveDetails(Map<?, ?> marker, Object value) {
    if (value == null) {
      return null;
    }
    if (!(marker.get("modelFullName") instanceof String ownerName)
        || !(marker.get("relationName") instanceof String relationName)) {
      throw unavailable();
    }
    var owner = BusinessModelCenter.getBusinessModel(ownerName).orElseThrow(BusinessRelationParameters::unavailable);
    var relation = owner.getRelation(relationName).orElseThrow(BusinessRelationParameters::unavailable);
    if (!"master".equalsIgnoreCase(relation.getType()) && !"reference".equalsIgnoreCase(relation.getType())) {
      throw unavailable();
    }
    var target = relation.getRelatedModel();
    if (target == null || !Boolean.TRUE.equals(target.isEnabled())) {
      throw unavailable();
    }
    String authority = target.getFullName() + ".search";
    if (!BusinessBehaviorSecurity.hasAuthority(authority)) {
      throw unavailable();
    }
    var model = DataModelCenter.getDataModel(target.getFullName()).orElseThrow(BusinessRelationParameters::unavailable);
    String key = relation.getReferenceKey() == null || relation.getReferenceKey().isBlank() ? "id" : relation.getReferenceKey();
    var field = model.getField(key).orElseThrow(BusinessRelationParameters::unavailable);
    Object rawKey = value instanceof Map<?, ?> object ? object.get(key) : value;
    if (rawKey == null || (value instanceof Map<?, ?> object && object.get("modelFullName") != null
        && !target.getFullName().equalsIgnoreCase(String.valueOf(object.get("modelFullName"))))) {
      throw unavailable();
    }
    Object converted;
    try {
      converted = field.getValueType().convert(rawKey);
    } catch (RuntimeException invalid) {
      throw new BusinessException("业务关系对象不可用", invalid);
    }
    if (converted == null) {
      throw unavailable();
    }
    var params = BusinessBehaviorSecurity.accessControlParams(authority);
    if (!params.getFieldConstraint().allows(key)
        || params.getRowConstraint().mode() == com.ouroboros.security.authority.accesscontrol.RowConstraint.Mode.NONE) {
      throw unavailable();
    }
    Map<String, Object> identityFilter = Map.of(key, converted);
    var where = params.getFilters().isEmpty() ? identityFilter
        : Map.<String, Object>of("$and", List.of(identityFilter, params.getFilters()));
    var rows = BusinessBehaviorSecurity.withAuthority(authority, () -> model.query().where(where).clause("LIMIT", 2)
        .execute().getOrElseThrow(error -> new BusinessException("业务关系对象不可用", error)));
    if (rows.size() != 1) {
      throw unavailable();
    }
    var values = new java.util.LinkedHashMap<>(rows.get(0));
    values.keySet().removeIf(name -> !params.getFieldConstraint().allows(name));
    var state = com.ouroboros.data.record.Record.of(values);
    return new ResolvedRelation(model, key, converted, state);
  }

  private static BusinessException unavailable() {
    return new BusinessException("业务关系对象不可用");
  }
}
