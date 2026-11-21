package com.ouroboros.ai.openai;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import com.ouroboros.ai.exception.AiInvocationException;
import com.ouroboros.ai.exception.AiProviderProtocolException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiSpringAiExceptionMappingTest {

  @Test
  void inheritsCanonicalConnectionAndCredentialFailures() {
    var missing = new OpenAiChatAdapter(new OpenAiAdapterFactory(value -> value,
        new OpenAiSpringAiTestSupport.RecordingConnectionResolver()));
    var missingConnection = assertThrows(AiInvocationException.class,
        () -> missing.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest()));
    assertEquals("Missing OpenAI provider connection for model: platform-openai-chat", missingConnection.getMessage());

    var cause = new IllegalStateException("secret backend unavailable");
    var failingCredential = new OpenAiChatAdapter(new OpenAiAdapterFactory(ref -> {
      throw cause;
    }, new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
        OpenAiSpringAiTestSupport.connection("http://127.0.0.1:9/", "openai-secret"))));
    var credentialFailure = assertThrows(AiInvocationException.class,
        () -> failingCredential.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest()));
    assertEquals("Failed to resolve OpenAI credentials for provider connection: openai-prod", credentialFailure.getMessage());
    assertSame(cause, credentialFailure.getCause());

    var missingCredential = new OpenAiChatAdapter(new OpenAiAdapterFactory(value -> value,
        new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
            OpenAiSpringAiTestSupport.connectionWithOptions("http://127.0.0.1:9/", null, null))));
    var exception = assertThrows(AiInvocationException.class,
        () -> missingCredential.chat(OpenAiSpringAiTestSupport.chatDescriptorWithProviderApiKey(),
            OpenAiSpringAiTestSupport.chatRequest()));
    assertEquals("Missing OpenAI credentials for provider connection: openai-prod", exception.getMessage());
  }

  @Test
  void mapsProviderHttpTransportAndResponseMappingFailuresWithoutLeakingCredentials() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
          .setBody("{\"error\":{\"code\":\"bad_request\",\"message\":\"bad input secret-api-key\"}}"));
      var adapter = new OpenAiChatAdapter(new OpenAiAdapterFactory(value -> value,
          new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
              OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  OpenAiSpringAiTestSupport.mapOf("apiKey", "secret-api-key")))));

      var providerError = assertThrows(AiProviderProtocolException.class,
          () -> adapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest()));

      assertTrue(providerError.getMessage().contains("OpenAI chat provider error for model: platform-openai-chat"));
      assertTrue(providerError.getMessage().contains("HTTP status 400"));
      assertTrue(providerError.getMessage().contains("bad_request"));
      assertFalse(providerError.getMessage().contains("secret-api-key"));
    }

    var transportAdapter = new OpenAiChatAdapter(new OpenAiAdapterFactory(value -> value,
        new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
            OpenAiSpringAiTestSupport.connectionWithOptions("http://127.0.0.1:9/", null,
                OpenAiSpringAiTestSupport.mapOf("apiKey", "connection-key")))));
    var transport = assertThrows(AiProviderProtocolException.class,
        () -> transportAdapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest()));
    assertEquals("OpenAI chat invocation failed for model: platform-openai-chat", transport.getMessage());
    assertTrue(transport.getCause().getClass().getName().startsWith("com.openai.errors."));

    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(OpenAiSpringAiTestSupport.jsonResponse("{\"id\":\"chat-1\",\"choices\":[{\"message\":null}]}"));
      var mappingAdapter = new OpenAiChatAdapter(new OpenAiAdapterFactory(value -> value,
          new OpenAiSpringAiTestSupport.RecordingConnectionResolver(
              OpenAiSpringAiTestSupport.connectionWithOptions(server.url("/").toString(), null,
                  OpenAiSpringAiTestSupport.mapOf("apiKey", "connection-key")))));
      var mapping = assertThrows(AiProviderProtocolException.class,
          () -> mappingAdapter.chat(OpenAiSpringAiTestSupport.chatDescriptor(), OpenAiSpringAiTestSupport.chatRequest()));
      assertEquals("Failed to map OpenAI chat response for model: platform-openai-chat", mapping.getMessage());
    }
  }
}
