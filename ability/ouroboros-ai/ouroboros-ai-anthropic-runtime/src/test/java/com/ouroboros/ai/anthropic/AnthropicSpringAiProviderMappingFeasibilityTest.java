package com.ouroboros.ai.anthropic;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Model;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicServiceTier;
import org.springframework.ai.anthropic.AnthropicSkill;
import org.springframework.ai.anthropic.AnthropicSkillContainer;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import com.ouroboros.ai.descriptor.AiToolDescriptor;
import com.ouroboros.ai.exception.AiPolicyViolationException;
import com.ouroboros.ai.exception.AiProviderProtocolException;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicSpringAiProviderMappingFeasibilityTest {

  @Test
  void springAiApiAndModelBuildersSupportDynamicBaseUrlAndDefaultOptions() {
    var options = selectedOptions("http://127.0.0.1:9/v1", "test-key", "claude-3-haiku-20240307");
    var model = AnthropicChatModel.builder()
        .options(options)
        .build();

    assertNotNull(model);
    assertEquals("http://127.0.0.1:9/v1", model.getOptions().getBaseUrl());
    assertEquals("claude-3-haiku-20240307", model.getOptions().getModel());
    assertEquals(AnthropicServiceTier.STANDARD_ONLY, model.getOptions().getServiceTier());
    assertEquals("eu", model.getOptions().getInferenceGeo());
    assertFalse(model.getOptions().getSkillContainer().getSkills().isEmpty());
    assertEquals("beta-features", model.getOptions().getHttpHeaders().get("anthropic-beta"));
  }

  @Test
  void mapsPlatformChatOptionsToolsAndSpringAiResponseThroughCanonicalConnection() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(jsonResponse(anthropicResponse("ok")));
      var model = AnthropicChatModel.builder()
          .options(selectedOptions(server.url("/").toString(), "resolved-anthropic-secret", "claude-3-haiku-20240307"))
          .build();

      var response = model.call(new Prompt(List.of(new UserMessage("hello")), model.getOptions()));

      assertEquals("ok", response.getResult().getOutput().getText());
      var recorded = server.takeRequest();
      assertEquals("/v1/messages", recorded.getPath());
      assertEquals("resolved-anthropic-secret", recorded.getHeader("x-api-key"));
      assertTrue(recorded.getHeaders().values("anthropic-beta").stream().anyMatch(value -> value.contains("skills-")));
      var body = Json.toMap(recorded.getBody().readUtf8());
      assertEquals("claude-3-haiku-20240307", body.get("model"));
      assertEquals(64, body.get("max_tokens"));
      assertEquals("standard_only", body.get("service_tier"));
      assertEquals("eu", body.get("inference_geo"));
      assertTrue(map(body.get("container")).containsKey("skills"));
    }
  }

  @Test
  void usesRequestModelIdFallbackAndDoesNotCacheCredentialRotation() throws Exception {
    try (MockWebServer firstServer = new MockWebServer();
         MockWebServer secondServer = new MockWebServer()) {
      firstServer.enqueue(jsonResponse(anthropicResponse("first")));
      secondServer.enqueue(jsonResponse(anthropicResponse("second")));

      var first = call(firstServer.url("/").toString(), "secret-a", "request-model-a", "one");
      var second = call(secondServer.url("/").toString(), "secret-b", "request-model-b", "two");

      assertEquals("first", first);
      assertEquals("second", second);
      var firstRequest = firstServer.takeRequest();
      var secondRequest = secondServer.takeRequest();
      assertEquals("secret-a", firstRequest.getHeader("x-api-key"));
      assertEquals("secret-b", secondRequest.getHeader("x-api-key"));
      assertEquals("request-model-a", Json.toMap(firstRequest.getBody().readUtf8()).get("model"));
      assertEquals("request-model-b", Json.toMap(secondRequest.getBody().readUtf8()).get("model"));
    }
  }

  @Test
  void inheritsCanonicalConnectionAndCredentialFailures() {
    var missingCredential = assertThrows(AiProviderProtocolException.class,
        () -> requireCredential(null, "anthropic-prod"));
    assertEquals("Missing Anthropic credentials for provider connection: anthropic-prod", missingCredential.getMessage());

    var blankCredential = assertThrows(AiProviderProtocolException.class,
        () -> requireCredential(" ", "anthropic-prod"));
    assertEquals("Missing Anthropic credentials for provider connection: anthropic-prod", blankCredential.getMessage());
  }

  @Test
  void rejectsUnsupportedSelectedOptionsAndDeniedToolBeforeNetwork() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      var unknown = assertThrows(AiProviderProtocolException.class,
          () -> validateProviderOptions(Map.of("unknownAnthropicOption", "value")));
      assertEquals("Anthropic provider option is not supported by Spring AI 2.0 feasibility spike: unknownAnthropicOption",
          unknown.getMessage());

      var deniedTool = AiToolDescriptor.builder()
          .toolName("search")
          .description("Search")
          .inputSchema(Collections.emptyMap())
          .allowModelInvocation(false)
          .build();
      var denied = assertThrows(AiPolicyViolationException.class, () -> validateTool(deniedTool));
      assertEquals("AI model invocation is not allowed for tool: search", denied.getMessage());
      assertEquals(0, server.getRequestCount());
    }
  }

  @Test
  void mapsProviderHttpTransportAndResponseMappingFailuresWithoutLeakingCredentials() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
          .setBody("{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad secret-anthropic-key\"}}"));
      var model = AnthropicChatModel.builder()
          .options(selectedOptions(server.url("/").toString(), "secret-anthropic-key", "claude-3-haiku-20240307"))
          .build();

      var providerError = assertThrows(AiProviderProtocolException.class,
          () -> callWithPlatformBoundary(model, "platform-anthropic-chat", "hello", "secret-anthropic-key"));

      assertTrue(providerError.getMessage().contains("Anthropic chat provider error for model: platform-anthropic-chat"));
      assertTrue(providerError.getMessage().contains("HTTP status 400"));
      assertFalse(providerError.getMessage().contains("secret-anthropic-key"));
    }

    var transportModel = AnthropicChatModel.builder()
        .options(selectedOptions("http://127.0.0.1:9/", "connection-key", "claude-3-haiku-20240307"))
        .build();
    var transport = assertThrows(AiProviderProtocolException.class,
        () -> callWithPlatformBoundary(transportModel, "platform-anthropic-chat", "hello", "connection-key"));
    assertEquals("Anthropic chat invocation failed for model: platform-anthropic-chat", transport.getMessage());
    assertTrue(transport.getCause().getClass().getName().startsWith("com.anthropic.errors."));
  }

  private static AnthropicChatOptions selectedOptions(String baseUrl, String apiKey, String model) {
    return AnthropicChatOptions.builder()
        .baseUrl(baseUrl)
        .apiKey(apiKey)
        .model(Model.of(model))
        .maxTokens(64)
        .temperature(0.2)
        .topP(0.9)
        .topK(12)
        .stopSequences(List.of("stop-a", "stop-b"))
        .serviceTier(AnthropicServiceTier.STANDARD_ONLY)
        .inferenceGeo("eu")
        .httpHeaders(Map.of("anthropic-beta", "beta-features"))
        .skillContainer(AnthropicSkillContainer.builder().skill(AnthropicSkill.PDF).build())
        .build();
  }

  private static String call(String baseUrl, String apiKey, String modelId, String text) {
    var model = AnthropicChatModel.builder()
        .options(selectedOptions(baseUrl, apiKey, modelId))
        .build();
    return model.call(new Prompt(List.of(new UserMessage(text)), model.getOptions())).getResult().getOutput().getText();
  }

  private static void callWithPlatformBoundary(AnthropicChatModel model,
                                               String platformModelId,
                                               String text,
                                               String apiKey) {
    try {
      model.call(new Prompt(List.of(new UserMessage(text)), model.getOptions()));
    } catch (AnthropicServiceException exception) {
      throw new AiProviderProtocolException("Anthropic chat provider error for model: " + platformModelId
          + " HTTP status " + exception.statusCode() + ": "
          + sanitize(exception.getMessage(), apiKey), exception);
    } catch (AnthropicException exception) {
      throw new AiProviderProtocolException("Anthropic chat invocation failed for model: " + platformModelId, exception);
    }
  }

  private static String requireCredential(String apiKey, String providerInstanceId) {
    if (apiKey == null || apiKey.trim().isEmpty()) {
      throw new AiProviderProtocolException("Missing Anthropic credentials for provider connection: " + providerInstanceId);
    }
    return apiKey;
  }

  private static void validateProviderOptions(Map<String, Object> providerOptions) {
    for (String key : providerOptions.keySet()) {
      if (!List.of("apiKey", "anthropicVersion", "anthropicBeta", "serviceTier", "topK",
          "system", "inferenceGeo", "container", "stopSequences").contains(key)) {
        throw new AiProviderProtocolException(
            "Anthropic provider option is not supported by Spring AI 2.0 feasibility spike: " + key);
      }
    }
  }

  private static void validateTool(AiToolDescriptor toolDescriptor) {
    if (!toolDescriptor.isAllowModelInvocation()) {
      throw new AiPolicyViolationException("AI model invocation is not allowed for tool: " + toolDescriptor.getToolName());
    }
  }

  private static MockResponse jsonResponse(String body) {
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
  }

  private static String anthropicResponse(String text) {
    return "{\"id\":\"msg_123\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-3-haiku-20240307\","
        + "\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}],"
        + "\"stop_reason\":\"end_turn\",\"stop_sequence\":null,"
        + "\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
  }

  private static String sanitize(String message, String apiKey) {
    if (message == null || apiKey == null) {
      return message;
    }
    return message.replace(apiKey, "[redacted]");
  }

  private static Map<?, ?> map(Object value) {
    return (Map<?, ?>) value;
  }
}
