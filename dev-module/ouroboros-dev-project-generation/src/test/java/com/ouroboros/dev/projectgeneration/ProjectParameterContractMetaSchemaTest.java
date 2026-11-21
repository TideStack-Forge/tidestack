package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.parameter.ProjectTemplateParameterValidator;

class ProjectParameterContractMetaSchemaTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final ProjectTemplateParameterValidator validator = new ProjectTemplateParameterValidator();

  @Test
  void shouldAcceptFlatV1ParameterContract() throws Exception {
    assertDoesNotThrow(() -> validator.validateContract(read(ProjectGenerationBatch5TestSupport.parameterSchema()), read("{}")));
  }

  @Test
  void shouldRejectRefNestedObjectExtraKeywordAndMissingStringMaxLength() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> validator.validateContract(read("""
        {"$ref":"#/bad","type":"object","additionalProperties":false,"properties":{}}
        """), read("{}")));
    assertThrows(IllegalArgumentException.class, () -> validator.validateContract(read("""
        {"type":"object","additionalProperties":false,"properties":{"nested":{"type":"object"}}}
        """), read("{}")));
    assertThrows(IllegalArgumentException.class, () -> validator.validateContract(read("""
        {"type":"object","additionalProperties":false,"properties":{"name":{"type":"string","pattern":"x","maxLength":64}}}
        """), read("{}")));
    assertThrows(IllegalArgumentException.class, () -> validator.validateContract(read("""
        {"type":"object","additionalProperties":false,"properties":{"name":{"type":"string"}}}
        """), read("{}")));
  }

  private static JsonNode read(String json) throws Exception {
    return OBJECT_MAPPER.readTree(json);
  }
}
