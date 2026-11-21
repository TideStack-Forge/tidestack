package com.ouroboros.businesslog.dev;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.dev.metadata.*;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import com.ouroboros.util.Json;

class BusinessLogCodeContextProviderTest {
  @Test void httpContractsSupplyTemplateFieldsWithRuntimePrecedenceAndFixedRoots() {
    var api = new Metadata(); api.setName("POST /orders/{id}");
    api.setDetails(Json.toMap("""
        {"method":"POST","path":"/orders/{id}","parameters":[{"name":"search","in":"query"}],
        "requestBody":{"content":{"application/json":{"schema":{"type":"object","properties":{"id":{"type":"integer"},"customer":{"type":"object","properties":{"name":{"type":"string"}}}}}}}},
        "responses":{"200":{"content":{"application/json":{"schema":{"type":"object","properties":{"data":{"type":"object","properties":{"approved":{"type":"boolean"}}}}}}}}}}
        """));
    var metadata = new ReadOnlyMetadataView("app", ApplicationMetadataSnapshot.current(Map.of("webapi", List.of(api))), new ScriptIntelligenceSnapshot());
    var request = new CodeContextRequest("log", 1, 1, "business-log.summary", "freemarker",
        new CodeContextRequest.Resource("business-log-template", "orders"), new CodeContextRequest.Location(null, "/summaryTemplate"),
        Json.toBean(JsonNode.class, "{\"name\":\"orders\",\"httpMethod\":\"POST\",\"url\":\"/orders/{id}\"}"));
    var result = new BusinessLogCodeContextProvider().describe(request, metadata);
    assertFalse(result.allowGlobals());
    var bindings = result.bindings().stream().collect(java.util.stream.Collectors.toMap(binding -> binding.getName(), binding -> binding.getShape()));
    assertEquals("string", bindings.get("id").type(), "Path variables shadow request body fields");
    assertEquals("integer", bindings.get("Request").property("body").property("id").type());
    assertEquals("string", bindings.get("customer").property("name").type());
    assertEquals("boolean", bindings.get("Response").property("data").property("approved").type());
    assertTrue(bindings.containsKey("Auth")); assertTrue(bindings.containsKey("Jsons"));
    assertTrue(result.diagnostics().isEmpty());
  }
}
