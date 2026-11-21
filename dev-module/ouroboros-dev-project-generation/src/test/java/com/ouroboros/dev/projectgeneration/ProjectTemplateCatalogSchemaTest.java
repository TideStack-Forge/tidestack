package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.ouroboros.util.JsonSchemas;

class ProjectTemplateCatalogSchemaTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void shouldValidateCatalogCorpusWithVersionedSchema() throws IOException {
    Path root = locateContractRoot();
    String schema = Files.readString(root.resolve("catalog.schema.json"), StandardCharsets.UTF_8);
    JsonNode positive = readJson(root.resolve("corpus/catalog/positive/minimal-catalog.json"));
    JsonNode negative = readJson(root.resolve("corpus/catalog/negative/unknown-channel.json"));

    assertTrue(JsonSchemas.validateResult(schema, positive).isValid());
    assertFalse(JsonSchemas.validateResult(schema, negative).isValid());
  }

  private static JsonNode readJson(Path path) throws IOException {
    return OBJECT_MAPPER.readTree(path.toFile());
  }

  private static Path locateContractRoot() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      Path candidate = current.resolve("project-templates/contracts/v1");
      if (Files.isRegularFile(candidate.resolve("catalog.schema.json"))) {
        return candidate;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Cannot locate project-templates/contracts/v1");
  }
}
