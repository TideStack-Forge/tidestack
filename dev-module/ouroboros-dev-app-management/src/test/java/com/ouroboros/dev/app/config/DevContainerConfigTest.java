package com.ouroboros.dev.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.constant.ConfigurationConstant;

@ExtendWith(MockitoExtension.class)
class DevContainerConfigTest {

  @Mock
  private ConfigurationService configurationService;

  @Mock
  private ContainerService containerService;

  @Mock
  private DevelopWorkspaceContext developWorkspaceContext;

  @Mock
  private Environment environment;

  @Test
  void shouldReadHelperBaseImageFromDedicatedHelperConfiguration() {
    when(configurationService.getRequired(ConfigurationConstant.DEV_HELPER_CONTAINER_IMAGE))
        .thenReturn("registry.example.com/team/ouroboros-mothership:1.0.0");

    DevContainerConfig config = new DevContainerConfig(
        configurationService,
        containerService,
        developWorkspaceContext,
        environment
    );

    assertEquals("registry.example.com/team/ouroboros-mothership:1.0.0", config.getMothershipImageName());
  }
}
