package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.parameter.ProjectTemplateParameterValidator;

class ProjectParameterVisibleWhenContractTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final ProjectTemplateParameterValidator validator = new ProjectTemplateParameterValidator();

  @Test
  void shouldAcceptVisibleWhenOnOptionalScalarParameter() throws Exception {
    assertDoesNotThrow(() -> validator.validateContract(schema(), read("""
        {"packageName":{"visibleWhen":{"parameter":"includeExample","equals":true}}}
        """)));
  }

  @Test
  void shouldRejectVisibleWhenOnRequiredTypeMismatchAndCycles() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> validator.validateContract(schema(), read("""
        {"projectName":{"visibleWhen":{"parameter":"includeExample","equals":true}}}
        """)));
    assertThrows(IllegalArgumentException.class, () -> validator.validateContract(schema(), read("""
        {"packageName":{"visibleWhen":{"parameter":"includeExample","equals":"yes"}}}
        """)));
    assertThrows(IllegalArgumentException.class, () -> validator.validateContract(read("""
        {
          "type":"object",
          "x-ouroboros-project-root":"projectName",
          "additionalProperties":false,
          "properties":{
            "projectName":{"type":"string","maxLength":64,"x-ouroboros-format":"path-segment"},
            "a":{"type":"string","maxLength":10},
            "b":{"type":"string","maxLength":10}
          },
          "required":["projectName"]
        }
        """), read("""
        {
          "a":{"visibleWhen":{"parameter":"b","equals":"x"}},
          "b":{"visibleWhen":{"parameter":"a","equals":"x"}}
        }
        """)));
  }

  private static JsonNode schema() throws Exception {
    return read(ProjectGenerationBatch5TestSupport.parameterSchema());
  }

  private static JsonNode read(String json) throws Exception {
    return OBJECT_MAPPER.readTree(json);
  }
}
