package com.ouroboros.security.authority.accesscontrol;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ouroboros.security.exception.AuthorizationDeniedException;

/** Pure resolved values used by decisions and frozen credential boundaries. */
public record ResolvedAccessControlParams(FieldConstraint fieldConstraint, RowConstraint rowConstraint,
    java.util.Set<String> fieldUniverse)
    implements AccessControlParams, java.io.Serializable {
  public ResolvedAccessControlParams {
    java.util.Objects.requireNonNull(fieldConstraint);
    java.util.Objects.requireNonNull(rowConstraint);
    fieldUniverse = java.util.Set.copyOf(fieldUniverse);
  }

  public ResolvedAccessControlParams(FieldConstraint fields, RowConstraint rows) {
    this(fields, rows, java.util.Set.of());
  }

  public static ResolvedAccessControlParams all() {
    return new ResolvedAccessControlParams(FieldConstraint.all(), RowConstraint.all());
  }

  public static ResolvedAccessControlParams snapshot(AccessControlParams params) {
    return new ResolvedAccessControlParams(params.getFieldConstraint(), params.getRowConstraint(),
        params instanceof ResolvedAccessControlParams resolved ? resolved.fieldUniverse() : java.util.Set.of());
  }

  /** Definition fields supply the complete universe required by a legacy blacklist consumer. */
  public static ResolvedAccessControlParams snapshot(AccessControlParams params, AccessControlDefine definition) {
    var fields = definition == null ? java.util.Set.<String>of()
        : java.util.stream.Stream.concat(definition.getCommonFields().stream(), definition.getControlledFields().stream())
            .map(AccessControlDefine.Field::getName).collect(java.util.stream.Collectors.toSet());
    return new ResolvedAccessControlParams(params.getFieldConstraint(), params.getRowConstraint(), fields);
  }

  @Override
  public FieldConstraint getFieldConstraint() { return fieldConstraint; }

  @Override
  public RowConstraint getRowConstraint() { return rowConstraint; }

  @Override
  @JsonIgnore
  public List<String> getAllowedFields() {
    if (fieldConstraint.mode() == FieldConstraint.Mode.NONE) {
      throw new AuthorizationDeniedException("没有可访问的业务字段");
    }
    return fieldConstraint.mode() == FieldConstraint.Mode.ALL ? List.of("*") : List.copyOf(fieldConstraint.fields());
  }

  @Override
  @JsonIgnore
  public List<String> getDisallowedFields() {
    if (fieldConstraint.mode() == FieldConstraint.Mode.ONLY && !fieldUniverse.isEmpty()) {
      return fieldUniverse.stream().filter(field -> !fieldConstraint.allows(field)).sorted().toList();
    }
    if (fieldConstraint.mode() != FieldConstraint.Mode.ALL) {
      throw new AuthorizationDeniedException("有限字段约束必须使用允许集执行");
    }
    return List.copyOf(fieldConstraint.excluded());
  }

  @Override
  @JsonIgnore
  public Map<String, Object> getFilters() {
    if (rowConstraint.mode() == RowConstraint.Mode.NONE) {
      throw new AuthorizationDeniedException("没有可访问的数据行");
    }
    return rowConstraint.condition();
  }

  // Preserve representable properties in the permissions response. The tagged constraints
  // remain authoritative when a legacy property cannot represent a denial.
  @JsonGetter("allowedFields")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public List<String> jsonAllowedFields() {
    return fieldConstraint.mode() == FieldConstraint.Mode.NONE ? null : getAllowedFields();
  }

  @JsonGetter("disallowedFields")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public List<String> jsonDisallowedFields() {
    return fieldConstraint.mode() == FieldConstraint.Mode.ALL
        || fieldConstraint.mode() == FieldConstraint.Mode.ONLY && !fieldUniverse.isEmpty() ? getDisallowedFields() : null;
  }

  @JsonGetter("filters")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public Map<String, Object> jsonFilters() {
    return rowConstraint.mode() == RowConstraint.Mode.NONE ? null : getFilters();
  }
}
