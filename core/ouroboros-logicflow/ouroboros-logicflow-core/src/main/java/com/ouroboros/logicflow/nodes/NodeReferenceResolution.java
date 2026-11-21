package com.ouroboros.logicflow.nodes;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface NodeReferenceResolution<T>
    permits NodeReferenceResolution.Found,
    NodeReferenceResolution.Missing,
    NodeReferenceResolution.Indeterminate {

  <R> R fold(
      Function<? super T, ? extends R> onFound,
      Supplier<? extends R> onMissing,
      Function<? super NodeReferenceIndeterminateReason, ? extends R> onIndeterminate
  );

  static <T> NodeReferenceResolution<T> found(T value) {
    return new Found<>(value);
  }

  static <T> NodeReferenceResolution<T> missing() {
    return new Missing<>();
  }

  static <T> NodeReferenceResolution<T> indeterminate(NodeReferenceIndeterminateReason reason) {
    return new Indeterminate<>(reason);
  }

  record Found<T>(T value) implements NodeReferenceResolution<T> {
    public Found {
      Objects.requireNonNull(value, "value");
    }

    @Override
    public <R> R fold(
        Function<? super T, ? extends R> onFound,
        Supplier<? extends R> onMissing,
        Function<? super NodeReferenceIndeterminateReason, ? extends R> onIndeterminate
    ) {
      return onFound.apply(value);
    }
  }

  record Missing<T>() implements NodeReferenceResolution<T> {
    @Override
    public <R> R fold(
        Function<? super T, ? extends R> onFound,
        Supplier<? extends R> onMissing,
        Function<? super NodeReferenceIndeterminateReason, ? extends R> onIndeterminate
    ) {
      return onMissing.get();
    }
  }

  record Indeterminate<T>(NodeReferenceIndeterminateReason reason) implements NodeReferenceResolution<T> {
    public Indeterminate {
      Objects.requireNonNull(reason, "reason");
    }

    @Override
    public <R> R fold(
        Function<? super T, ? extends R> onFound,
        Supplier<? extends R> onMissing,
        Function<? super NodeReferenceIndeterminateReason, ? extends R> onIndeterminate
    ) {
      return onIndeterminate.apply(reason);
    }
  }
}
