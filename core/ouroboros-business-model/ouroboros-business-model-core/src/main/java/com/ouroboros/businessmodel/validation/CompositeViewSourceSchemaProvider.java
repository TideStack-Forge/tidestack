package com.ouroboros.businessmodel.validation;

import java.util.Optional;

/** Supplies the source schema from the current development workspace or effective runtime definitions. */
public interface CompositeViewSourceSchemaProvider {
  boolean supportsCurrentContext();

  Optional<CompositeViewSourceSchema> resolve(String datasetName);

  default Optional<CompositeViewSourceSchema> resolveEmbedded(Object query, String owner) {
    return Optional.empty();
  }
}
