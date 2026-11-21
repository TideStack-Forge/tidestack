package com.ouroboros.ai.starter.legacy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.ai.anthropic.AnthropicRuntimeAutoConfiguration;
import com.ouroboros.ai.openai.OpenAiRuntimeAutoConfiguration;
import com.ouroboros.ai.rag.AiRagRuntimeAutoConfiguration;
import com.ouroboros.ai.runtime.AiRuntimeAutoConfiguration;
import com.ouroboros.ai.runtime.service.AiInvocationService;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyAiStarterContextTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          AiRuntimeAutoConfiguration.class,
          OpenAiRuntimeAutoConfiguration.class,
          AnthropicRuntimeAutoConfiguration.class,
          AiRagRuntimeAutoConfiguration.class));

  @Test
  void legacyStarterExposesSingleLegacyRuntimeFamily() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(AiInvocationService.class);
      assertThat(context).hasBean("openAiChatAdapter");
      assertThat(context).hasBean("anthropicChatAdapter");
      assertThat(context).doesNotHaveBean("springAiChatModel");
    });
  }
}
