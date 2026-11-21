package com.ouroboros.notification.controller;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.notification.NotificationDispatcherImplTest;
import com.ouroboros.notification.adapter.NotificationAdapterSupplier;
import com.ouroboros.notification.controller.fixture.SchemaExposureNotificationAdapterSupplier;
import com.ouroboros.util.SPI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationAdapterSchemaControllerHttpStatusTest {
  private org.mockito.MockedStatic<SPI> spiBinding;


  @BeforeEach
  void seedNotificationAdapterSuppliers() {
    spiBinding = org.mockito.Mockito.mockStatic(SPI.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    spiBinding.when(() -> SPI.getCachedServiceStream(NotificationAdapterSupplier.class))
        .thenAnswer(ignored -> java.util.stream.Stream.of(
            new NotificationDispatcherImplTest.TestNotificationAdapterSupplier(),
            new SchemaExposureNotificationAdapterSupplier()));
  }

  @AfterEach
  void clearSpiCache() throws Exception {
    if (spiBinding != null) { spiBinding.close(); spiBinding = null; }
    SPI.clearCache();
  }

  @Test
  void getAdapterSchemaShouldReturnHttp200AndExposeRegisteredAdapters() throws Exception {
    NotificationAdapterSchemaController controller = new NotificationAdapterSchemaController();

    var mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    mockMvc.perform(get("/api/notification/adapter-schema"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.test-channel").exists())
        .andExpect(jsonPath("$.data.test-channel.schema").isMap())
        .andExpect(jsonPath("$.data.test-channel.options").isMap())
        .andExpect(jsonPath("$.data.test-channel.schema").isEmpty())
        .andExpect(jsonPath("$.data.test-channel.options.endpoint").value("string"))
        .andExpect(jsonPath("$.data.test-channel.options.sender").value("string"));
  }

  @Test
  void getAdapterSchemaShouldExposeInheritedOptionFieldsFromSchemaFixtureAdapter() throws Exception {
    NotificationAdapterSchemaController controller = new NotificationAdapterSchemaController();

    var mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    mockMvc.perform(get("/api/notification/adapter-schema"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.schema-fixture-channel.schema").isMap())
        .andExpect(jsonPath("$.data.schema-fixture-channel.schema.token").value("string"))
        .andExpect(jsonPath("$.data.schema-fixture-channel.options").isMap())
        .andExpect(jsonPath("$.data.schema-fixture-channel.options.endpoint").value("string"))
        .andExpect(jsonPath("$.data.schema-fixture-channel.options.sender").value("string"));
  }

  @Test
  void managementCatalogIncludesInstalledAndMissingReferencedChannelsWithoutConfigurationValues() throws Exception {
    var controller = new NotificationAdapterSchemaController();
    var management = org.mockito.Mockito.mock(com.ouroboros.notification.service.NotificationTemplateRuntimeManagementService.class);
    org.springframework.test.util.ReflectionTestUtils.setField(controller, "management", management);
    var template = Map.<String, Object>of("channel", Map.of("managed-test", Map.of(), "missing-channel", Map.of()));
    var definition = new com.ouroboros.notification.service.model.NotificationTemplateManagement.Definition(
        "notice", template, template, false, true, "DEFAULT", "jar", "jar", "d:1", "NONE", false,
        null, null, Map.of(), List.of(), List.of());
    org.mockito.Mockito.when(management.getRuntimeDefinitions()).thenReturn(List.of(definition));
    try (var adapters = org.mockito.Mockito.mockStatic(com.ouroboros.notification.adapter.NotificationAdapterCenter.class)) {
      adapters.when(() -> com.ouroboros.notification.adapter.NotificationAdapterCenter.getAllSuppliers(java.util.List.of())).thenReturn(Map.of(
          "managed-test", new com.ouroboros.notification.template.NotificationTemplateDefinitionValidatorTest.Supplier(),
          "test-channel", new NotificationDispatcherImplTest.TestNotificationAdapterSupplier()));
      var mvc = MockMvcBuilders.standaloneSetup(controller).build();
      mvc.perform(get("/api/notification/adapter-schema/management"))
          .andExpect(status().isOk())
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
          .andExpect(jsonPath("$.data.length()").value(3))
          .andExpect(jsonPath("$.data[0].id").value("managed-test"))
          .andExpect(jsonPath("$.data[0].manageable").value(true))
          .andExpect(jsonPath("$.data[0].referenceCount").value(1))
          .andExpect(jsonPath("$.data[0].schema.properties.body.title").value("正文"))
          .andExpect(jsonPath("$.data[0].options.properties.volume.maximum").value(100))
          .andExpect(jsonPath("$.data[0].value").doesNotExist())
          .andExpect(jsonPath("$.data[1].id").value("missing-channel"))
          .andExpect(jsonPath("$.data[1].installed").value(false))
          .andExpect(jsonPath("$.data[1].referenceCount").value(1))
          .andExpect(jsonPath("$.data[2].manageable").value(false));
    }
  }
}
