package com.ouroboros.ai.openai;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.ai.context.AiInvocationContext;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.embedding.EmbeddingRequest;
import com.ouroboros.ai.exception.AiInvocationException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiEmbeddingAdapterTest {

  @Test
  void embeddingAdapter_propagatesAiInvocationExceptionFromFactory() {
    var expected = new AiInvocationException("Missing OpenAI provider connection for model: platform-openai-embedding");
    var adapter = new OpenAiEmbeddingAdapter(new ThrowingOpenAiAdapterFactory(expected));

    var thrown = assertThrows(AiInvocationException.class,
        () -> adapter.embed(OpenAiSpringAiTestSupport.embeddingDescriptor(), EmbeddingRequest.builder()
            .modelId("platform-openai-embedding")
            .inputs(java.util.List.of("docs"))
            .build()));

    assertSame(expected, thrown);
  }

  private static final class ThrowingOpenAiAdapterFactory extends OpenAiAdapterFactory {
    private final AiInvocationException exception;

    private ThrowingOpenAiAdapterFactory(AiInvocationException exception) {
      super(value -> value, (modelDescriptor, scope) -> Optional.empty());
      this.exception = exception;
    }

    @Override
    public OpenAiInvocation createInvocation(AiModelDescriptor modelDescriptor, AiInvocationContext invocationContext) {
      throw exception;
    }
  }
}
