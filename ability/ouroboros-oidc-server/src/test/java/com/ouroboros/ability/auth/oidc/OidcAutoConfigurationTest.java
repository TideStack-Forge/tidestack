package com.ouroboros.ability.auth.oidc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ouroboros.exception.ValidationException;

class OidcAutoConfigurationTest {
  @Test
  void validatesClientRegistrationsWhileTheProtocolIsDisabled() {
    new ApplicationContextRunner()
        .withBean(com.ouroboros.configuration.management.ConfigurationManagementService.class, () -> org.mockito.Mockito.mock(com.ouroboros.configuration.management.ConfigurationManagementService.class))
        .withConfiguration(AutoConfigurations.of(OidcAutoConfiguration.class))
        .run(context -> {
          assertThat(context).hasNotFailed().hasSingleBean(OidcClientConfigurationValidator.class)
              .doesNotHaveBean(SecurityFilterChain.class).doesNotHaveBean(OidcLoginController.class);
          assertThrows(ValidationException.class,
              () -> context.getBean(OidcClientConfigurationValidator.class).validate("broken-json"));
        });
  }
}
