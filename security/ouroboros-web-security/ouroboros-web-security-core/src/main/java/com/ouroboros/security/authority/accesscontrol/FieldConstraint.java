package com.ouroboros.security.authority.accesscontrol;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable field set, with a complement representation for ALL minus excluded fields. */
public record FieldConstraint(Mode mode, Set<String> fields, Set<String> excluded) implements java.io.Serializable {
  public enum Mode { ALL, ONLY, NONE }

  public FieldConstraint {
    Objects.requireNonNull(mode, "字段模式不能为空");
    fields = Set.copyOf(fields);
    excluded = Set.copyOf(excluded);
    if (fields.contains("*") || excluded.contains("*")
        || fields.stream().anyMatch(String::isBlank) || excluded.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("字段约束不能包含通配或空名称");
    }
    if (mode == Mode.ONLY) {
      var allowed = new LinkedHashSet<>(fields);
      allowed.removeAll(excluded);
      fields = Set.copyOf(allowed);
      excluded = Set.of();
      if (fields.isEmpty()) { mode = Mode.NONE; }
    } else if (!fields.isEmpty() || (mode == Mode.NONE && !excluded.isEmpty())) {
      throw new IllegalArgumentException("字段模式与载荷不一致");
    }
  }

  public static FieldConstraint all() { return new FieldConstraint(Mode.ALL, Set.of(), Set.of()); }
  public static FieldConstraint none() { return new FieldConstraint(Mode.NONE, Set.of(), Set.of()); }
  public static FieldConstraint only(Set<String> fields) { return new FieldConstraint(Mode.ONLY, fields, Set.of()); }

  public boolean allows(String field) {
    return mode == Mode.ALL ? !excluded.contains(field) : mode == Mode.ONLY && fields.contains(field);
  }

  public FieldConstraint and(FieldConstraint other) {
    if (mode == Mode.NONE || other.mode == Mode.NONE) { return none(); }
    if (mode == Mode.ALL && other.mode == Mode.ALL) {
      var exclusions = new LinkedHashSet<>(excluded);
      exclusions.addAll(other.excluded);
      return new FieldConstraint(Mode.ALL, Set.of(), exclusions);
    }
    Set<String> candidates = mode == Mode.ONLY ? fields : other.fields;
    return only(candidates.stream().filter(this::allows).filter(other::allows).collect(java.util.stream.Collectors.toSet()));
  }

  public FieldConstraint or(FieldConstraint other) {
    if (mode == Mode.NONE) { return other; }
    if (other.mode == Mode.NONE) { return this; }
    if (mode == Mode.ALL || other.mode == Mode.ALL) {
      var all = mode == Mode.ALL ? this : other;
      var rest = all == this ? other : this;
      var exclusions = new LinkedHashSet<>(all.excluded);
      if (rest.mode == Mode.ALL) { exclusions.retainAll(rest.excluded); }
      else { exclusions.removeAll(rest.fields); }
      return new FieldConstraint(Mode.ALL, Set.of(), exclusions);
    }
    var union = new LinkedHashSet<>(fields);
    union.addAll(other.fields);
    return only(union);
  }
}
