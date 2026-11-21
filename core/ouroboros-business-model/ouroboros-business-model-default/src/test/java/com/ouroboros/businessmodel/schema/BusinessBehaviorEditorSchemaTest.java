package com.ouroboros.businessmodel.schema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessBehaviorEditorSchemaTest {
  @Test
  void exposesOriginalDefinitionEditorsAndVersionedReferencesWithoutSaveDefaults() throws Exception {
    var schema = Json.toMap(Json.toJsonString(new DefaultBusinessModelEditorSchemaGenerator().generate()));
    var controls = new ArrayList<Map<?, ?>>();
    collect(schema, controls);
    var workbenches = controls.stream().filter(control -> "business-behavior-catalog".equals(control.get("type"))).toList();
    assertEquals(1, workbenches.size());
    var workbench = workbenches.getFirst();
    assertTrue(workbench.get("definitionSchemas") instanceof Map<?, ?>);
    var definitions = (Map<?, ?>) workbench.get("definitionSchemas");
    assertEquals(java.util.Set.of("action", "event", "subscription"), definitions.keySet());
    assertFalse(workbench.containsKey("value"));
    assertTrue(workbench.get("diagnostics") instanceof List<?>);
    assertTrue(controls.stream().noneMatch(control -> "business-behavior-definitions".equals(control.get("type"))));
    var reference = controls.stream().filter(control -> "business-action-reference".equals(control.get("type"))).findFirst().orElseThrow();
    assertEquals("api", reference.get("name"));
    assertTrue(reference.containsKey("visibleOn"));
    for (var control : controls) {
      if (control.get("name") instanceof String name
          && List.of("api.isGenerateImplementation", "api.isBindAction", "api.isCustomLogicFlow", "custom").contains(name)) {
        assertFalse(control.containsKey("value"), String.valueOf(control.get("name")));
      }
    }
    String artifact = System.getProperty("ouroboros.editor-schema-artifact");
    if (artifact != null) { Files.writeString(Path.of(artifact), Json.toPrettyJsonString(schema)); }
  }

  private static void collect(Object node, List<Map<?, ?>> controls) {
    if (node instanceof Map<?, ?> map) {
      controls.add(map);
      map.values().forEach(child -> collect(child, controls));
    } else if (node instanceof List<?> list) {
      list.forEach(child -> collect(child, controls));
    }
  }
}
