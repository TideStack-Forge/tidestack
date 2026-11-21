package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.ouroboros.util.JsonSchemas;

class ProjectTemplateContractCorpusTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void shouldValidateAllVersionedContractCorpusFixtures() throws IOException {
    Path contractRoot = locateContractRoot();
    CorpusIndex corpusIndex = readJson(contractRoot.resolve("corpus/corpus-index.json"), CorpusIndex.class);

    assertTrue("1".equals(corpusIndex.contractVersion()), "corpus index must target contract v1");

    for (CorpusGroup group : corpusIndex.groups()) {
      String schema = Files.readString(contractRoot.resolve(group.schema()), StandardCharsets.UTF_8);
      assertFixtures(contractRoot, schema, group.positive(), true, group.name());
      assertFixtures(contractRoot, schema, group.negative(), false, group.name());
    }
  }

  private static void assertFixtures(
      Path contractRoot,
      String schema,
      List<String> fixtures,
      boolean expectedValid,
      String groupName
  ) throws IOException {
    for (String fixture : fixtures) {
      JsonNode data = readJson(contractRoot.resolve(fixture), JsonNode.class);
      var result = JsonSchemas.validateResult(schema, data);
      String message = groupName + " fixture " + fixture + " expected valid=" + expectedValid
          + " but got " + result.toMessageString();
      if (expectedValid) {
        assertTrue(result.isValid(), message);
      } else {
        assertFalse(result.isValid(), message);
      }
    }
  }

  private static Path locateContractRoot() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      Path candidate = current.resolve("project-templates/contracts/v1");
      if (Files.isRegularFile(candidate.resolve("corpus/corpus-index.json"))) {
        return candidate;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Cannot locate project-templates/contracts/v1");
  }

  private static <T> T readJson(Path path, Class<T> type) throws IOException {
    return OBJECT_MAPPER.readValue(path.toFile(), type);
  }

  private record CorpusIndex(String contractVersion, List<CorpusGroup> groups) {
  }

  private record CorpusGroup(
      String name,
      String schema,
      List<String> positive,
      List<String> negative
  ) {
  }
}
