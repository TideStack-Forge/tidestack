package com.ouroboros.dev.projectgeneration.model;

import com.fasterxml.jackson.databind.JsonNode;

public record ProjectTemplateSummary(
    String templateId,
    String templateVersion,
    String compatibilityRowId,
    String title,
    String description,
    String channel,
    JsonNode parameterSchema,
    JsonNode uiHints
) {
}
