package com.ouroboros.ai.runtime.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.chat.ChatMessage;
import com.ouroboros.ai.chat.ChatMessageRole;
import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.chat.ChatResponse;
import com.ouroboros.ai.chat.TextContentPart;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.exception.AiPolicyViolationException;
import com.ouroboros.ai.options.AiOptions;
import com.ouroboros.ai.policy.AiEffectivePolicy;
import com.ouroboros.ai.runtime.pipeline.AiInvocationPipeline;
import com.ouroboros.ai.runtime.pipeline.AiInvocationStage;
import com.ouroboros.ai.runtime.resolution.AiEffectiveRuntimeSnapshot;
import com.ouroboros.ai.runtime.resolution.AiRuntimeSnapshotResolver;
import com.ouroboros.ai.runtime.spi.AiChatAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultAiInvocationServiceTest {

  @Test
  void invocationService_resolvesSnapshotBeforeDispatch() {
    var stageLog = new ArrayList<String>();
    var harness = TestAiInvocationServiceFactory.create(stageLog, snapshot(baseModel(), allowedPolicy("gpt-4o")));

    harness.service.chat(TestRequests.chat());

    assertEquals(Arrays.asList("resolve-snapshot", "prompt", "guardrail", "dispatch"), stageLog);
  }

  @Test
  void invocationService_rejectsPolicyDeniedModel() {
    var stageLog = new ArrayList<String>();
    var harness = TestAiInvocationServiceFactory.create(stageLog, snapshot(baseModel(), allowedPolicy("other-model")));

    assertThrows(AiPolicyViolationException.class, () -> harness.service.chat(TestRequests.chat()));
    assertEquals(Collections.singletonList("resolve-snapshot"), stageLog);
  }

  @Test
  void invocationService_rejectsPolicyDeniedTool() {
    var stageLog = new ArrayList<String>();
    var harness = TestAiInvocationServiceFactory.create(stageLog, snapshot(baseModel(), toolRestrictedPolicy("safe-tool")));

    assertThrows(AiPolicyViolationException.class, () -> harness.service.chat(TestRequests.chatWithTools("unsafe-tool")));
    assertEquals(Collections.singletonList("resolve-snapshot"), stageLog);
  }

  @Test
  void invocationService_preservesTypedMessagesWhenMergingDefaultOptions() {
    var stageLog = new ArrayList<String>();
    var harness = TestAiInvocationServiceFactory.create(stageLog, snapshot(modelWithDefaultOptions(), allowedPolicy("openai-chat")));

    harness.service.chat(TestRequests.typedChat());

    assertEquals(Arrays.asList("resolve-snapshot", "prompt", "guardrail", "dispatch"), stageLog);
    assertEquals(1, harness.chatAdapter.getLastRequest().getMessageList().size());
    assertEquals(ChatMessageRole.USER, harness.chatAdapter.getLastRequest().getMessageList().get(0).getRole());
    assertEquals("hello", ((TextContentPart) harness.chatAdapter.getLastRequest().getMessageList().get(0).getContentParts().get(0)).getText());
    assertEquals(Double.valueOf(0.7d), harness.chatAdapter.getLastRequest().getOptions().getTemperature());
  }

  private static AiEffectiveRuntimeSnapshot snapshot(AiModelDescriptor modelDescriptor, AiEffectivePolicy effectivePolicy) {
    return AiEffectiveRuntimeSnapshot.builder()
        .modelDescriptor(modelDescriptor)
        .effectivePolicy(effectivePolicy)
        .build();
  }

  private static AiModelDescriptor baseModel() {
    return AiModelDescriptor.builder()
        .modelId("gpt-4o")
        .providerFamily(AiProviderFamily.OPENAI)
        .build();
  }

  private static AiModelDescriptor modelWithDefaultOptions() {
    return AiModelDescriptor.builder()
        .modelId("openai-chat")
        .providerFamily(AiProviderFamily.OPENAI)
        .defaultOptions(AiOptions.builder()
            .temperature(0.7d)
            .build())
        .build();
  }

  private static AiEffectivePolicy allowedPolicy(String modelId) {
    return AiEffectivePolicy.builder()
        .policyId("allow-model")
        .modelAllowList(Collections.singletonList(modelId))
        .build();
  }

  private static AiEffectivePolicy toolRestrictedPolicy(String toolName) {
    return AiEffectivePolicy.builder()
        .policyId("allow-tool")
        .modelAllowList(Collections.singletonList("gpt-4o"))
        .toolAllowList(Collections.singletonList(toolName))
        .build();
  }

  private static final class TestAiInvocationServiceFactory {

    private static TestHarness create(List<String> stageLog, AiEffectiveRuntimeSnapshot snapshot) {
      var pipeline = new AiInvocationPipeline(Arrays.asList(
          stage("prompt", stageLog, 10),
          stage("guardrail", stageLog, 20)
      ));
      var chatAdapter = new TestChatAdapter(stageLog);
      var snapshotResolver = new TestSnapshotResolver(stageLog, snapshot);

      return new TestHarness(
          new DefaultAiInvocationService(snapshotResolver, pipeline,
              Collections.<AiChatAdapter>singletonList(chatAdapter), Collections.emptyList()),
          chatAdapter
      );
    }

    private static AiInvocationStage stage(String name, List<String> stageLog, int order) {
      return new AiInvocationStage() {
        @Override
        public int getOrder() {
          return order;
        }

        @Override
        public ChatRequest beforeChat(AiModelDescriptor modelDescriptor, ChatRequest request) {
          stageLog.add(name);
          return request;
        }
      };
    }
  }

  private static final class TestHarness {
    private final AiInvocationService service;
    private final TestChatAdapter chatAdapter;

    private TestHarness(AiInvocationService service, TestChatAdapter chatAdapter) {
      this.service = service;
      this.chatAdapter = chatAdapter;
    }
  }

  private static final class TestSnapshotResolver implements AiRuntimeSnapshotResolver {
    private final List<String> stageLog;
    private final AiEffectiveRuntimeSnapshot snapshot;

    private TestSnapshotResolver(List<String> stageLog, AiEffectiveRuntimeSnapshot snapshot) {
      this.stageLog = stageLog;
      this.snapshot = snapshot;
    }

    @Override
    public AiEffectiveRuntimeSnapshot resolve(String modelId, com.ouroboros.ai.context.AiInvocationContext invocationContext) {
      stageLog.add("resolve-snapshot");
      return snapshot;
    }
  }

  private static final class TestChatAdapter implements AiChatAdapter {
    private final List<String> stageLog;
    private ChatRequest lastRequest;

    private TestChatAdapter(List<String> stageLog) {
      this.stageLog = stageLog;
    }

    @Override
    public AiProviderFamily getProviderFamily() {
      return AiProviderFamily.OPENAI;
    }

    @Override
    public ChatResponse chat(AiModelDescriptor modelDescriptor, ChatRequest request) {
      this.lastRequest = request;
      stageLog.add("dispatch");
      return ChatResponse.builder()
          .modelId(modelDescriptor.getModelId())
          .text("ok")
          .build();
    }

    private ChatRequest getLastRequest() {
      return lastRequest;
    }
  }

  private static final class TestRequests {

    private static ChatRequest chat() {
      return ChatRequest.builder()
          .modelId("GPT-4O")
          .message(textMessage("hello"))
          .build();
    }

    private static ChatRequest chatWithTools(String toolName) {
      return ChatRequest.builder()
          .modelId("GPT-4O")
          .message(textMessage("hello"))
          .toolNames(Collections.singletonList(toolName))
          .build();
    }

    private static ChatRequest typedChat() {
      return ChatRequest.builder()
          .modelId("openai-chat")
          .message(ChatMessage.builder()
              .role(ChatMessageRole.USER)
              .addContentPart(TextContentPart.of("hello"))
              .build())
          .build();
    }

    private static ChatMessage textMessage(String content) {
      return ChatMessage.builder()
          .role(ChatMessageRole.USER)
          .addContentPart(TextContentPart.of(content))
          .build();
    }
  }
}
