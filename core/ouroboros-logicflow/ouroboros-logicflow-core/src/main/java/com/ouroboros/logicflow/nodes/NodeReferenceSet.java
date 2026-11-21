package com.ouroboros.logicflow.nodes;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

public final class NodeReferenceSet<T> {
  private final NodeReferenceKind<T> kind;
  private final Function<String, NodeReferenceResolution<T>> resolver;
  private final Comparator<String> nameComparator;
  private final boolean complete;
  private final NodeReferenceIndeterminateReason indeterminateReason;

  private NodeReferenceSet(
      NodeReferenceKind<T> kind,
      Function<String, NodeReferenceResolution<T>> resolver,
      Comparator<String> nameComparator,
      boolean complete,
      NodeReferenceIndeterminateReason indeterminateReason
  ) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.resolver = Objects.requireNonNull(resolver, "resolver");
    this.nameComparator = Objects.requireNonNull(nameComparator, "nameComparator");
    this.complete = complete;
    this.indeterminateReason = complete
        ? null
        : Objects.requireNonNull(indeterminateReason, "indeterminateReason");
  }

  public static <T> NodeReferenceSet<T> complete(
      NodeReferenceKind<T> kind,
      Map<String, ? extends T> values
  ) {
    return complete(kind, values, Comparator.naturalOrder());
  }

  public static <T> NodeReferenceSet<T> complete(
      NodeReferenceKind<T> kind,
      Map<String, ? extends T> values,
      Comparator<String> nameComparator
  ) {
    return fromMap(kind, values, nameComparator, true, null);
  }

  public static <T> NodeReferenceSet<T> lazyComplete(
      NodeReferenceKind<T> kind,
      Function<String, ? extends T> valueLookup
  ) {
    return lazyComplete(kind, valueLookup, Comparator.naturalOrder());
  }

  public static <T> NodeReferenceSet<T> lazyComplete(
      NodeReferenceKind<T> kind,
      Function<String, ? extends T> valueLookup,
      Comparator<String> nameComparator
  ) {
    Objects.requireNonNull(valueLookup, "valueLookup");
    return new NodeReferenceSet<>(
        kind,
        name -> resolveValue(kind, valueLookup.apply(name), true, null),
        nameComparator,
        true,
        null
    );
  }

  public static <T> NodeReferenceSet<T> indeterminate(
      NodeReferenceKind<T> kind,
      Map<String, ? extends T> trustedValues,
      NodeReferenceIndeterminateReason reason
  ) {
    return indeterminate(kind, trustedValues, Comparator.naturalOrder(), reason);
  }

  public static <T> NodeReferenceSet<T> indeterminate(
      NodeReferenceKind<T> kind,
      Map<String, ? extends T> trustedValues,
      Comparator<String> nameComparator,
      NodeReferenceIndeterminateReason reason
  ) {
    return fromMap(kind, trustedValues, nameComparator, false, reason);
  }

  public static <T> NodeReferenceSet<T> lazyIndeterminate(
      NodeReferenceKind<T> kind,
      Function<String, ? extends T> valueLookup,
      NodeReferenceIndeterminateReason reason
  ) {
    return lazyIndeterminate(kind, valueLookup, Comparator.naturalOrder(), reason);
  }

  public static <T> NodeReferenceSet<T> lazyIndeterminate(
      NodeReferenceKind<T> kind,
      Function<String, ? extends T> valueLookup,
      Comparator<String> nameComparator,
      NodeReferenceIndeterminateReason reason
  ) {
    Objects.requireNonNull(valueLookup, "valueLookup");
    return new NodeReferenceSet<>(
        kind,
        name -> resolveValue(kind, valueLookup.apply(name), false, reason),
        nameComparator,
        false,
        Objects.requireNonNull(reason, "reason")
    );
  }

  public static <T> NodeReferenceSet<T> lazy(
      NodeReferenceKind<T> kind,
      Function<String, NodeReferenceResolution<T>> resolver,
      boolean complete,
      NodeReferenceIndeterminateReason indeterminateReason
  ) {
    return new NodeReferenceSet<>(kind, resolver, Comparator.naturalOrder(), complete, indeterminateReason);
  }

  public static <T> NodeReferenceSet<T> lazy(
      NodeReferenceKind<T> kind,
      Function<String, NodeReferenceResolution<T>> resolver,
      Comparator<String> nameComparator,
      boolean complete,
      NodeReferenceIndeterminateReason indeterminateReason
  ) {
    return new NodeReferenceSet<>(kind, resolver, nameComparator, complete, indeterminateReason);
  }

  private static <T> NodeReferenceSet<T> fromMap(
      NodeReferenceKind<T> kind,
      Map<String, ? extends T> values,
      Comparator<String> nameComparator,
      boolean complete,
      NodeReferenceIndeterminateReason reason
  ) {
    Objects.requireNonNull(values, "values");
    Objects.requireNonNull(nameComparator, "nameComparator");
    var indexedValues = new TreeMap<String, T>(nameComparator);
    values.forEach((name, value) -> {
      var checkedName = Objects.requireNonNull(name, "reference name");
      var checkedValue = kind.valueType().cast(Objects.requireNonNull(value, "reference value"));
      if (indexedValues.containsKey(checkedName)) {
        throw new IllegalArgumentException("节点引用名称重复: " + checkedName);
      }
      indexedValues.put(checkedName, checkedValue);
    });
    return new NodeReferenceSet<>(
        kind,
        name -> resolveValue(kind, indexedValues.get(name), complete, reason),
        nameComparator,
        complete,
        reason
    );
  }

  public NodeReferenceKind<T> getKind() {
    return kind;
  }

  public boolean isComplete() {
    return complete;
  }

  public NodeReferenceSet<T> overlay(
      Map<String, ? extends T> overrides,
      Collection<String> removedNames
  ) {
    Objects.requireNonNull(overrides, "overrides");
    Objects.requireNonNull(removedNames, "removedNames");
    var overrideValues = new TreeMap<String, T>(nameComparator);
    overrides.forEach((name, value) -> {
      var checkedName = Objects.requireNonNull(name, "reference name");
      var checkedValue = kind.valueType().cast(Objects.requireNonNull(value, "reference value"));
      if (overrideValues.containsKey(checkedName)) {
        throw new IllegalArgumentException("节点引用名称重复: " + checkedName);
      }
      overrideValues.put(checkedName, checkedValue);
    });
    var removed = new TreeSet<String>(nameComparator);
    removedNames.forEach(name -> removed.add(Objects.requireNonNull(name, "removedName")));
    return new NodeReferenceSet<>(
        kind,
        name -> removed.contains(name)
            ? resolveValue(kind, null, complete, indeterminateReason)
            : overrideValues.containsKey(name)
                ? NodeReferenceResolution.found(overrideValues.get(name))
                : resolver.apply(name),
        nameComparator,
        complete,
        indeterminateReason
    );
  }

  NodeReferenceResolution<T> resolve(String name) {
    return resolver.apply(Objects.requireNonNull(name, "name")).fold(
        value -> NodeReferenceResolution.found(kind.valueType().cast(value)),
        NodeReferenceResolution::missing,
        NodeReferenceResolution::indeterminate
    );
  }

  private static <T> NodeReferenceResolution<T> resolveValue(
      NodeReferenceKind<T> kind,
      T value,
      boolean complete,
      NodeReferenceIndeterminateReason indeterminateReason
  ) {
    if (value != null) {
      return NodeReferenceResolution.found(kind.valueType().cast(value));
    }
    if (complete) {
      return NodeReferenceResolution.missing();
    }
    return NodeReferenceResolution.indeterminate(Objects.requireNonNull(indeterminateReason, "indeterminateReason"));
  }
}
