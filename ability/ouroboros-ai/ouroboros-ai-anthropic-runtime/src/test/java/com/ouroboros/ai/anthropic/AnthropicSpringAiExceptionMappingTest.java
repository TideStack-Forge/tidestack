package com.ouroboros.ai.anthropic;

import com.anthropic.errors.AnthropicInvalidDataException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;

import com.ouroboros.ai.exception.AiInvocationException;
import com.ouroboros.ai.exception.AiProviderProtocolException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicSpringAiExceptionMappingTest {

  @Test
  void inheritsCanonicalConnectionAndCredentialFailures() {
    var missing = new AnthropicChatAdapter(new AnthropicAdapterFactory(value -> value,
        new AnthropicSpringAiTestSupport.RecordingConnectionResolver()));
    var missingConnection = assertThrows(AiInvocationException.class,
        () -> missing.chat(AnthropicSpringAiTestSupport.chatDescriptor(), AnthropicSpringAiTestSupport.chatRequest()));
    assertEquals("Missing Anthropic provider connection for model: platform-anthropic-chat", missingConnection.getMessage());

    var cause = new IllegalStateException("secret backend unavailable");
    var failingCredential = new AnthropicChatAdapter(new AnthropicAdapterFactory(ref -> {
      throw cause;
    }, new AnthropicSpringAiTestSupport.RecordingConnectionResolver(
        AnthropicSpringAiTestSupport.connection("http://127.0.0.1:9/", "anthropic-secret"))));
    var credentialFailure = assertThrows(AiInvocationException.class,
        () -> failingCredential.chat(AnthropicSpringAiTestSupport.chatDescriptor(), AnthropicSpringAiTestSupport.chatRequest()));
    assertEquals("Failed to resolve Anthropic credentials for provider connection: anthropic-test", credentialFailure.getMessage());
    assertSame(cause, credentialFailure.getCause());

    var missingCredential = new AnthropicChatAdapter(new AnthropicAdapterFactory(value -> value,
        new AnthropicSpringAiTestSupport.RecordingConnectionResolver(
            AnthropicSpringAiTestSupport.connectionWithOptions("http://127.0.0.1:9/", null, null))));
    var exception = assertThrows(AiInvocationException.class,
        () -> missingCredential.chat(AnthropicSpringAiTestSupport.chatDescriptorWithProviderApiKey(),
            AnthropicSpringAiTestSupport.chatRequest()));
    assertEquals("Missing Anthropic credentials for provider connection: anthropic-test", exception.getMessage());
  }

  @Test
  void mapsProviderHttpTransportAndResponseMappingFailuresWithoutLeakingCredentials() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
          .setBody("{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad secret-anthropic-key\"}}"));
      var adapter = new AnthropicChatAdapter(new AnthropicAdapterFactory(value -> value,
          new AnthropicSpringAiTestSupport.RecordingConnectionResolver(
              AnthropicSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  AnthropicSpringAiTestSupport.mapOf("apiKey", "secret-anthropic-key")))));

      var providerError = assertThrows(AiProviderProtocolException.class,
          () -> adapter.chat(AnthropicSpringAiTestSupport.chatDescriptor(), AnthropicSpringAiTestSupport.chatRequest()));

      assertTrue(providerError.getMessage().contains("Anthropic chat provider error for model: platform-anthropic-chat"));
      assertTrue(providerError.getMessage().contains("HTTP status 400"));
      assertTrue(providerError.getMessage().contains("invalid_request_error"));
      assertFalse(providerError.getMessage().contains("secret-anthropic-key"));
    }

    var transportAdapter = new AnthropicChatAdapter(new AnthropicAdapterFactory(value -> value,
        new AnthropicSpringAiTestSupport.RecordingConnectionResolver(
            AnthropicSpringAiTestSupport.connectionWithOptions("http://127.0.0.1:9/", null,
                AnthropicSpringAiTestSupport.mapOf("apiKey", "connection-key")))));
    var transport = assertThrows(AiProviderProtocolException.class,
        () -> transportAdapter.chat(AnthropicSpringAiTestSupport.chatDescriptor(), AnthropicSpringAiTestSupport.chatRequest()));
    assertEquals("Anthropic chat invocation failed for model: platform-anthropic-chat", transport.getMessage());
    assertTrue(transport.getCause().getClass().getName().startsWith("com.anthropic.errors."));

    var mappingAdapter = new AnthropicChatAdapter(new MappingFailureAnthropicAdapterFactory(value -> value,
        new AnthropicSpringAiTestSupport.RecordingConnectionResolver(
            AnthropicSpringAiTestSupport.connectionWithOptions("http://127.0.0.1:9/", null,
                AnthropicSpringAiTestSupport.mapOf("apiKey", "connection-key")))));
    var mapping = assertThrows(AiProviderProtocolException.class,
        () -> mappingAdapter.chat(AnthropicSpringAiTestSupport.chatDescriptor(), AnthropicSpringAiTestSupport.chatRequest()));
    assertEquals("Failed to map Anthropic chat response for model: platform-anthropic-chat", mapping.getMessage());
  }

  private static final class MappingFailureAnthropicAdapterFactory extends AnthropicAdapterFactory {
    private MappingFailureAnthropicAdapterFactory(java.util.function.Function<String, String> credentialResolver,
                                                 com.ouroboros.ai.runtime.resolution.AiProviderConnectionResolver providerConnectionResolver) {
      super(credentialResolver, providerConnectionResolver);
    }

    @Override
    protected AnthropicChatModel createChatModel(AnthropicChatOptions options) {
      throw new AnthropicInvalidDataException("invalid Anthropic payload");
    }
  }
}
