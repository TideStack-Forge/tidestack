package com.ouroboros.security.externalauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.security.externalauth.secret.SecretResolver;

class ExternalAuthSecretAssemblyTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(ExternalAuthAutoConfiguration.class).withPropertyValues("secret.test=value");

  @Test
  void moduleContributionsJoinTheOfficialResolverAndKeepEnvironmentConfigurationSemantics() {
    runner.withBean(SecretResolver.class, () -> resolver("managed", "{\"appId\":\"application\"}"))
        .run(context -> {
          assertThat(context).hasSingleBean(CompositeSecretResolver.class);
          var composite = context.getBean(CompositeSecretResolver.class);
          assertThat(composite.resolve("managed:connection#/appId")).contains("application");
          assertThat(composite.resolve("config:secret.test")).contains("value");
          assertThat(composite.resolve("uninstalled:connection")).isEmpty();
        });
  }

  @Test
  void contributionsCannotSilentlyReplaceBuiltInSchemes() {
    runner.withBean(SecretResolver.class, () -> resolver("config", "replacement"))
        .run(context -> assertThatThrownBy(() -> context.getBean(CompositeSecretResolver.class).resolve("config:secret.test"))
            .hasMessageContaining("多个解析器"));
  }

  private SecretResolver resolver(String scheme, String value) {
    return new SecretResolver() {
      @Override
      public boolean supports(String requested) { return scheme.equals(requested); }
      @Override
      public Optional<String> resolve(String reference) { return Optional.of(value); }
    };
  }
}
