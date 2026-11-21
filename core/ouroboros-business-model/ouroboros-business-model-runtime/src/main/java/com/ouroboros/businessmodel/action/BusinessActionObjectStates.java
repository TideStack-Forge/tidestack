package com.ouroboros.businessmodel.action;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.ouroboros.businessmodel.security.BusinessBehaviorSecurity;
import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.data.model.DataModelWriteGuard;
import com.ouroboros.data.record.Record;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

/** Invocation-local object roles, their authorized state, and deterministic precondition locks. */
final class BusinessActionObjectStates {
  private final Map<String, Role> roles = new LinkedHashMap<>();
  private String ownerModel;
  private String ownerReadAuthority;

  static BusinessActionObjectStates resolve(Object schema, Object input) {
    return resolve(schema, input, null, null);
  }

  static BusinessActionObjectStates resolve(Object schema, Object input, String ownerModel, String ownerReadAuthority) {
    var result = new BusinessActionObjectStates();
    result.ownerModel = ownerModel;
    result.ownerReadAuthority = ownerReadAuthority;
    result.collect(schema, input, "");
    return result;
  }

  Map<String, Record> values() {
    var values = new LinkedHashMap<String, Record>();
    roles.forEach((path, role) -> values.put(path, role.state));
    return values;
  }

  void requireReadableFields(BusinessActionPreconditionPlan plan) {
    for (var path : plan.accessPaths()) {
      if (path.get(0).equals("objectStates")) {
        Role role = roles.get(path.get(1));
        if (role != null) {
          requireReadableField(role.model, String.join(".", path.subList(2, path.size())), role.readAuthority);
        }
      }
    }
  }

  void lockAndRevalidate(DataModelWriteGuard guard) {
    var ordered = new TreeMap<ObjectKey, Role>(Comparator.comparing(ObjectKey::model)
        .thenComparing(ObjectKey::type).thenComparing(ObjectKey::value));
    roles.values().forEach(role -> ordered.putIfAbsent(role.identity(), role));
    var locked = new LinkedHashMap<ObjectKey, Record>();
    ordered.forEach((key, role) -> {
      guard.beforeWrite(role.model);
      locked.put(key, lock(role, false));
    });
    for (Role role : roles.values()) {
      // A second current read uses database equality (including decimal scale/collation).
      // It verifies the original relation key against the already locked primary key.
      role.state = role.relationKey == null || role.relationKey.equalsIgnoreCase("id")
          ? locked.get(role.identity()) : lock(role, true);
    }
  }

  private Record lock(Role role, boolean revalidateRelation) {
    SExpression<Boolean> where = SExpression.create(Operators.EQ, SExpression.field("id"), SExpression.constant(role.id));
    if (revalidateRelation) {
      requireReadableField(role.model, role.relationKey, role.readAuthority);
      where = SExpression.create(Operators.AND, where, SExpression.create(Operators.EQ,
          SExpression.field(role.relationKey), SExpression.constant(role.relationValue)));
    }
    var statement = QueryStatement.builder().from(role.model.getFullName(), role.model.getRawName())
        .select(role.model.getFields().stream().<SExpression<?>>map(field -> SExpression.field(field.getName())).toList())
        .where(where).build();
    var rows = BusinessBehaviorSecurity.withAuthority(role.readAuthority, scopeModel(role.model, role.readAuthority), () ->
        role.model.queryForUpdate(statement).getOrElseThrow(error -> new BusinessException("业务对象锁定失败", error)));
    if (rows.size() != 1 || !Objects.equals(canonicalId(role.id), canonicalId(primaryKey(role.model).getValueType().convert(rows.get(0).get("id"))))) {
      throw unavailable();
    }
    return rows.get(0);
  }

  private void collect(Object schemaValue, Object value, String path) {
    if (!(schemaValue instanceof Map<?, ?> schema) || value == null) { return; }
    if (schema.get("x-business-object-ref") instanceof String modelName) {
      if (!(value instanceof Map<?, ?> reference) || reference.get("id") == null
          || (reference.get("modelFullName") != null && !modelName.equalsIgnoreCase(String.valueOf(reference.get("modelFullName"))))) {
        throw unavailable();
      }
      var model = DataModelCenter.getDataModel(modelName).orElseThrow(BusinessActionObjectStates::unavailable);
      String readAuthority = ownerReadAuthority != null && modelName.equalsIgnoreCase(ownerModel)
          ? ownerReadAuthority : modelName + ".search";
      requireReadableModel(model, readAuthority);
      Object id = convert(primaryKey(model), reference.get("id"));
      var state = BusinessBehaviorSecurity.withAuthority(readAuthority, scopeModel(model, readAuthority), () -> model.get(id)
          .getOrElseThrow(error -> new BusinessException("业务对象不可用", error)));
      if (state == null) { throw unavailable(); }
      roles.put(path, new Role(model, id, null, null, state, readAuthority));
      return;
    }
    if (schema.get("x-business-relation") instanceof Map<?, ?> marker) {
      var relation = BusinessRelationParameters.resolveDetails(marker, value);
      if (relation != null) {
        String readAuthority = relation.model().getFullName() + ".search";
        requireReadableModel(relation.model(), readAuthority);
        roles.put(path, new Role(relation.model(), convert(primaryKey(relation.model()), relation.state().get("id")),
            relation.key(), relation.value(), relation.state(), readAuthority));
      }
      return;
    }
    if (schema.get("properties") instanceof Map<?, ?> properties && value instanceof Map<?, ?> data) {
      data.forEach((key, nested) -> {
        if (key instanceof String name) { collect(properties.get(name), nested, path + "/" + name.replace("~", "~0").replace("/", "~1")); }
      });
    }
    if (schema.get("items") instanceof Map<?, ?> items && value instanceof List<?> values) {
      for (int index = 0; index < values.size(); index++) { collect(items, values.get(index), path + "/" + index); }
    }
  }

  private static void requireReadableModel(DataModel model, String readAuthority) {
    if (!BusinessBehaviorSecurity.hasAuthority(readAuthority)) { throw unavailable(); }
    requireReadableField(model, "id", readAuthority);
  }

  private static void requireReadableField(DataModel model, String path, String readAuthority) {
    var params = BusinessBehaviorSecurity.accessControlParams(readAuthority);
    var fields = params.getFieldConstraint();
    if (fields.mode() == com.ouroboros.security.authority.accesscontrol.FieldConstraint.Mode.NONE
        || fields.excluded().stream().anyMatch(field -> relatedPath(field, path))
        || (fields.mode() == com.ouroboros.security.authority.accesscontrol.FieldConstraint.Mode.ONLY
            && fields.fields().stream().noneMatch(field -> path.equalsIgnoreCase(field)
                || path.toLowerCase(Locale.ROOT).startsWith(field.toLowerCase(Locale.ROOT) + ".")))) {
      throw unavailable();
    }
  }

  private static boolean relatedPath(String field, String path) {
    String first = field.toLowerCase(Locale.ROOT);
    String second = path.toLowerCase(Locale.ROOT);
    return first.equals(second) || first.startsWith(second + ".") || second.startsWith(first + ".");
  }

  private static DataModelField primaryKey(DataModel model) {
    if (model.getPrimaryKeys().size() != 1 || !model.getPrimaryKeys().get(0).getName().equalsIgnoreCase("id")) { throw unavailable(); }
    return model.getPrimaryKeys().get(0);
  }

  private static String scopeModel(DataModel model, String readAuthority) {
    return readAuthority.equalsIgnoreCase(model.getFullName() + ".search") ? null : model.getFullName();
  }

  private static Object convert(DataModelField field, Object raw) {
    try {
      Object value = raw == null ? null : field.getValueType().convert(raw);
      if (value == null) { throw unavailable(); }
      return value;
    } catch (RuntimeException error) {
      throw new BusinessException("业务对象身份不可用", error);
    }
  }

  private static BusinessException unavailable() { return new BusinessException("业务对象不可用"); }

  private static Object canonicalId(Object id) {
    return id instanceof java.math.BigDecimal decimal ? decimal.stripTrailingZeros() : id;
  }

  private record ObjectKey(String model, String type, String value) { }

  private static final class Role {
    private final DataModel model;
    private final Object id;
    private final String relationKey;
    private final Object relationValue;
    private Record state;
    private final String readAuthority;

    private Role(DataModel model, Object id, String relationKey, Object relationValue, Record state, String readAuthority) {
      this.model = model;
      this.id = id;
      this.relationKey = relationKey;
      this.relationValue = relationValue;
      this.state = state;
      this.readAuthority = readAuthority;
    }

    private ObjectKey identity() {
      Object value = canonicalId(id);
      return new ObjectKey(model.getFullName().toLowerCase(Locale.ROOT), primaryKey(model).getValueType().getType().getName(), Json.toJsonString(value));
    }
  }
}
