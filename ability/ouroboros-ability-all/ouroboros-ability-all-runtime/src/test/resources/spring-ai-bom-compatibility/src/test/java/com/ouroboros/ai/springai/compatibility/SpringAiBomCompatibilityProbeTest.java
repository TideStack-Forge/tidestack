package com.ouroboros.ai.springai.compatibility;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiBomCompatibilityProbeTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(TestApplication.class)
      .withPropertyValues(
          "spring.ai.openai.api-key=test-key",
          "spring.ai.openai.chat.options.model=gpt-4o-mini",
          "spring.ai.openai.embedding.options.model=text-embedding-3-small"
      );

  @Test
  void springAiOpenAiAutoconfigurationStartsOnBoot35() {
    contextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context).hasSingleBean(OpenAiChatModel.class);
      assertThat(context).hasSingleBean(OpenAiEmbeddingModel.class);
    });
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {
  }
}
