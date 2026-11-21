package com.ouroboros.configuration.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ouroboros.Application;
import com.ouroboros.configuration.ConfigurationRevision;
import com.ouroboros.configuration.model.RuntimeConfigurationValueModel;
import com.ouroboros.configuration.service.RuntimeConfigurationService;
import com.ouroboros.configuration.validation.ConfigurationValueValidator;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;

import io.vavr.control.Try;

@SpringBootTest(classes = {Application.class})
public class RuntimeConfigurationServiceImplTest {

  @Autowired
  private RuntimeConfigurationService runtimeConfigurationService;

  @Autowired
  private RuntimeConfigurationServiceImpl runtimeConfigurationServiceImpl;

  @MockitoBean
  private TypedDataModel<String, RuntimeConfigurationValueModel> configurationValueDataModel;

  @MockitoBean
  private ConfigurationValueValidator configurationValueValidator;

  @BeforeEach
  public void setUp() {
    RuntimeConfigurationValueModel test0Value = new RuntimeConfigurationValueModel();
    test0Value.setKey("test0");
    test0Value.setValue("stored-value");
    test0Value.setUpdateBy("test");
    test0Value.setUpdateAt(LocalDateTime.now());

    when(configurationValueDataModel.query(anyMap()))
        .thenReturn(Try.success(Arrays.asList(null, test0Value)));
    when(configurationValueDataModel.get("list0")).thenReturn(Try.success(null));
    when(configurationValueDataModel.insert(any())).thenReturn(Try.success(new RuntimeConfigurationValueModel()));
    when(configurationValueValidator.supports("test0")).thenReturn(true);
    doThrow(new PlatformException("custom configuration validation failed"))
        .when(configurationValueValidator).validate("rejected-value");
    runtimeConfigurationServiceImpl.clearCache();
  }

  @Test
  public void getAllConfigurationModel_ignoresNullValueModels() {
    var modelMap = runtimeConfigurationService.getAllConfigurationModel();

    assertEquals("stored-value", modelMap.get("test0").getValue());
    assertEquals(ConfigurationRevision.legacyStoredRevision("test0", "stored-value"),
        modelMap.get("test0").getRevision());
    assertEquals("key0-default", modelMap.get("key0").getValue());
    assertEquals(ConfigurationRevision.defaultRevision("key0", "key0-default"),
        modelMap.get("key0").getRevision());
  }

  @Test
  public void updateRunsDomainValidatorBeforeWritingValue() {
    assertThrows(PlatformException.class,
        () -> runtimeConfigurationService.update(
            java.util.Map.of("test0", "rejected-value"), "test"));

    verify(configurationValueDataModel, never()).insert(any());
    verify(configurationValueDataModel, never()).update(anyString(), any(RuntimeConfigurationValueModel.class));
  }

  @Test
  public void updateAllowsEmptyCollectionForNonNullableListConfiguration() {
    runtimeConfigurationService.update(java.util.Map.of("list0", List.of()), "test");

    verify(configurationValueDataModel).insert(any(RuntimeConfigurationValueModel.class));
  }

  @Test
  public void batchCasRejectsMissingRevisionBeforeWritingAnyValue() {
    var error = assertThrows(BusinessException.class,
        () -> runtimeConfigurationService.update(
            java.util.Map.of("test0", "new", "list0", List.of()),
            "test",
            java.util.Map.of("test0", "stored-token")));

    assertEquals(400, error.getErrorCode());
    assertTrue(error.getMessage().contains("CONFIGURATION_REVISION_REQUIRED"));
    verify(configurationValueDataModel, never()).insert(any());
    verify(configurationValueDataModel, never()).update(anyMap(), anyMap());
  }
}
