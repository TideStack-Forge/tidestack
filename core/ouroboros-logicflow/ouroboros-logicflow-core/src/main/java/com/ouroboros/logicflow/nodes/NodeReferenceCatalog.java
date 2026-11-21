package com.ouroboros.logicflow.nodes;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.BiConsumer;

public final class NodeReferenceCatalog {
  private final Map<NodeReferenceKind<?>, Function<String, NodeReferenceResolution<?>>> resolvers;
  private final BiConsumer<NodeReferenceKind<?>, String> observer;

  private NodeReferenceCatalog(
      Map<NodeReferenceKind<?>, Function<String, NodeReferenceResolution<?>>> resolvers
  ) {
    this(resolvers, (kind, name) -> { });
  }

  private NodeReferenceCatalog(
      Map<NodeReferenceKind<?>, Function<String, NodeReferenceResolution<?>>> resolvers,
      BiConsumer<NodeReferenceKind<?>, String> observer
  ) {
    this.resolvers = Collections.unmodifiableMap(new LinkedHashMap<>(resolvers));
    this.observer = observer;
  }

  /** Observe actual resolutions, including missing references, without changing node semantics. */
  public NodeReferenceCatalog observing(BiConsumer<NodeReferenceKind<?>, String> observer) {
    Objects.requireNonNull(observer);
    return new NodeReferenceCatalog(resolvers, (kind, name) -> {
      this.observer.accept(kind, name);
      observer.accept(kind, name);
    });
  }

  public static NodeReferenceCatalog empty() {
    return new NodeReferenceCatalog(Collections.emptyMap());
  }

  public static NodeReferenceCatalog of(Collection<NodeReferenceSet<?>> referenceSets) {
    Objects.requireNonNull(referenceSets, "referenceSets");
    var indexedResolvers = new LinkedHashMap<
        NodeReferenceKind<?>, Function<String, NodeReferenceResolution<?>>
    >();
    referenceSets.forEach(set -> {
      var nonNullSet = Objects.requireNonNull(set, "referenceSet");
      var previous = indexedResolvers.putIfAbsent(nonNullSet.getKind(), nonNullSet::resolve);
      if (previous != null) {
        throw new IllegalArgumentException("节点引用类型重复注册: " + nonNullSet.getKind().name());
      }
    });
    return new NodeReferenceCatalog(indexedResolvers);
  }

  public NodeReferenceCatalog with(NodeReferenceSet<?> referenceSet) {
    Objects.requireNonNull(referenceSet, "referenceSet");
    if (resolvers.containsKey(referenceSet.getKind())) {
      throw new IllegalArgumentException("节点引用类型重复注册: " + referenceSet.getKind().name());
    }
    var extendedResolvers = new LinkedHashMap<>(resolvers);
    extendedResolvers.put(referenceSet.getKind(), referenceSet::resolve);
    return new NodeReferenceCatalog(extendedResolvers, observer);
  }

  public <T> NodeReferenceResolution<T> resolve(NodeReferenceKind<T> kind, String name) {
    observer.accept(kind, name);
    var resolver = resolvers.get(Objects.requireNonNull(kind, "kind"));
    if (resolver == null) {
      throw new IllegalStateException("节点引用类型未注册: " + kind.name());
    }
    return resolver.apply(name).fold(
        value -> NodeReferenceResolution.found(kind.valueType().cast(value)),
        NodeReferenceResolution::missing,
        NodeReferenceResolution::indeterminate
    );
  }

}
