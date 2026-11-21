package com.ouroboros.configuration;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.dev.metadata.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import com.ouroboros.util.Json;

class ConfigurationCodeContextProviderTest {
  private CodeContextRequest request(String pointer, String draft) {
    return new CodeContextRequest("configuration-editor", 1, 1, "configuration.value", "template", new CodeContextRequest.Resource("configuration", "endpoint"),
        new CodeContextRequest.Location(null, pointer), Json.toBean(JsonNode.class, draft));
  }
  @Test void configurationCatalogExcludesCurrentProtectedAndSensitiveSourcesWithoutExposingValues() {
    var ordinary = entry("public.url", Map.of("valueType", "STRING", "defaultValue", "do-not-expose", "label", "URL"));
    var current = entry("endpoint", Map.of());
    var secret = entry("secret", Map.of("managementAccessPolicyRequired", true, "sensitiveSourceNames", List.of("TOKEN")));
    var hidden = entry("TOKEN", Map.of());
    var view = new ReadOnlyMetadataView("app", ApplicationMetadataSnapshot.current(Map.of("configuration", List.of(ordinary, current, secret, hidden))), new ScriptIntelligenceSnapshot());
    var provider = new ConfigurationCodeContextProvider();
    var result = provider.describe(request("/defaultValue", "{\"key\":\"endpoint\"}"), view);
    assertEquals(List.of("public.url"), result.bindings().stream().map(binding -> binding.getName()).toList());
    assertEquals("$", result.projectionHints().get("configurationMarker"));
    assertFalse(Json.toJsonString(result).contains("do-not-expose"));
    assertEquals("disabled", provider.describe(request("/value", "{\"managementAccessPolicyRequired\":true}"), view).projectionHints().get("templateMode"));
    assertEquals("disabled", provider.describe(request("/value", "{\"key\":\"secret\"}"), view).projectionHints().get("templateMode"));
    assertThrows(BusinessException.class, () -> provider.describe(request("/key", "{}"), view));
  }
  private Metadata entry(String key, Map<String, Object> details) { var entry = new Metadata(); entry.setName(key); entry.setDetails(details); return entry; }
}
