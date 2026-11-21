package com.ouroboros.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.exception.PlatformException;

public class ConfigurationServiceTest {

  @Test
  public void defaultMethods_delegateToGetAndReturnNonNullValue() {
    ConfigurationService service = new ConfigurationService() {
      @Override
      public <T> Optional<T> get(String key) {
        return (Optional<T>) Optional.of("configured-value");
      }

      @Override
      public Map<String, Object> getValues(ConfigScope scope) {
        return Collections.emptyMap();
      }
    };

    Optional<String> value = service.get(String.class, "demo.key");

    assertEquals(Optional.of("configured-value"), value);
    assertSame("configured-value", service.getRequired("demo.key"));
    assertSame("configured-value", service.getNotNullValue("demo.key"));
    assertEquals(Optional.of("configured-value"), service.getByKey(String.class, "demo.key"));
    assertSame("configured-value", service.getNotNullValueByKey("demo.key"));
  }

  @Test
  public void typedAccessors_convertScalarObjectAndListValues() {
    Map<String, Object> electricity = new LinkedHashMap<>();
    electricity.put("energyType", "electricity");
    electricity.put("coefficient", 1.23d);

    Map<String, Object> coal = new LinkedHashMap<>();
    coal.put("energyType", "coal");
    coal.put("coefficient", 0.71d);

    ConfigurationService service = configurationService(Map.of(
        "integerString", "12",
        "booleanString", "true",
        "stringNumber", 42,
        "object", electricity,
        "objectList", List.of(electricity, coal),
        "rawList", List.of("electricity", 12, 1.5d, coal),
        "jsonObject", "{\"energyType\":\"gas\",\"coefficient\":0.9}",
        "jsonObjectList", "[{\"energyType\":\"steam\",\"coefficient\":0.5}]",
        "jsonRawList", "[\"sms\",2,{\"channel\":\"call\"}]"
    ));

    assertEquals(Optional.of(Integer.valueOf(12)), service.get(Integer.class, "integerString"));
    assertEquals(Optional.of(Boolean.TRUE), service.get(Boolean.class, "booleanString"));
    assertEquals(Optional.of("42"), service.get(String.class, "stringNumber"));

    assertEquals("electricity", service.get(EnergyCoefficient.class, "object").get().getEnergyType());
    assertEquals("gas", service.get(EnergyCoefficient.class, "jsonObject").get().getEnergyType());

    Optional<EnergyCoefficient> object = service.getAs(EnergyCoefficient.class, "object");
    Optional<List<EnergyCoefficient>> objectList = service.getList(EnergyCoefficient.class, "objectList");
    Optional<List<Object>> rawList = service.getList("rawList");

    assertTrue(object.isPresent());
    assertEquals("electricity", object.get().getEnergyType());
    assertEquals(Double.valueOf(1.23d), object.get().getCoefficient());

    assertTrue(objectList.isPresent());
    assertEquals(2, objectList.get().size());
    assertEquals("coal", objectList.get().get(1).getEnergyType());

    assertTrue(rawList.isPresent());
    assertEquals("electricity", rawList.get().get(0));
    assertEquals(Integer.valueOf(12), rawList.get().get(1));
    assertEquals(Double.valueOf(1.5d), rawList.get().get(2));
    assertEquals(coal, rawList.get().get(3));

    assertEquals("gas", service.getAs(EnergyCoefficient.class, "jsonObject").get().getEnergyType());
    assertEquals("steam", service.getList(EnergyCoefficient.class, "jsonObjectList").get().get(0).getEnergyType());
    assertEquals("steam", service.getListAs(EnergyCoefficient.class, "jsonObjectList").get().get(0).getEnergyType());
    assertEquals("sms", service.getList("jsonRawList").get().get(0));
    assertEquals(Integer.valueOf(2), service.getList("jsonRawList").get().get(1));
    assertEquals(Map.of("channel", "call"), service.getList("jsonRawList").get().get(2));
  }

  @Test
  public void requiredAndDefaultMethods_includeKeyAndTypeContext() {
    ConfigurationService service = configurationService(Map.of(
        "raw", "configured-value",
        "number", "7",
        "invalidNumber", "not-a-number"
    ));

    assertEquals("configured-value", service.getRequired("raw"));
    assertEquals(Integer.valueOf(7), service.getRequired(Integer.class, "number"));
    assertEquals("fallback", service.getOrDefault("missing", "fallback"));
    assertEquals(Integer.valueOf(9), service.getOrDefault(Integer.class, "missing", 9));
    assertEquals(Integer.valueOf(9), service.getOrDefault(Integer.class, "invalidNumber", 9));

    PlatformException missingException = assertThrows(PlatformException.class,
        () -> service.getRequired("missing"));
    PlatformException invalidException = assertThrows(PlatformException.class,
        () -> service.getRequired(Integer.class, "invalidNumber"));

    assertTrue(missingException.getMessage().contains("missing"));
    assertTrue(invalidException.getMessage().contains("invalidNumber"));
    assertTrue(invalidException.getMessage().contains("Integer"));
  }

  private static ConfigurationService configurationService(Map<String, Object> values) {
    return new ConfigurationService() {
      @Override
      public <T> Optional<T> get(String key) {
        return (Optional<T>) Optional.ofNullable(values.get(key));
      }

      @Override
      public Map<String, Object> getValues(ConfigScope scope) {
        return Collections.emptyMap();
      }
    };
  }

  public static class EnergyCoefficient {
    private String energyType;

    private Double coefficient;

    public String getEnergyType() {
      return energyType;
    }

    public void setEnergyType(String energyType) {
      this.energyType = energyType;
    }

    public Double getCoefficient() {
      return coefficient;
    }

    public void setCoefficient(Double coefficient) {
      this.coefficient = coefficient;
    }
  }
}
