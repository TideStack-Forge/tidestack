package com.ouroboros.dev.projectgeneration.catalog;

import java.util.List;

public record ProjectTemplateCatalogDocument(
    String revision,
    long sequence,
    List<ProjectTemplateRelease> releases
) {
  public ProjectTemplateCatalogDocument {
    ProjectTemplateIdentifierContracts.requireRevision(revision, "revision");
    if (sequence < 1) {
      throw new IllegalArgumentException("Catalog sequence must be positive");
    }
    releases = List.copyOf(releases == null ? List.of() : releases);
  }
}
