package com.ouroboros.devhelper.metadata.collector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.data.model.*;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

class DataModelCodeIntelligenceCollectorTest {
  @Test void collectsActualFieldTypesWithoutReadingDataOrDefaults() {
    var provider = mock(DataModelMetaProvider.class);
    when(provider.getDataModelMetaMap()).thenReturn(Map.of("sales.Order", Json.toBean(DataModelMeta.class, "{\"name\":\"Order\"}")));
    var model = mock(DataModel.class);
    var field = mock(DataModelField.class);
    when(field.getName()).thenReturn("customerId");
    ValueType<String> valueType = new ValueType<>() {
      public String getName() { return "string"; }
      public String getLabel() { return "String"; }
      public Class<String> getType() { return String.class; }
      public String convert(Object value) { throw new AssertionError("Must not convert runtime values"); }
    };
    doReturn(valueType).when(field).getValueType();
    when(model.getFields()).thenReturn(List.of(field));
    try (var spi = mockStatic(SPI.class); var models = mockStatic(DataModelCenter.class)) {
      spi.when(() -> SPI.getSortedServiceStream(DataModelMetaProvider.class)).thenAnswer(call -> Stream.of(provider));
      models.when(() -> DataModelCenter.getDataModel("sales.Order")).thenReturn(Optional.of(model));
      var result = new DataModelMetadataCollector().getAllMetadata();
      var details = Json.toBean(JsonNode.class, Json.toJsonString(result.getFirst().getDetails()));
      assertEquals("string", details.path("recordSchema").path("properties").path("customerId").path("type").asText());
      assertEquals("java", details.path("instanceSchema").path("x-representation").asText());
      verify(model).getFields();
      verifyNoMoreInteractions(model);
      verify(field, never()).getDefaultValue(anyMap());
    }
  }

  @Test void missingRuntimeModelKeepsMetadataWithoutInventingTypes() {
    var provider = mock(DataModelMetaProvider.class);
    when(provider.getDataModelMetaMap()).thenReturn(Map.of("Order", Json.toBean(DataModelMeta.class, "{\"name\":\"Order\"}")));
    try (var spi = mockStatic(SPI.class); var models = mockStatic(DataModelCenter.class)) {
      spi.when(() -> SPI.getSortedServiceStream(DataModelMetaProvider.class)).thenAnswer(call -> Stream.of(provider));
      models.when(() -> DataModelCenter.getDataModel("Order")).thenReturn(Optional.empty());
      var result = new DataModelMetadataCollector().getAllMetadata();
      assertEquals("Order", result.getFirst().getName());
      assertFalse(result.getFirst().getDetails().containsKey("recordSchema"));
    }
  }
}
