package com.ouroboros.ability.all.runtime;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.ClassUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.app.error.ErrorResponseResolver;
import com.ouroboros.app.error.RequestLocaleResolver;
import com.ouroboros.errorcode.registry.ErrorCodeRegistry;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.i18n.runtime.extractor.ErrorCodeTextExtractor;
import com.ouroboros.resource.manage.controller.RuntimeResourceController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicRuntimeConfigurationTest {
  @TempDir
  Path directory;

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
          .getCandidates().stream()
          .filter(Set.of(
              "com.ouroboros.errorcode.runtime.OuroborosErrorCodeRuntimeAutoConfiguration",
              "com.ouroboros.resource.runtime.config.ResourceAutoConfiguration",
              "com.ouroboros.resource.manage.ResourceRuntimeManageAutoConfiguration")::contains)
          .map(name -> ClassUtils.resolveClassName(name, getClass().getClassLoader()))
          .toArray(Class<?>[]::new)));

  @Test
  void ordinaryRuntimeUsesBundledErrorDefinitionInResponsesAndTranslations() {
    runner.withUserConfiguration(ErrorResponseResolver.class)
        .withBean(RequestLocaleResolver.class, RequestLocaleResolver::new)
        .run(context -> {
          assertThat(context).hasSingleBean(ErrorCodeRegistry.class);
          var response = context.getBean(ErrorResponseResolver.class)
              .resolve(new BusinessException(50000, "fallback"), null);
          assertThat(response._2).isEqualTo("服务暂时不可用，请稍后重试");
          assertThat(context.getBean(ErrorResponseResolver.class)
              .resolve(new BusinessException(49999, "unknown-error-fallback"), null)._2)
              .isEqualTo("unknown-error-fallback");
          var translations = new ErrorCodeTextExtractor(context.getBean(ErrorCodeRegistry.class)).extract().get();
          assertThat(translations).anySatisfy(entry -> {
            assertThat(entry.getKey()).isEqualTo("50000");
            assertThat(entry.getSourceText()).isEqualTo("服务暂时不可用，请稍后重试");
          });
        });
  }

  @Test
  void ordinaryRuntimeServesResourceManagementUsingItsConfiguredDirectories() {
    runner.withPropertyValues(
        "ouroboros.resource.app-default-base-directory=" + directory.resolve("defaults"),
        "ouroboros.resource.runtime-override-base-directory=" + directory.resolve("overrides"))
        .run(context -> {
          assertThat(context).hasSingleBean(RuntimeResourceController.class);
          var mvc = MockMvcBuilders.standaloneSetup(context.getBean(RuntimeResourceController.class)).build();
          mvc.perform(get("/api/sys/resource/runtime").param("scope", "frontend"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.code").value(0))
              .andExpect(jsonPath("$.data.items").isEmpty());
        });
  }
}
