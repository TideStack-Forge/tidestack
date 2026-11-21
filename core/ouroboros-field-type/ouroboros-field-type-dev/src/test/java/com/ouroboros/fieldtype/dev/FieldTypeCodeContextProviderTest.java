package com.ouroboros.fieldtype.dev;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import com.ouroboros.util.Json;

class FieldTypeCodeContextProviderTest {
  @Test void backendTemplateParamsAreDescribedWithoutExecutingTemplatesOrBrowserExpressions() {
    var provider = new FieldTypeCodeContextProvider();
    var draft = Json.toBean(JsonNode.class, """
        {"schema":{"paramsUiSchema":[{"name":"length","value":100},{"name":"optional"}],
          "dataType":{"name":"string","length":"${length}"},"ui":{"input":{"type":"input-text","visibleOn":"${browserOnly}"}}}}
        """);
    var request = new CodeContextRequest("type-editor", 1, 1, "field-type.template", "template", new CodeContextRequest.Resource("field-type", "text"),
        new CodeContextRequest.Location(null, "/schema/dataType"), draft);
    var view = new ReadOnlyMetadataView("app", ApplicationMetadataSnapshot.unavailable(), new ScriptIntelligenceSnapshot());
    var result = provider.describe(request, view);
    assertEquals("integer", result.bindings().getFirst().getShape().type());
    assertTrue(result.bindings().getLast().getShape().isUnknown());
    assertEquals("template", result.projectionHints().get("embeddedExpressionMode"));
    var invalid = new CodeContextRequest("type-editor", 1, 1, "field-type.template", "template", request.resource(), new CodeContextRequest.Location(null, "/schema/name"), draft);
    assertThrows(BusinessException.class, () -> provider.describe(invalid, view));
  }
}
