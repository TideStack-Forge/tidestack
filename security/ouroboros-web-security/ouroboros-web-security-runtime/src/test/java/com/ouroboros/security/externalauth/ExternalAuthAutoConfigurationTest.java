package com.ouroboros.security.externalauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;

class ExternalAuthAutoConfigurationTest {

  @Test
  void assemblesCoreResolverWithRuntimeSecretSources(@TempDir Path directory) throws Exception {
    Path secret = directory.resolve("credentials.json");
    Files.writeString(secret, "{\"clientId\":\"file-client\",\"clientSecret\":\"file-secret\"}");
    String fileReference = "file:" + secret;

    new ApplicationContextRunner()
        .withUserConfiguration(ExternalAuthAutoConfiguration.class)
        .withPropertyValues("EXTERNAL_AUTH_TEST_SECRET=env-secret", "external.auth.test.secret=config-secret")
        .run(context -> {
          assertThat(context).hasSingleBean(CompositeSecretResolver.class);
          CompositeSecretResolver resolver = context.getBean(CompositeSecretResolver.class);

          assertThat(resolver.resolve("env:EXTERNAL_AUTH_TEST_SECRET")).contains("env-secret");
          assertThat(resolver.resolve("config:external.auth.test.secret")).contains("config-secret");
          assertThat(resolver.resolveProjections(Map.of(
              "clientId", fileReference + "#/clientId",
              "clientSecret", fileReference + "#/clientSecret")))
              .contains(Map.of("clientId", "file-client", "clientSecret", "file-secret"));
          assertThat(resolver.resolveWithFallback("config:external.auth.test.missing", "inline-secret")).isEmpty();
        });
  }
}
