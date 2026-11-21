package com.ouroboros.ai.starter.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.ClassUtils;

import com.ouroboros.ai.runtime.service.AiInvocationService;
import com.ouroboros.ai.runtime.spi.AiChatAdapter;
import com.ouroboros.ai.runtime.spi.AiEmbeddingAdapter;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeAiStarterContextTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(TestApplication.class);

  @Test
  void runtimeStarterExposesOnlyProviderNeutralRuntime() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(AiInvocationService.class);
      assertThat(context.getBeansOfType(AiChatAdapter.class)).isEmpty();
      assertThat(context.getBeansOfType(AiEmbeddingAdapter.class)).isEmpty();
      assertThat(context).doesNotHaveBean("aiRetriever");
      assertThat(context).doesNotHaveBean("aiMcpBridgeService");
      assertThat(isPresent("com.ouroboros.ai.rag.AiRagRuntimeAutoConfiguration", context.getClassLoader())).isFalse();
      assertThat(isPresent("com.ouroboros.ai.mcp.AiMcpRuntimeAutoConfiguration", context.getClassLoader())).isFalse();
      assertThat(isPresent("dev.langchain4j.model.chat.ChatLanguageModel", context.getClassLoader())).isFalse();
    });
  }

  private static boolean isPresent(String className, ClassLoader classLoader) {
    return ClassUtils.isPresent(className, classLoader);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {
  }
}
