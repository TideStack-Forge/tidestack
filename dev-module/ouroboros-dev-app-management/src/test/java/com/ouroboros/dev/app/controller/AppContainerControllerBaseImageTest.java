package com.ouroboros.dev.app.controller;

import java.util.Collections;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.BaseImageReference;
import com.ouroboros.dev.app.model.RuntimeCreateInitDTO;
import com.ouroboros.dev.app.service.AppContainerService;
import com.ouroboros.dev.app.service.runtime.AppRuntimeInitService;
import com.ouroboros.dev.app.service.runtime.TestAppProfileResolver;
import com.ouroboros.exception.BusinessException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppContainerControllerBaseImageTest {

  private final AppContainerService appContainerService = mock(AppContainerService.class);
  private final AppRuntimeInitService appRuntimeInitService = mock(AppRuntimeInitService.class);
  private final TestAppProfileResolver testAppProfileResolver = mock(TestAppProfileResolver.class);

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var controller = new AppContainerController();
    ReflectionTestUtils.setField(controller, "appContainerService", appContainerService);
    ReflectionTestUtils.setField(controller, "appRuntimeInitService", appRuntimeInitService);
    ReflectionTestUtils.setField(controller, "testAppProfileResolver", testAppProfileResolver);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void getBaseImages_returnsImageNameAndTagShape() throws Exception {
    when(appContainerService.getBaseImages()).thenReturn(Try.success(Collections.singletonList(
        new BaseImageReference(
            "registry.example.com/team/ouroboros-mothership",
            "latest",
            "registry.example.com/team/ouroboros-mothership:latest"
        )
    )));

    mockMvc.perform(get("/api/app/container/base-images"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items[0].imageName").value("registry.example.com/team/ouroboros-mothership:latest"))
        .andExpect(jsonPath("$.data.items[0].tag").value("latest"))
        .andExpect(jsonPath("$.data.items[0].repositoryWithName").doesNotExist());
  }

  @Test
  void getImages_returnsCompatibleSelectOptions() throws Exception {
    when(appContainerService.getImages()).thenReturn(Try.success(Collections.singletonList(
        "registry.example.com/team/ouroboros-mothership:latest"
    )));

    mockMvc.perform(get("/api/app/container/images"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items[0].value").value("registry.example.com/team/ouroboros-mothership:latest"))
        .andExpect(jsonPath("$.data.items[0].label").value("ouroboros-mothership:latest"));
  }

  @Test
  void getImages_propagatesBaseImageQueryFailure() throws Exception {
    when(appContainerService.getImages()).thenReturn(Try.failure(new BusinessException("默认基座镜像配置无效")));

    mockMvc.perform(get("/api/app/container/images"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").isNotEmpty())
        .andExpect(jsonPath("$.msg").value("默认基座镜像配置无效"));
  }

  @Test
  void getCreateInit_returnsRuntimeInitPayload() throws Exception {
    RuntimeCreateInitDTO init = new RuntimeCreateInitDTO();
    var portBindings = new LinkedHashMap<String, Integer>();
    portBindings.put("web", 18080);
    portBindings.put("api", 18081);
    var configFile = new AppContainerRequest.ConfigFile();
    configFile.setReset(false);
    init.setImage("registry.example.com/team/ouroboros-mothership:latest");
    init.setPortBindings(portBindings);
    init.setConfigFile(configFile);
    when(testAppProfileResolver.resolveForCreate(any())).thenReturn("alice");
    when(appRuntimeInitService.buildTestAppCreateInit(null, "alice")).thenReturn(init);

    mockMvc.perform(get("/api/app/container/create-init"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.image").value("registry.example.com/team/ouroboros-mothership:latest"))
        .andExpect(jsonPath("$.data.portBindings.web").value(18080))
        .andExpect(jsonPath("$.data.configFile.isReset").value(false));
  }

  @Test
  void getCreateInit_keepsEmptyImageWhenRuntimeInitReturnsEmptyImage() throws Exception {
    RuntimeCreateInitDTO init = new RuntimeCreateInitDTO();
    init.setImage("");
    init.setPortBindings(Collections.singletonMap("web", 18080));
    when(testAppProfileResolver.resolveForCreate(any())).thenReturn("alice");
    when(appRuntimeInitService.buildTestAppCreateInit(null, "alice")).thenReturn(init);

    mockMvc.perform(get("/api/app/container/create-init"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.image").value(""));
  }

  @Test
  void getCreateInit_failsWhenRuntimeInitThrowsBusinessException() throws Exception {
    when(testAppProfileResolver.resolveForCreate(any())).thenReturn("alice");
    when(appRuntimeInitService.buildTestAppCreateInit(null, "alice"))
        .thenThrow(new BusinessException("默认基座镜像配置无效"));

    mockMvc.perform(get("/api/app/container/create-init"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").isNotEmpty())
        .andExpect(jsonPath("$.msg").value("默认基座镜像配置无效"));
  }
}
