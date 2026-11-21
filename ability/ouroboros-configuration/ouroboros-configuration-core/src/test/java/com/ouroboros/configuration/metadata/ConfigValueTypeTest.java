package com.ouroboros.configuration.metadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ConfigValueTypeTest {

  @Test
  public void forValue_andMetadataAccessorsResolveExpectedTypes() {
    assertSame(ConfigValueType.STRING, ConfigValueType.forValue("missing"));
    assertSame(ConfigValueType.BOOLEAN, ConfigValueType.forValue("BOOLEAN"));
    assertSame(ConfigValueType.ARRAY, ConfigValueType.forValue("array"));
    assertEquals("int", ConfigValueType.INT.getName());
    assertEquals(Integer.class, ConfigValueType.INT.getType());
    assertEquals(Map.class, ConfigValueType.MAP.getType());
    assertEquals(List.class, ConfigValueType.LIST.getType());
  }

  @Test
  public void validation_andConversion_coverPrimitiveAndStructuredTypes() {
    assertTrue(ConfigValueType.INT.isValid("12"));
    assertFalse(ConfigValueType.INT.isValid("12x"));
    assertTrue(ConfigValueType.LONG.isValid("12"));
    assertTrue(ConfigValueType.FLOAT.isValid("1.5"));
    assertTrue(ConfigValueType.DOUBLE.isValid("1.5"));
    assertTrue(ConfigValueType.BOOLEAN.isValid("true"));
    assertTrue(ConfigValueType.STRING.isValid("anything"));
    assertTrue(ConfigValueType.INT.isValid(Integer.valueOf(12)));
    assertTrue(ConfigValueType.LONG.isValid(Long.valueOf(12L)));
    assertTrue(ConfigValueType.FLOAT.isValid(Float.valueOf(1.5f)));
    assertTrue(ConfigValueType.DOUBLE.isValid(Double.valueOf(1.5d)));
    assertTrue(ConfigValueType.BOOLEAN.isValid(Boolean.TRUE));
    assertTrue(ConfigValueType.STRING.isValid(new String("plain")));
    assertTrue(ConfigValueType.LIST.isValid(List.of("item")));
    assertTrue(ConfigValueType.MAP.isValid(Map.of("key", "value")));
    assertEquals(Integer.valueOf(12), ConfigValueType.INT.convert("12"));
    assertEquals(Long.valueOf(12L), ConfigValueType.LONG.convert("12"));
    assertEquals(Float.valueOf(1.5f), ConfigValueType.FLOAT.convert("1.5"));
    assertEquals(Double.valueOf(1.5d), ConfigValueType.DOUBLE.convert("1.5"));
    assertEquals(Boolean.TRUE, ConfigValueType.BOOLEAN.convert("true"));
  }

  @Test
  public void objectTypeValidationRejectsIncompatibleContainers() {
    // 非字符串值走 isAssignableFrom 判定，方向必须是"期望类型 ⊇ 实际类型"：反过来会让任意
    // 容器通过校验（例如期望 List 却收到 Integer）。这条断言固定该方向。
    assertTrue(ConfigValueType.LIST.isValid(java.util.List.of("item")));
    assertFalse(ConfigValueType.LIST.isValid(Integer.valueOf(1)));
    assertFalse(ConfigValueType.MAP.isValid(java.util.List.of("item")));
    assertTrue(ConfigValueType.MAP.isValid(Map.of("k", "v")));
    assertFalse(ConfigValueType.INT.isValid(Long.valueOf(1L)));
    assertTrue(ConfigValueType.INT.isValid(Integer.valueOf(1)));
  }

  @Test
  public void containerTypesAcceptAnyStringAndOnlyTheirOwnContainers() {
    // 字符串形态一律交给格式校验器（LIST/MAP 的字符串校验恒为真），容器形态才走类型判定。
    // 两条路径的差异是既有语义，这里固定它，避免将来合并时静默改变行为。
    assertTrue(ConfigValueType.LIST.isValid("[]"));
    assertTrue(ConfigValueType.LIST.isValid("plain-string"));
    assertFalse(ConfigValueType.INT.isValid("not-a-number"));
  }

  @Test
  public void numericTypesAcceptWideningValuesButRejectNarrowing() {
    // JSON 中不带小数点的数字会被反序列化为 Integer：double/long/float 型配置必须能接受，
    // 否则前端以整数形态提交（如 50000）会误报「值类型不匹配」。
    assertTrue(ConfigValueType.DOUBLE.isValid(Integer.valueOf(50000)));
    assertTrue(ConfigValueType.DOUBLE.isValid(Long.valueOf(50000L)));
    assertTrue(ConfigValueType.DOUBLE.isValid(Float.valueOf(1.5f)));
    assertTrue(ConfigValueType.FLOAT.isValid(Integer.valueOf(50000)));
    assertTrue(ConfigValueType.LONG.isValid(Integer.valueOf(50000)));

    // 会静默截断的收窄仍需拒绝
    assertFalse(ConfigValueType.INT.isValid(Long.valueOf(1L)));
    assertFalse(ConfigValueType.INT.isValid(Double.valueOf(1.5d)));
    assertFalse(ConfigValueType.LONG.isValid(Double.valueOf(1.5d)));

    // 非数值类型不接受数值
    assertFalse(ConfigValueType.BOOLEAN.isValid(Integer.valueOf(1)));
    assertFalse(ConfigValueType.STRING.isValid(Integer.valueOf(1)));
    assertFalse(ConfigValueType.LIST.isValid(Integer.valueOf(1)));
  }

  @Test
  public void toString_formatsMapListAndPrimitiveValues() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("mode", "strict");

    assertEquals("not-json", ConfigValueType.MAP.toString("not-json"));
    assertEquals("not-json", ConfigValueType.LIST.toString("not-json"));
    assertEquals("{\"mode\":\"strict\"}", ConfigValueType.MAP.toString(map));
    assertEquals("{}", ConfigValueType.OBJECT.toString(Collections.<String, Object>emptyMap()));
    assertEquals("[]", ConfigValueType.LIST.toString(Collections.emptyList()));
    assertEquals("123", ConfigValueType.INT.toString(Integer.valueOf(123)));
    assertEquals("plain", ConfigValueType.STRING.toString("plain"));
  }
}
