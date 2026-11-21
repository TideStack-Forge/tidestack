package com.ouroboros.dev.codeintelligence;

import com.ouroboros.codeintelligence.type.TypeShape;

/** Domain-owned static evidence about calls to a resource; no runtime invocation or sample collection. */
public interface InvocationInputProvider {
  java.util.Optional<TypeShape> infer(String resourceType, String name, ReadOnlyMetadataView metadata);
}
