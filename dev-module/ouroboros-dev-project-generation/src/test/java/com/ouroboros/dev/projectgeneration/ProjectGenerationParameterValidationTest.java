package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.parameter.ProjectTemplateParameterValidator;

class ProjectGenerationParameterValidationTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final ProjectTemplateParameterValidator validator = new ProjectTemplateParameterValidator();

  @Test
  void shouldFillDefaultsAndNormalizePostParameters() throws Exception {
    Map<String, Object> normalized = validator.normalize(schema(), read("{}"), Map.of(
        "projectName", "customer-portal"
    ));

    assertEquals("customer-portal", normalized.get("projectName"));
    assertEquals("demo-app", normalized.get("packageName"));
    assertEquals(Boolean.TRUE, normalized.get("includeExample"));
  }

  @Test
  void shouldRejectExtraParametersMissingRequiredAndUnsafePathSegment() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> validator.normalize(schema(), read("{}"), Map.of(
        "projectName", "customer-portal",
        "extra", "value"
    )));
    assertThrows(IllegalArgumentException.class, () -> validator.normalize(schema(), read("{}"), Map.of()));
    assertThrows(IllegalArgumentException.class, () -> validator.normalize(schema(), read("{}"), Map.of(
        "projectName", "bad/name"
    )));
  }

  private static JsonNode schema() throws Exception {
    return read(ProjectGenerationBatch5TestSupport.parameterSchema());
  }

  private static JsonNode read(String json) throws Exception {
    return OBJECT_MAPPER.readTree(json);
  }
}
