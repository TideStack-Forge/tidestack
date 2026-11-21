package com.ouroboros.dev.app.service.impl;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Either;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.model.BaseImagePullRequest;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.app.service.support.BaseImageQuerySupport;
import com.ouroboros.dev.app.service.support.BaseImageReferenceResolver;
import com.ouroboros.dev.app.service.runtime.AppRuntimeViewAssembler;
import com.ouroboros.dev.app.service.runtime.DebugSessionViewAssembler;
import com.ouroboros.dev.app.service.runtime.RuntimeImageViewService;
import com.ouroboros.dev.app.service.runtime.TestAppRuntimeSpecFactory;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.dev.runtime.service.DebugSessionStore;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppContainerServiceImplBaseImageTest {

  @Mock
  private AppManagementService appManagementService;

  @Mock
  private ConfigurationService configurationService;

  @Mock
  private DevelopWorkspaceContext developWorkspaceContext;

  @Mock
  private ContainerService containerService;

  @Mock
  private DevContainerConfig devContainerConfig;

  @Mock
  private AppDataSourceService appDataSourceService;

  @Mock
  private PlatformDataSourceService platformDataSourceService;

  @Mock
  private DevRuntimeService devRuntimeService;

  @Mock
  private RuntimeStateStore runtimeStateStore;

  @Mock
  private DebugSessionStore debugSessionStore;

  @Mock
  private TestAppRuntimeSpecFactory runtimeSpecFactory;

  private AppContainerServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new AppContainerServiceImpl(
        appManagementService,
        configurationService,
        developWorkspaceContext,
        containerService,
        devContainerConfig,
        appDataSourceService,
        platformDataSourceService,
        devRuntimeService,
        runtimeStateStore,
        debugSessionStore,
        runtimeSpecFactory,
        new AppRuntimeViewAssembler(new DebugSessionViewAssembler(), new RuntimeImageViewService()),
        new RuntimeImageViewService(),
        new BaseImageQuerySupport(new BaseImageReferenceResolver())
    );
  }

  @Test
  void getBaseImages_deduplicatesAndSortsTaggedImages() {
    mockConfiguredBaseImage("registry.example.com/team/ouroboros-mothership:1.0.0-beta.2");
    when(containerService.getAllTaggedImageNames()).thenReturn(Either.right(Arrays.asList(
        "registry.example.com/team/ouroboros-mothership:latest",
        "registry.example.com/team/ouroboros-mothership:1.0.0-beta.3",
        "registry.example.com/team/ouroboros-mothership:latest",
        "registry.example.com/team/ouroboros-mothership",
        "registry.example.com/team/ouroboros-mothership@sha256:abcdef",
        "registry.example.com/team/other-image:1.0.0",
        "<none>:<none>"
    )));

    var result = service.getBaseImages();

    assertTrue(result.isSuccess());
    assertEquals(Arrays.asList(
            "registry.example.com/team/ouroboros-mothership:1.0.0-beta.3",
            "registry.example.com/team/ouroboros-mothership:latest"
        ),
        result.get().stream().map(baseImage -> baseImage.getImageName()).collect(Collectors.toList()));
    assertEquals(Arrays.asList("1.0.0-beta.3", "latest"),
        result.get().stream().map(baseImage -> baseImage.getTag()).collect(Collectors.toList()));
  }

  @Test
  void getDefaultImage_prefersConfiguredImageWhenPresentLocally() {
    mockConfiguredBaseImage("registry.example.com/team/ouroboros-mothership:1.0.0-beta.2");
    when(containerService.getAllTaggedImageNames()).thenReturn(Either.right(Arrays.asList(
        "registry.example.com/team/ouroboros-mothership:latest",
        "registry.example.com/team/ouroboros-mothership:1.0.0-beta.2"
    )));

    var result = service.getDefaultImage();

    assertTrue(result.isSuccess());
    assertEquals("registry.example.com/team/ouroboros-mothership:1.0.0-beta.2", result.get());
  }

  @Test
  void getDefaultImage_fallsBackToFirstSortedImageWhenConfiguredImageMissingLocally() {
    mockConfiguredBaseImage("registry.example.com/team/ouroboros-mothership:1.0.0-beta.2");
    when(containerService.getAllTaggedImageNames()).thenReturn(Either.right(Arrays.asList(
        "registry.example.com/team/ouroboros-mothership:latest",
        "registry.example.com/team/ouroboros-mothership:1.0.0-beta.3"
    )));

    var result = service.getDefaultImage();

    assertTrue(result.isSuccess());
    assertEquals("registry.example.com/team/ouroboros-mothership:1.0.0-beta.3", result.get());
  }

  @Test
  void getDefaultImage_returnsEmptyWhenNoBaseImagesExistLocally() {
    mockConfiguredBaseImage("registry.example.com/team/ouroboros-mothership:1.0.0-beta.2");
    when(containerService.getAllTaggedImageNames()).thenReturn(Either.right(Collections.emptyList()));

    var result = service.getDefaultImage();

    assertTrue(result.isSuccess());
    assertEquals("", result.get());
  }

  @Test
  void pullBaseImage_rejectsWhenImageNameAndTagAreBothMissing() {
    var result = service.pullBaseImage(new BaseImagePullRequest());

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("二选一"));
  }

  @Test
  void pullBaseImage_rejectsWhenImageNameAndTagAreBothProvided() {
    var request = new BaseImagePullRequest()
        .setImageName("registry.example.com/team/ouroboros-mothership:latest")
        .setTag("latest");

    var result = service.pullBaseImage(request);

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("二选一"));
  }

  @Test
  void pullBaseImage_rejectsBlankImageNameForRepullMode() {
    var result = service.pullBaseImage(new BaseImagePullRequest().setImageName(" "));

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("镜像名"));
  }

  @Test
  void pullBaseImage_rejectsBlankTagForTagMode() {
    var result = service.pullBaseImage(new BaseImagePullRequest().setTag(" "));

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("tag"));
  }

  @Test
  void pullBaseImage_rejectsInvalidTagSyntax() {
    mockConfiguredBaseImage("registry.example.com/team/ouroboros-mothership:1.0.0-beta.2");

    var result = service.pullBaseImage(new BaseImagePullRequest().setTag("bad/tag"));

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("tag"));
  }

  @Test
  void pullBaseImage_rejectsNonLocalImageNameForRepullMode() {
    mockConfiguredBaseImage("registry.example.com/team/ouroboros-mothership:1.0.0-beta.2");
    when(containerService.getAllTaggedImageNames()).thenReturn(Either.right(Collections.singletonList(
        "registry.example.com/team/ouroboros-mothership:latest"
    )));

    var result = service.pullBaseImage(new BaseImagePullRequest()
        .setImageName("registry.example.com/team/ouroboros-mothership:1.0.0-beta.3"));

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("本地"));
    verify(containerService, never()).pullImageAsync(anyString());
  }

  @Test
  void pullBaseImage_trimsTagAndStartsAsyncPull() {
    mockConfiguredBaseImage("registry.example.com/team/ouroboros-mothership:1.0.0-beta.2");
    when(containerService.pullImageAsync(anyString())).thenReturn(new CompletableFuture<>());

    var result = service.pullBaseImage(new BaseImagePullRequest().setTag(" latest "));

    assertTrue(result.isSuccess());
    verify(containerService).pullImageAsync("registry.example.com/team/ouroboros-mothership:latest");
  }

  @Test
  void pullBaseImage_returnsSynchronousErrorWhenPullStartupFails() {
    mockConfiguredBaseImage("registry.example.com/team/ouroboros-mothership:1.0.0-beta.2");
    when(containerService.pullImageAsync(anyString())).thenThrow(new IllegalStateException("boom"));

    var result = service.pullBaseImage(new BaseImagePullRequest().setTag("latest"));

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("boom"));
  }

  private void mockConfiguredBaseImage(String configuredImageName) {
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_CONTAINER_IMAGE))
        .thenReturn(Optional.ofNullable(configuredImageName));
  }
}
