package com.ouroboros.statistics.reporting.dev.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Try;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel;
import com.ouroboros.statistics.reporting.dev.service.impl.DefaultDatasetDevConfigService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasetDevConfigServiceTest {
  @Mock
  private TypedDataModel<String, DatasetDevConfigModel> dataModel;

  private DefaultDatasetDevConfigService service;

  @BeforeEach
  void setUp() {
    service = new DefaultDatasetDevConfigService() {
      @Override
      protected TypedDataModel<String, DatasetDevConfigModel> getDataModel() {
        return dataModel;
      }
    };
  }

  @Test
  void saveRejectsIncompleteConnectionReferences() {
    var config = devConfig(null, "demo-app", "demo.sales");
    var reference = new com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection();
    config.setTestConnection(reference);
    for (String kind : java.util.List.of("platform", "runtime", "jdbc")) {
      reference.setKind(kind);
      assertTrue(service.save(config).isFailure());
    }
    org.mockito.Mockito.verifyNoInteractions(dataModel);
  }

  @Test
  void switchingToApplicationClearsTheSavedConnectionReference() {
    var config = devConfig(null, "DEMO-App", "demo.SALES");
    var reference = new com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection();
    reference.setKind("application");
    reference.setPlatformDataSourceName("obsolete");
    reference.setRuntimeId("old-runtime");
    config.setTestConnection(reference);
    when(dataModel.query(queryStatement("demo-app", "demo.sales"))).thenReturn(Try.success(java.util.List.of()));
    when(dataModel.insert(config)).thenReturn(Try.success(config));
    var saved = service.save(config).get();
    assertEquals("demo-app", saved.getAppName());
    assertEquals("demo.sales", saved.getDatasetName());
    org.junit.jupiter.api.Assertions.assertNull(saved.getTestConnection().getPlatformDataSourceName());
    org.junit.jupiter.api.Assertions.assertNull(saved.getTestConnection().getRuntimeId());
  }

  @Test
  void findByDatasetUsesAppNameAndDatasetNameAsUniqueLookup() {
    DatasetDevConfigModel devConfig = devConfig("config-1", "demo-app", "demo.sales");
    when(dataModel.query(queryStatement("demo-app", "demo.sales")))
        .thenReturn(Try.success(Collections.singletonList(devConfig)));

    Optional<DatasetDevConfigModel> result = service.findByDataset("DEMO-App", "demo.SALES");

    assertEquals(Optional.of(devConfig), result);
    verify(dataModel).query(queryStatement("demo-app", "demo.sales"));
  }

  @Test
  void findByDatasetReturnsEmptyForBlankLookupInput() {
    assertFalse(service.findByDataset("", "demo.sales").isPresent());
    assertFalse(service.findByDataset("demo-app", " ").isPresent());
  }

  @Test
  void findByDatasetRejectsDuplicateDevConfigRows() {
    when(dataModel.query(queryStatement("demo-app", "demo.sales")))
        .thenReturn(Try.success(java.util.Arrays.asList(
            devConfig("config-1", "demo-app", "demo.sales"),
            devConfig("config-2", "demo-app", "demo.sales")
        )));

    BusinessException error = assertThrows(BusinessException.class,
        () -> service.findByDataset("demo-app", "demo.sales"));

    assertEquals("dataset dev config is not unique: demo-app/demo.sales", error.getMessage());
  }

  @Test
  void saveInsertsNewDevConfigWhenUniqueKeyDoesNotExist() {
    DatasetDevConfigModel devConfig = devConfig(null, "demo-app", "demo.sales");
    when(dataModel.query(queryStatement("demo-app", "demo.sales")))
        .thenReturn(Try.success(Collections.emptyList()));
    when(dataModel.insert(devConfig)).thenReturn(Try.success(devConfig));

    DatasetDevConfigModel saved = service.save(devConfig).get();

    assertSame(devConfig, saved);
    verify(dataModel).insert(devConfig);
  }

  @Test
  void saveUpdatesExistingDevConfigWhenUniqueKeyAlreadyExists() {
    DatasetDevConfigModel existing = devConfig("config-1", "demo-app", "demo.sales");
    DatasetDevConfigModel replacement = devConfig(null, "demo-app", "demo.sales");
    when(dataModel.query(queryStatement("demo-app", "demo.sales")))
        .thenReturn(Try.success(Collections.singletonList(existing)));
    when(dataModel.update("config-1", replacement)).thenReturn(Try.success(1L));

    DatasetDevConfigModel saved = service.save(replacement).get();

    assertSame(replacement, saved);
    assertEquals("config-1", replacement.getId());
    verify(dataModel).update("config-1", replacement);
  }

  @Test
  void saveRejectsMissingUniqueKeyParts() {
    DatasetDevConfigModel devConfig = devConfig(null, "demo-app", "");

    assertThrows(BusinessException.class, () -> service.save(devConfig).get());
  }

  private static DatasetDevConfigModel devConfig(String id, String appName, String datasetName) {
    DatasetDevConfigModel devConfig = new DatasetDevConfigModel();
    devConfig.setId(id);
    devConfig.setAppName(appName);
    devConfig.setDatasetName(datasetName);
    devConfig.setDatasource("dev-datasource");
    devConfig.setParameters(Collections.<String, Object>singletonMap("keyword", "dev-keyword"));
    return devConfig;
  }

  private static Map<String, Object> queryStatement(String appName, String datasetName) {
    return Collections.<String, Object>singletonMap(Keyword.WHERE.toString(), where(appName, datasetName));
  }

  private static Map<String, Object> where(String appName, String datasetName) {
    Map<String, Object> where = new HashMap<String, Object>();
    where.put("appName", appName);
    where.put("datasetName", datasetName);
    return where;
  }
}
