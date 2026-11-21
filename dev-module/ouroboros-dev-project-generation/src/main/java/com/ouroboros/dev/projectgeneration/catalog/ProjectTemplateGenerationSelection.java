package com.ouroboros.dev.projectgeneration.catalog;

import java.util.Map;

import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;

record ProjectTemplateGenerationSelection(
    ProjectTemplateScope scope,
    String catalogRevision,
    String targetPlatformVersion,
    String templateId,
    String templateVersion,
    String compatibilityRowId,
    boolean allowPreview,
    Map<String, Object> parameters
) {
}
