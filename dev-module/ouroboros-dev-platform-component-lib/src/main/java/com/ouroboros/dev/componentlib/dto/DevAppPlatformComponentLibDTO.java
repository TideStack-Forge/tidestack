package com.ouroboros.dev.componentlib.dto;

import java.util.Collections;
import java.util.List;

public class DevAppPlatformComponentLibDTO {
  private List<ImportedPlatformComponentLibDTO> imports;

  public List<ImportedPlatformComponentLibDTO> getImports() {
    return imports == null ? Collections.emptyList() : imports;
  }

  public DevAppPlatformComponentLibDTO setImports(List<ImportedPlatformComponentLibDTO> imports) {
    this.imports = imports;
    return this;
  }
}
