package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.parameter.ProjectTemplateParameterValidator;

class ProjectParameterContractCorpusParityTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final ProjectTemplateParameterValidator validator = new ProjectTemplateParameterValidator();

  @Test
  void shouldClassifyParameterCorpusLikeJavaRuntimeValidator() throws Exception {
    for (Path positive : jsonFiles(locateContractRoot().resolve("corpus/parameter/positive"))) {
      JsonNode schema = readJson(positive);
      assertDoesNotThrow(() -> validator.validateContract(schema, readJson("{}")), positive.toString());
    }
    for (Path negative : jsonFiles(locateContractRoot().resolve("corpus/parameter/negative"))) {
      JsonNode schema = readJson(negative);
      assertThrows(IllegalArgumentException.class, () -> validator.validateContract(schema, readJson("{}")),
          negative.toString());
    }
  }

  private static Path[] jsonFiles(Path dir) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      return files.filter(path -> path.getFileName().toString().endsWith(".json"))
          .sorted()
          .toArray(Path[]::new);
    }
  }

  private static JsonNode readJson(Path path) throws IOException {
    return OBJECT_MAPPER.readTree(path.toFile());
  }

  private static JsonNode readJson(String json) throws IOException {
    return OBJECT_MAPPER.readTree(json);
  }

  private static Path locateContractRoot() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      Path candidate = current.resolve("project-templates/contracts/v1");
      if (Files.isDirectory(candidate.resolve("corpus/parameter"))) {
        return candidate;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Cannot locate project-templates/contracts/v1");
  }
}
