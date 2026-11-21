package com.ouroboros.businessmodel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessModelMetaParserTest {
  @Test
  void selectsKnownFormatsWithoutChangingTheOwningDocument() {
    for (String version : List.of("0.1", "1.0", "2.0")) {
      var original = document();
      original.put("formatVersion", version);
      var meta = BusinessModelMetaParser.parse(Json.toJsonString(original)).get();
      assertInstanceOf(DefaultBusinessModelMeta.class, meta);
      assertEquals(version, meta.getFormatVersion());
      assertEquals(version.equals("2.0"), BusinessModelMetaParser.isCurrent(meta));
      assertEquals(version, Json.toMap(Json.toJsonString(meta)).get("formatVersion"));
      assertEquals(original.get("custom"), meta.getExtraProp("custom").orElseThrow());
      assertEquals(Map.of("future", true), meta.getExtraProp("extension").orElseThrow());
    }
  }

  @Test
  void missingVersionRemainsMissingAfterSerializationAndReload() {
    var meta = BusinessModelMetaParser.parse(Json.toJsonString(document())).get();
    assertNull(meta.getFormatVersion());
    assertFalse(BusinessModelMetaParser.isCurrent(meta));
    var serialized = Json.toJsonString(meta);
    assertFalse(Json.toMap(serialized).containsKey("formatVersion"));
    assertNull(BusinessModelMetaParser.parse(serialized).get().getFormatVersion());
  }

  @Test
  void rejectsInvalidRawVersionsBeforeStringCoercionOrDefaultFallback() {
    for (Object version : List.of("", " ", "3.0", 2, 2.0, true, List.of("2.0"), Map.of("value", "2.0"))) {
      var document = document();
      document.put("formatVersion", version);
      var result = BusinessModelMetaParser.parse(Json.toJsonString(document));
      assertTrue(result.isFailure(), String.valueOf(version));
      assertTrue(result.getCause().getMessage().contains("formatVersion"));
    }
    assertTrue(BusinessModelMetaParser.parse("{\"formatVersion\":null}").isFailure());
    assertTrue(BusinessModelMetaParser.parse("[]").isFailure());
    assertTrue(BusinessModelMetaParser.parse("not-json").isFailure());
  }

  @Test
  void doesNotApplyDefaultModelVersionsToOtherModelTypes() {
    var meta = BusinessModelMetaParser.parse("{\"modelType\":\"dashboard\",\"name\":\"Metrics\",\"formatVersion\":\"vendor-3\"}").get();
    assertEquals("dashboard", meta.getModelType());
    assertEquals("vendor-3", meta.getFormatVersion());
  }

  private static Map<String, Object> document() {
    var document = new LinkedHashMap<String, Object>();
    document.put("modelType", "default");
    document.put("name", "Order");
    document.put("extension", Map.of("future", true));
    document.put("custom", List.of(Map.of("name", "approve", "api", Map.of(
        "isEnabled", true, "isBindAction", false, "script", "return request.params.id;"))));
    return document;
  }
}
