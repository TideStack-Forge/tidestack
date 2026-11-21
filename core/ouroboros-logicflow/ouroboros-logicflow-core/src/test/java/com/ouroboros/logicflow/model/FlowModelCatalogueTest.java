package com.ouroboros.logicflow.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FlowModelCatalogueTest {
  @Test
  void observesShadowedDisabledAndRuntimeSourcesWithoutChangingDefaultSelection() {
    var jar = source("jar", 0, false, "FLOW", "jar", true);
    var app = source("app", 100, false, "flow", "app", true);
    var disabled = source("disabled", 150, false, "FLOW", "disabled", false);
    var override = source("runtime", 200, true, "flow", "override", true);
    var catalogue = new FlowModelCatalogue(List.of(override, disabled, app, jar));
    assertEquals(4, catalogue.sources().size());
    assertEquals("runtime", catalogue.origins().get("FLOW"));
    assertEquals("app", catalogue.defaults().origins().get("FLOW"));
    assertEquals("override", FlowModelDefinitions.read(catalogue.definitions().get("FLOW")).getDescription());
    assertEquals(catalogue.stamp(), catalogue.defaults().withOverrides(List.of(override)).stamp());
    assertThrows(IllegalArgumentException.class, () -> catalogue.withOverrides(List.of(app)));
  }

  @Test
  void formalCopyPreservesNestedSchemaAndExtensionsButDiscardsDerivedValidation() {
    var schema = new LinkedHashMap<String, Object>();
    var required = new ArrayList<>(List.of("name"));
    schema.put("required", required);
    var extension = new ArrayList<>(List.of("first"));
    var original = FlowModelMeta.builder().name("test").inputSchema(schema)
        .nodes(List.of(FlowNodeMeta.builder().name("node").type("script")
            .extraProp("extension", extension).build()))
        .validationSummary("old error").lastValidatedTime(LocalDateTime.now()).build();
    var definition = FlowModelDefinitions.write(original);
    var copy = FlowModelDefinitions.read(definition);
    required.add("mutated");
    extension.add("mutated");
    assertEquals(List.of("name"), copy.getInputSchema().get("required"));
    assertEquals(List.of("first"), copy.getNodes().getFirst().getExtraProp("extension").orElseThrow());
    assertNull(copy.getValidationState());
    assertNull(copy.getLastValidatedTime());
    assertEquals(definition, FlowModelDefinitions.write(copy));
    assertThrows(IllegalArgumentException.class, () -> FlowModelDefinitions.read("invalid-json"));
  }

  @Test
  void canonicalFingerprintIgnoresObjectOrderAndKeepsArrayOrderAndLayout() {
    var first = FlowModelDefinitions.read("{\"name\":\"test\",\"inputSchema\":{\"type\":\"object\",\"required\":[\"a\",\"b\"]}}");
    var reordered = FlowModelDefinitions.read("{\"inputSchema\":{\"required\":[\"a\",\"b\"],\"type\":\"object\"},\"name\":\"test\"}");
    assertEquals(FlowModelDefinitions.write(first), FlowModelDefinitions.write(reordered));
    var changed = first.map(builder -> builder.inputSchema(Map.of("required", List.of("b", "a"), "type", "object")));
    assertNotEquals(FlowModelDefinitions.hash(FlowModelDefinitions.write(first)),
        FlowModelDefinitions.hash(FlowModelDefinitions.write(changed)));
    var positions = first.map(builder -> builder.nodePositions(Map.of("node", FlowNodePositionMeta.builder().x(10.0).y(20.0).build())));
    assertNotEquals(FlowModelDefinitions.write(first), FlowModelDefinitions.write(positions));
  }

  @Test
  void nameKeyFollowsExistingCaseInsensitiveEqualityIncludingUnicode() {
    for (var pair : List.of(List.of("Flow", "FLOW"), List.of("İ", "ı"), List.of("Σ", "ς"),
        List.of("\uD801\uDC00", "\uD801\uDC28"))) {
      assertEquals(0, pair.getFirst().compareToIgnoreCase(pair.getLast()));
      assertEquals(FlowModelDefinitions.nameKey(pair.getFirst()), FlowModelDefinitions.nameKey(pair.getLast()));
    }
    assertNotEquals(FlowModelDefinitions.nameKey("ß"), FlowModelDefinitions.nameKey("SS"));
  }

  private FlowModelSource source(String id, int priority, boolean runtime, String name, String description, boolean enabled) {
    return FlowModelSource.capture(id, priority, runtime, true, List.of(), Map.of(name,
        FlowModelMeta.builder().name(name).description(description).enabled(enabled).build()));
  }
}
