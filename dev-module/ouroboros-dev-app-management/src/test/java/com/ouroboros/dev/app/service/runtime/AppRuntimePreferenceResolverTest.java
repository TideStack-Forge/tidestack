package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.model.BackendType;

@ExtendWith(MockitoExtension.class)
class AppRuntimePreferenceResolverTest {

  @Mock
  private ConfigurationService configurationService;

  @Test
  void shouldResolveConfiguredDefaultsAndAvailableOptions() {
    DevRuntimeProperties properties = new DevRuntimeProperties();
    properties.setDefaultBackend(BackendType.KUBERNETES);
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND))
        .thenReturn(Optional.of("DOCKER"));
    when(configurationService.get(String.class, ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY))
        .thenReturn(Optional.of("STABLE_DOMAIN"));

    AppRuntimePreferenceResolver resolver = new AppRuntimePreferenceResolver(configurationService, properties);

    assertEquals(BackendType.DOCKER, resolver.resolveBackendType(null));
    assertEquals("STABLE_DOMAIN", resolver.resolveTestAppExposureStrategy(null).name());
    assertEquals(Arrays.asList("DOCKER", "KUBERNETES"), resolver.availableBackendTypeNames());
    assertEquals(Arrays.asList("TEMP_PORT", "STABLE_DOMAIN"), resolver.availableTestAppExposureStrategyNames());
  }
}
