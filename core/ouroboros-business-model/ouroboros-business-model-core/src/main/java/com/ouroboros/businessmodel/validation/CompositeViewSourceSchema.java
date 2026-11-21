package com.ouroboros.businessmodel.validation;

import java.util.List;

/** Source contract needed by composite-view validation, independent of the execution implementation. */
public record CompositeViewSourceSchema(boolean enabled, List<Field> fields) {
  public CompositeViewSourceSchema {
    fields = List.copyOf(fields);
  }

  public record Field(String name, String dataType, boolean nullable, boolean filterable, boolean sortable) {
  }
}
