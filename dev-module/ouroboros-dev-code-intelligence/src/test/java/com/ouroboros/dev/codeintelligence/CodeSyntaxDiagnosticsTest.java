package com.ouroboros.dev.codeintelligence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.util.Json;

class CodeSyntaxDiagnosticsTest {
  private final CodeSyntaxDiagnostics syntax = new CodeSyntaxDiagnostics();

  private static com.fasterxml.jackson.databind.JsonNode json(Object value) {
    return Json.toBean(com.fasterxml.jackson.databind.JsonNode.class, Json.toJsonString(value));
  }

  private List<Map<String, Object>> diagnose(String language, Object value, Map<String, Object> hints) {
    var request = new CodeContextRequest("editor", 1, 1, "test.value", language,
        new CodeContextRequest.Resource("test", "model"), new CodeContextRequest.Location(null, "/value"),
        json(Map.of("value", value)));
    return syntax.diagnose(request, new CodeContextDescription(language, List.of(), TypeShape.unknown("test"), List.of(), false, hints));
  }

  @Test void bareSpelReportsGrammarErrorsWithoutResolvingUnknownNamesOrExecutingCode() {
    assertFalse(diagnose("spel", "$0.total +", Map.of("embeddedExpressionMode", "bare")).isEmpty());
    assertTrue(diagnose("spel", "$0.items.?[enabled].![name]", Map.of("embeddedExpressionMode", "bare")).isEmpty());
    assertTrue(diagnose("spel", "${unknown.method()}", Map.of()).isEmpty());
    String key = "ouroboros.syntax.must-not-execute";
    assertNull(System.getProperty(key));
    assertTrue(diagnose("spel", "${T(java.lang.System).setProperty('" + key + "', 'executed')}", Map.of()).isEmpty());
    assertNull(System.getProperty(key));
  }

  @Test void mixedTemplatesDistinguishConfigurationReferencesExpressionsAndEscapedLiterals() {
    assertTrue(diagnose("rpc-template", "https://#{service.host}/${customer.id}", Map.of("templateMode", "mixed")).isEmpty());
    assertFalse(diagnose("rpc-template", "https://#{service.host}/${customer.id + }", Map.of("templateMode", "mixed")).isEmpty());
    assertFalse(diagnose("rpc-template", "#{service.host", Map.of("templateMode", "mixed")).isEmpty());
    assertFalse(diagnose("rpc-template", "#{ }", Map.of("templateMode", "mixed")).isEmpty());
    assertTrue(diagnose("template", "literal $${not closed", Map.of("templateMode", "expression-only")).isEmpty());
    assertTrue(diagnose("template", "yyyy/MM/{dd}", Map.of("templateMode", "expression-only")).isEmpty());
  }

  @Test void configurationOnlyAndDisabledFieldsDoNotAccidentallyParseSpel() {
    assertTrue(diagnose("rpc-template", "${not + valid", Map.of("templateMode", "config-only")).isEmpty());
    assertFalse(diagnose("rpc-template", "#{missing", Map.of("templateMode", "config-only")).isEmpty());
    assertTrue(diagnose("template", "${anything", Map.of("templateMode", "disabled")).isEmpty());
    assertFalse(diagnose("template", "${value +}", Map.of("templateMode", "expression-only", "configurationMarker", "$")).isEmpty());
    assertTrue(diagnose("template", "literal #{not a configuration", Map.of("templateMode", "expression-only")).isEmpty());
  }

  @Test void payloadTemplatesArePlainSubstitutionsAndOnlyDeclaredPayloadFieldsAreChecked() {
    var hints = Map.<String, Object>of("templateMode", "payload-only", "embeddedPayloadFields", List.of("titleTemplate"));
    assertTrue(diagnose("template", Map.of("titleTemplate", "Hello ${customer-name}", "raw", "${literal"), hints).isEmpty());
    var issues = diagnose("template", Map.of("titleTemplate", "Hello ${customer"), hints);
    assertEquals(1, issues.size());
    assertEquals(List.of("titleTemplate"), issues.getFirst().get("path"));
  }

  @Test void freemarkerUsesTheRealParserWithoutRenderingOrLoadingIncludes() {
    assertTrue(diagnose("freemarker", "<#if unknown>\n${missing.method()}\n</#if>", Map.of()).isEmpty());
    assertTrue(diagnose("freemarker", "<#include 'file-that-does-not-exist.ftl'>", Map.of()).isEmpty());
    var issues = diagnose("freemarker", "Hello\n<#if enabled>", Map.of());
    assertEquals(1, issues.size());
    assertEquals(1, issues.getFirst().get("severity"));
    assertEquals("ouroboros-syntax", issues.getFirst().get("source"));
    assertFalse(diagnose("freemarker", "${name?not_a_real_builtin}", Map.of()).isEmpty());
    assertTrue(diagnose("freemarker", "<#noparse>${literal</#noparse>", Map.of()).isEmpty());
  }

  @Test void jsonPathIsCompiledWithoutReadingAnyResponseOrConfiguration() {
    assertTrue(diagnose("jsonpath", "$['customers'][?(@.enabled == true)].name", Map.of()).isEmpty());
    assertFalse(diagnose("jsonpath", "$['access_token'", Map.of()).isEmpty());
    assertTrue(diagnose("jsonpath", "#{token.path}", Map.of()).isEmpty());
    assertFalse(diagnose("jsonpath", "#{token.path", Map.of()).isEmpty());
  }

  @Test void mappingChecksStringValuesAndRetainsTheirPathsButSkipsLiteralArrayContents() {
    var issues = diagnose("spel", Map.of("bad key +", "$0.name", "nested", Map.of("value", "$0.amount +"),
        "literal", List.of("not an expression +"), "count", 1),
        Map.of("embeddedExpressionMode", "bare", "embeddedArrayValues", false));
    assertEquals(1, issues.size());
    assertEquals(List.of("nested", "value"), issues.getFirst().get("path"));
    assertTrue(diagnose("spel", Map.of("literal", "plain text", "expression", "${unknown.name}"), Map.of()).isEmpty());
  }

  @Test void legacyMappingRetainsItsOwnLiteralAndReferenceRules() {
    assertTrue(diagnose("spel", Map.of("literal", "${broken"), Map.of("embeddedExpressionMode", "legacy-input")).isEmpty());
    assertFalse(diagnose("spel", Map.of("expression", "$.name["), Map.of("embeddedExpressionMode", "legacy-input")).isEmpty());
    assertFalse(diagnose("spel", Map.of("expression", "${name +}"), Map.of("embeddedExpressionMode", "legacy-event")).isEmpty());
    assertTrue(diagnose("spel", Map.of("literal", "$.name["), Map.of("embeddedExpressionMode", "legacy-output")).isEmpty());
  }

  @Test void diagnosticsCarryUtf16LocationsWithinTheSelectedFlowNode() {
    var request = new CodeContextRequest("editor", 3, 2, "logicflow.expression", "spel",
        new CodeContextRequest.Resource("logicflow", "flow"), new CodeContextRequest.Location("current", "/expression"),
        json(Map.of("nodes", List.of(Map.of("name", "other", "expression", "broken +"),
            Map.of("name", "current", "expression", "${'😀'}\n${value +}")))));
    var issues = syntax.diagnose(request, new CodeContextDescription("spel", List.of(), TypeShape.unknown("test"), List.of(), false));
    assertEquals(1, issues.size());
    var range = json(issues.getFirst()).path("range");
    assertEquals(1, range.path("start").path("line").asInt());
    assertTrue(diagnose("javascript", "const value = ;", Map.of()).isEmpty());
  }

  @Test void spelLocationsIgnoreRepeatedLiteralBodiesAndEscapedOpeners() {
    for (String text : List.of("value + * 2: ${value + * 2}", "literal $${ignored}\r${value + * 2}",
        "${'${value + * 2}'} ${value + * 2}")) {
      var issue = json(diagnose("spel", text, Map.of()).getFirst());
      int line = text.contains("\r") ? 1 : 0;
      int column = text.lastIndexOf('*') - (line == 1 ? text.indexOf('\r') + 1 : 0);
      assertEquals(json(Map.of("line", line, "character", column)), issue.path("range").path("start"), text);
    }
    var bare = json(diagnose("spel", "$0 + * 2", Map.of("embeddedExpressionMode", "bare")).getFirst());
    assertEquals(json(Map.of("line", 0, "character", 5)), bare.path("range").path("start"));
  }

  @Test void freemarkerLocationsConvertTabsAndCarriageReturnsToUtf16Coordinates() {
    String text = "Hello\r\t${name + * 2}";
    var issue = json(diagnose("freemarker", text, Map.of()).getFirst());
    assertEquals(json(Map.of("line", 1, "character", 10)), issue.path("range").path("start"));
  }
}
