package com.ouroboros.dev.projectgeneration.render;

import java.util.Map;

public record RenderedProjectTemplate(
    String projectName,
    Map<String, byte[]> entries
) {
  public RenderedProjectTemplate {
    if (projectName == null || projectName.isBlank()) {
      throw new IllegalArgumentException("projectName is required");
    }
    entries = Map.copyOf(entries == null ? Map.of() : entries);
  }
}
