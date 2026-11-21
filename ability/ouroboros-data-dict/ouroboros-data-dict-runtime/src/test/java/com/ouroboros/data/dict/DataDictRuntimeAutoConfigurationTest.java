package com.ouroboros.data.dict;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.AppContext;
import com.ouroboros.data.dict.provider.AppHomeDataDictMetadataProvider;
import com.ouroboros.data.dict.provider.DataDictMetadataProvider;
import com.ouroboros.data.dict.pojo.FlatDataDict;
import com.ouroboros.data.dict.provider.JarDataDictMetadataProvider;
import com.ouroboros.data.dict.service.DataDictCoreService;
import com.ouroboros.data.dict.service.DataDictItemService;
import com.ouroboros.data.model.DataOperationIdentityProviders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataDictRuntimeAutoConfigurationTest {

  @TempDir
  Path tempDir;

  @Test
  void typedDataDictFileOverridesLegacyFileWithSameName() throws Exception {
    Path dictDir = tempDir.resolve("data-dict");
    Files.createDirectories(dictDir);
    Files.writeString(dictDir.resolve("customer.json"), dictJson("customer", "legacy title"));
    Files.writeString(dictDir.resolve("customer.dict.json"), dictJson("customer", "typed title"));

    AppContext appContext = mock(AppContext.class);
    DataDictService dataDictService = mock(DataDictService.class);
    DataDictCoreService dataDictCoreService = mock(DataDictCoreService.class);
    DataDictItemService dataDictItemService = mock(DataDictItemService.class);
    when(appContext.getHomePath()).thenReturn(tempDir.toString());
    when(dataDictCoreService.searchAll()).thenReturn(Collections.emptyList());
    when(dataDictCoreService.drop("customer")).thenReturn(true);
    when(dataDictItemService.dropByDict("customer")).thenReturn(0L);
    when(dataDictService.add(any(DataDict.class))).thenAnswer(invocation -> invocation.getArgument(0));

    DataDictRuntimeAutoConfiguration configuration = new DataDictRuntimeAutoConfiguration();
    ReflectionTestUtils.setField(configuration, "dataDictService", dataDictService);
    ReflectionTestUtils.setField(configuration, "dataDictCoreService", dataDictCoreService);
    ReflectionTestUtils.setField(configuration, "dataDictItemService", dataDictItemService);
    ReflectionTestUtils.setField(configuration, "dataDictMetadataProviders", Collections.singletonList(new AppHomeDataDictMetadataProvider(appContext)));

    configuration.init();

    ArgumentCaptor<DataDict> dictCaptor = ArgumentCaptor.forClass(DataDict.class);
    verify(dataDictService).add(dictCaptor.capture());
    assertEquals("typed title", dictCaptor.getValue().getTitle());
  }

  @Test
  void initRunsDataDictWriteUnderSystemDataOperationIdentity() {
    FlatDataDict dict = new FlatDataDict();
    dict.setName("system-context");
    dict.setTitle("System Context");
    dict.setType("STATIC");
    dict.setGroup("test");
    dict.setEditable(false);
    dict.setTreeMode(false);
    dict.setItems(Collections.emptyList());

    DataDictService dataDictService = mock(DataDictService.class);
    DataDictCoreService dataDictCoreService = mock(DataDictCoreService.class);
    DataDictItemService dataDictItemService = mock(DataDictItemService.class);
    when(dataDictCoreService.searchAll()).thenAnswer(invocation -> {
      assertFalse(DataOperationIdentityProviders.findCurrentOperator().isPresent());
      return Collections.emptyList();
    });
    when(dataDictCoreService.drop("system-context")).thenAnswer(invocation -> {
      assertEquals(DataOperationIdentityProviders.SYSTEM_OPERATOR,
          DataOperationIdentityProviders.findCurrentOperator().orElseThrow());
      return true;
    });
    when(dataDictItemService.dropByDict("system-context")).thenAnswer(invocation -> {
      assertEquals(DataOperationIdentityProviders.SYSTEM_OPERATOR,
          DataOperationIdentityProviders.findCurrentOperator().orElseThrow());
      return 0L;
    });
    when(dataDictService.add(any(DataDict.class))).thenAnswer(invocation -> {
      assertEquals(DataOperationIdentityProviders.SYSTEM_OPERATOR,
          DataOperationIdentityProviders.findCurrentOperator().orElseThrow());
      return invocation.getArgument(0);
    });
    DataDictMetadataProvider metadataProvider = () -> {
      assertFalse(DataOperationIdentityProviders.findCurrentOperator().isPresent());
      return Map.of(dict.getName(), dict);
    };

    DataDictRuntimeAutoConfiguration configuration = new DataDictRuntimeAutoConfiguration();
    ReflectionTestUtils.setField(configuration, "dataDictService", dataDictService);
    ReflectionTestUtils.setField(configuration, "dataDictCoreService", dataDictCoreService);
    ReflectionTestUtils.setField(configuration, "dataDictItemService", dataDictItemService);
    ReflectionTestUtils.setField(configuration, "dataDictMetadataProviders", Collections.singletonList(metadataProvider));

    configuration.init();

    verify(dataDictService).add(dict);
    assertFalse(DataOperationIdentityProviders.findCurrentOperator().isPresent());
  }

  @Test
  void jarDataDictProviderLoadsTypedMetadataFromJarMetadataDirectory() {
    var provider = new JarDataDictMetadataProvider();

    var dicts = provider.getDataDicts();

    assertTrue(dicts.containsKey("jar-customer"));
    assertEquals("Jar Customer", dicts.get("jar-customer").getTitle());
  }

  private String dictJson(String name, String title) {
    return """
        {
          "name": "%s",
          "title": "%s",
          "type": "STATIC",
          "group": "test",
          "editable": false,
          "treeMode": false,
          "items": []
        }
        """.formatted(name, title);
  }
}
