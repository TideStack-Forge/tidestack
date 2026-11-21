package com.ouroboros.configuration.controller;

import org.junit.jupiter.api.Test;

import com.ouroboros.configuration.controller.ConfigurationController.ConfigurationModelVO;
import com.ouroboros.configuration.ConfigurationRevision;
import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.model.ConfigurationModel;
import com.ouroboros.configuration.model.ConfigurationValueModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigurationModelVORawValueTest {

  @Test
  void rawValueShouldReturnDatabaseValueWhenPresent() {
    ConfigurationMetadata metadata = mock(ConfigurationMetadata.class);
    when(metadata.getDefaultValue()).thenReturn("default-${placeholder}");
    when(metadata.getValueType()).thenReturn(ConfigValueType.STRING);

    ConfigurationValueModel configValue = mock(ConfigurationValueModel.class);
    when(configValue.getValue()).thenReturn("custom-${placeholder}");

    ConfigurationModel model = new ConfigurationModel("test.key", "computed-value", metadata, configValue);
    ConfigurationModelVO vo = new ConfigurationModelVO(model);

    assertEquals("custom-${placeholder}", vo.getRawValue());
  }

  @Test
  void rawValueShouldReturnDefaultValueWhenDatabaseValueAbsent() {
    ConfigurationMetadata metadata = mock(ConfigurationMetadata.class);
    when(metadata.getDefaultValue()).thenReturn("default-${placeholder}");
    when(metadata.getValueType()).thenReturn(ConfigValueType.STRING);

    ConfigurationModel model = new ConfigurationModel("test.key", "computed-value", metadata, null);
    ConfigurationModelVO vo = new ConfigurationModelVO(model);

    assertEquals("default-${placeholder}", vo.getRawValue());
  }

  @Test
  void rawValueShouldPreserveEscapedPlaceholders() {
    ConfigurationMetadata metadata = mock(ConfigurationMetadata.class);
    when(metadata.getDefaultValue()).thenReturn("/api/preview?id=$${fileId}&token=$${accessToken}");
    when(metadata.getValueType()).thenReturn(ConfigValueType.STRING);

    ConfigurationValueModel configValue = mock(ConfigurationValueModel.class);
    when(configValue.getValue()).thenReturn("/api/preview?id=$${fileId}&token=$${accessToken}");

    ConfigurationModel model = new ConfigurationModel("test.key", "/api/preview?id=&token=", metadata, configValue);
    ConfigurationModelVO vo = new ConfigurationModelVO(model);

    assertEquals("/api/preview?id=$${fileId}&token=$${accessToken}", vo.getRawValue());
  }

  @Test
  void valueShouldReturnComputedValue() {
    ConfigurationMetadata metadata = mock(ConfigurationMetadata.class);
    when(metadata.getDefaultValue()).thenReturn("default-${placeholder}");
    when(metadata.getValueType()).thenReturn(ConfigValueType.STRING);

    ConfigurationValueModel configValue = mock(ConfigurationValueModel.class);
    when(configValue.getValue()).thenReturn("custom-${placeholder}");

    ConfigurationModel model = new ConfigurationModel("test.key", "computed-value", metadata, configValue);
    ConfigurationModelVO vo = new ConfigurationModelVO(model);

    assertEquals("computed-value", vo.getValue());
  }

  @Test
  void revisionShouldBeExposedForCasRequests() {
    ConfigurationMetadata metadata = mock(ConfigurationMetadata.class);
    when(metadata.getDefaultValue()).thenReturn("default");
    when(metadata.getValueType()).thenReturn(ConfigValueType.STRING);

    String revision = ConfigurationRevision.defaultRevision("test.key", "default");
    ConfigurationModel model = new ConfigurationModel("test.key", "computed-value", metadata, null, revision);

    assertEquals(revision, new ConfigurationModelVO(model).getRevision());
  }
}
