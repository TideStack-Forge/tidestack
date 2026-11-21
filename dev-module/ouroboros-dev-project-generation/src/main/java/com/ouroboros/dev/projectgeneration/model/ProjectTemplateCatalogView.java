package com.ouroboros.dev.projectgeneration.model;

import java.util.List;

public record ProjectTemplateCatalogView(
    String revision,
    String targetPlatformVersion,
    ProjectTemplateScope scope,
    boolean previewIncluded,
    List<ProjectTemplateSummary> templates
) {
}
