package com.ouroboros.dev.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.dev.app.model.DebugKeyGenerationRequest;
import com.ouroboros.dev.app.model.DevKeyMaterialDTO;
import com.ouroboros.dev.app.model.DevKeyPluginConfigDTO;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.app.service.runtime.AuthenticationPrincipalTestAppProfileResolver;
import com.ouroboros.dev.app.service.runtime.DebugKeyGenerationService;
import com.ouroboros.dev.app.service.runtime.DebugKeyManagementService;
import com.ouroboros.dev.app.service.runtime.DevKeyStore;
import com.ouroboros.dev.app.service.runtime.InMemoryDevKeyDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.web.WebApiResult;

class DevKeyManagementControllerTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @AfterEach
  void tearDown() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldManageDevKeyFromAppScopedController() throws Exception {
    DevKeyManagementController controller = createController("alice.dev@example.com");

    WebApiResult<DevKeyMaterialDTO> generated = controller.generate("crm", request("integration dev key", 7));
    assertEquals(0, generated.getCode());
    assertEquals("crm", generated.getData().getAppName());
    assertEquals("alice.dev@example.com", generated.getData().getUserIdentity());
    assertEquals("alice_dev_example_com", generated.getData().getProfileId());
    assertTrue(generated.getData().getDevKey().matches("[0-9a-f]{32}"));

    WebApiResult<DevKeyMaterialDTO> current = controller.get("crm");
    assertEquals(0, current.getCode());
    assertEquals(generated.getData().getDevKey().substring(0, 8) + "****", current.getData().getDevKey());

    WebApiResult<DevKeyPluginConfigDTO> config = controller.getPluginConfig("crm");
    assertEquals(0, config.getCode());
    assertEquals(1, config.getData().getSchemaVersion());
    assertEquals("https://dev.ouroboros.example", config.getData().getDevelopmentServerUrl());
    assertEquals("crm", config.getData().getAppName());
    assertEquals(generated.getData().getDevKey(), config.getData().getDevKey());

    ResponseEntity<byte[]> download = controller.downloadPluginConfig("crm");
    assertEquals(200, download.getStatusCodeValue());
    assertEquals(MediaType.APPLICATION_JSON, download.getHeaders().getContentType());
    assertTrue(download.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("attachment"));
    Map<String, Object> downloadedConfig = OBJECT_MAPPER.readValue(download.getBody(), new TypeReference<>() {});
    assertEquals(1, downloadedConfig.get("schemaVersion"));
    assertEquals("https://dev.ouroboros.example", downloadedConfig.get("developmentServerUrl"));
    assertEquals("crm", downloadedConfig.get("appName"));
    assertEquals(generated.getData().getDevKey(), downloadedConfig.get("devKey"));

    WebApiResult<DevKeyMaterialDTO> otherAppGenerated = controller.generate("erp", request("erp dev key", 7));
    assertEquals(0, otherAppGenerated.getCode());
    assertEquals("erp", otherAppGenerated.getData().getAppName());
    assertTrue(!generated.getData().getDevKey().equals(otherAppGenerated.getData().getDevKey()));

    WebApiResult<DevKeyPluginConfigDTO> otherAppConfig = controller.getPluginConfig("erp");
    assertEquals("erp", otherAppConfig.getData().getAppName());
    assertEquals(otherAppGenerated.getData().getDevKey(), otherAppConfig.getData().getDevKey());
    assertEquals(generated.getData().getDevKey(), controller.getPluginConfig("crm").getData().getDevKey());

    WebApiResult<DevKeyMaterialDTO> rotated = controller.rotate("crm", request("rotated", 14));
    assertEquals(0, rotated.getCode());
    assertTrue(!generated.getData().getDevKey().equals(rotated.getData().getDevKey()));

    assertEquals(204, controller.delete("crm").getStatusCodeValue());
    assertEquals(null, controller.get("crm").getData());
  }

  @Test
  void shouldRejectDevKeyRequestWhenAppDoesNotExist() throws Exception {
    DevKeyManagementController controller = createController("alice", false);

    assertThrows(BusinessException.class, () -> controller.generate("crm", request("missing app", 7)));
    assertThrows(BusinessException.class, () -> controller.get("crm"));
    assertThrows(BusinessException.class, () -> controller.rotate("crm", request("missing app", 7)));
    assertThrows(BusinessException.class, () -> controller.delete("crm"));
    assertThrows(BusinessException.class, () -> controller.getPluginConfig("crm"));
    assertThrows(BusinessException.class, () -> controller.downloadPluginConfig("crm"));
  }

  private static DevKeyManagementController createController(String principal) {
    return createController(principal, true);
  }

  private static DevKeyManagementController createController(String principal, boolean appExists) {
    AppManagementService appManagementService = mock(AppManagementService.class);
    when(appManagementService.appExists(anyString())).thenReturn(appExists);
    return new DevKeyManagementController(createManagementService(principal, appManagementService), OBJECT_MAPPER);
  }

  private static DebugKeyManagementService createManagementService(String principal,
                                                                   AppManagementService appManagementService) {
    AuthenticationPrincipalTestAppProfileResolver resolver = new AuthenticationPrincipalTestAppProfileResolver();
    CurrentSecurityContextHolder.setDelegate(new CurrentSecurityContext() {
      @Override
      public Optional<com.ouroboros.security.identity.SecurityIdentity> findIdentity() {
        return Optional.empty();
      }

      @Override
      public Optional<OuroborosAuthentication> findAuthentication() {
        return Optional.of(OuroborosAuthenticationHolder.adapt(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, "n/a", Collections.emptyList())));
      }
    });

    DevKeyStore store = new DevKeyStore(new InMemoryDevKeyDataModel());
    DebugKeyGenerationService generationService = new DebugKeyGenerationService(java.time.Duration.ofDays(90));
    return new DebugKeyManagementService(
        generationService,
        store,
        resolver,
        appManagementService,
        "https://dev.ouroboros.example");
  }

  private static DebugKeyGenerationRequest request(String description, Integer expiryDays) {
    DebugKeyGenerationRequest request = new DebugKeyGenerationRequest();
    request.setDescription(description);
    request.setExpiryDays(expiryDays);
    return request;
  }
}
