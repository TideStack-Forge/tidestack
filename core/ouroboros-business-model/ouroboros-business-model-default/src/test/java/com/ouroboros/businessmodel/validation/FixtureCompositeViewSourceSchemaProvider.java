package com.ouroboros.businessmodel.validation;

import java.util.List;
import java.util.Optional;

public class FixtureCompositeViewSourceSchemaProvider implements CompositeViewSourceSchemaProvider {
  @Override
  public boolean supportsCurrentContext() {
    return true;
  }

  @Override
  public Optional<CompositeViewSourceSchema> resolve(String name) {
    if (!List.of("demo.sales", "sales.orders").contains(name)) {
      return Optional.empty();
    }
    return Optional.of(new CompositeViewSourceSchema(true, List.of(
        new CompositeViewSourceSchema.Field("id", "string", false, true, true))));
  }
}
