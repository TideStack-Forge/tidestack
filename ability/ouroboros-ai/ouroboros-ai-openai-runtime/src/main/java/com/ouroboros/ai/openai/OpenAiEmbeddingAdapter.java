package com.ouroboros.ai.openai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.descriptor.AiModelDescriptor;
import com.ouroboros.ai.embedding.EmbeddingRequest;
import com.ouroboros.ai.embedding.EmbeddingResponse;
import com.ouroboros.ai.exception.AiInvocationException;
import com.ouroboros.ai.exception.AiProviderProtocolException;
import com.ouroboros.ai.options.AiOptions;
import com.ouroboros.ai.runtime.spi.AiEmbeddingAdapter;

public class OpenAiEmbeddingAdapter implements AiEmbeddingAdapter {
  private static final String PROVIDER_OPTION_API_KEY = "apiKey";
  private static final String PROVIDER_OPTION_DIMENSIONS = "dimensions";

  private final OpenAiAdapterFactory adapterFactory;

  public OpenAiEmbeddingAdapter(OpenAiAdapterFactory adapterFactory) {
    this.adapterFactory = Objects.requireNonNull(adapterFactory, "OpenAI adapter factory must not be null");
  }

  @Override
  public AiProviderFamily getProviderFamily() {
    return AiProviderFamily.OPENAI;
  }

  @Override
  public EmbeddingResponse embed(AiModelDescriptor modelDescriptor, EmbeddingRequest request) {
    Objects.requireNonNull(modelDescriptor, "AI model descriptor must not be null");
    Objects.requireNonNull(request, "Embedding request must not be null");
    OpenAiAdapterFactory.OpenAiInvocation invocation = null;
    try {
      invocation = adapterFactory.createInvocation(modelDescriptor, request.getInvocationContext());
      var options = toOptions(modelDescriptor, request, invocation);
      var response = adapterFactory.createEmbeddingModel(options)
          .call(new org.springframework.ai.embedding.EmbeddingRequest(request.getInputs(), options));
      return toEmbeddingResponse(modelDescriptor, response);
    } catch (AiProviderProtocolException exception) {
      throw exception;
    } catch (AiInvocationException exception) {
      throw exception;
    } catch (OpenAIServiceException exception) {
      throw new AiProviderProtocolException("OpenAI embedding provider error for model: " + modelDescriptor.getModelId()
          + " HTTP status " + exception.statusCode() + ": "
          + sanitize(openAiBody(exception), apiKey(invocation)), exception);
    } catch (OpenAIIoException exception) {
      throw new AiProviderProtocolException("OpenAI embedding invocation failed for model: " + modelDescriptor.getModelId(),
          exception);
    } catch (OpenAIInvalidDataException exception) {
      throw new AiProviderProtocolException("Failed to map OpenAI embedding response for model: " + modelDescriptor.getModelId(),
          exception);
    } catch (OpenAIException exception) {
      throw new AiProviderProtocolException("OpenAI embedding invocation failed for model: " + modelDescriptor.getModelId(),
          exception);
    } catch (RuntimeException exception) {
      throw new AiProviderProtocolException("Failed to map OpenAI embedding response for model: " + modelDescriptor.getModelId(),
          exception);
    }
  }

  private OpenAiEmbeddingOptions toOptions(AiModelDescriptor modelDescriptor,
                                           EmbeddingRequest request,
                                           OpenAiAdapterFactory.OpenAiInvocation invocation) {
    var options = mergeOptions(modelDescriptor, request.getOptions());
    var providerOptions = mergeProviderOptions(modelDescriptor, options);
    rejectUnknownProviderOptions(providerOptions);
    return OpenAiEmbeddingOptions.builder()
        .baseUrl(invocation.getBaseUrl())
        .apiKey(invocation.getApiKey())
        .model(providerModelName(modelDescriptor, request.getModelId()))
        .dimensions(dimensions(providerOptions))
        .build();
  }

  private static EmbeddingResponse toEmbeddingResponse(AiModelDescriptor modelDescriptor,
                                                       org.springframework.ai.embedding.EmbeddingResponse response) {
    if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
      throw new IllegalStateException("OpenAI embedding response is empty");
    }
    return EmbeddingResponse.builder()
        .modelId(modelDescriptor.getModelId())
        .vectors(mapVectors(response.getResults()))
        .usage(mapUsage(response.getMetadata() == null ? null : response.getMetadata().getUsage()))
        .build();
  }

  private static List<List<Double>> mapVectors(List<org.springframework.ai.embedding.Embedding> embeddings) {
    List<List<Double>> vectors = new ArrayList<>();
    for (org.springframework.ai.embedding.Embedding embedding : embeddings) {
      List<Double> vector = new ArrayList<>();
      for (float value : embedding.getOutput()) {
        vector.add((double) value);
      }
      vectors.add(vector);
    }
    return vectors;
  }

  private static Map<String, Object> mapUsage(org.springframework.ai.chat.metadata.Usage usage) {
    if (usage == null) {
      return Collections.emptyMap();
    }
    return mapOf("promptTokens", usage.getPromptTokens(), "totalTokens", usage.getTotalTokens());
  }

  private static AiOptions mergeOptions(AiModelDescriptor modelDescriptor, AiOptions requestOptions) {
    return modelDescriptor.getDefaultOptions() == null
        ? requestOptions
        : requestOptions == null ? modelDescriptor.getDefaultOptions() : modelDescriptor.getDefaultOptions().merge(requestOptions);
  }

  private static Map<String, Object> mergeProviderOptions(AiModelDescriptor modelDescriptor, AiOptions options) {
    Map<String, Object> providerOptions = new LinkedHashMap<>();
    providerOptions.putAll(modelDescriptor.getProviderOptions());
    if (options != null) {
      providerOptions.putAll(options.getProviderOptions());
    }
    return providerOptions;
  }

  private static void rejectUnknownProviderOptions(Map<String, Object> providerOptions) {
    for (Map.Entry<String, Object> entry : providerOptions.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      if (!PROVIDER_OPTION_API_KEY.equals(entry.getKey()) && !PROVIDER_OPTION_DIMENSIONS.equals(entry.getKey())) {
        throw new AiProviderProtocolException("OpenAI embedding provider option is not supported: " + entry.getKey());
      }
    }
  }

  private static Integer dimensions(Map<String, Object> providerOptions) {
    var value = providerOptions.get(PROVIDER_OPTION_DIMENSIONS);
    return value == null ? null : Integer.parseInt(String.valueOf(value));
  }

  private static String providerModelName(AiModelDescriptor modelDescriptor, String fallbackModelId) {
    return hasText(modelDescriptor.getModelName()) ? modelDescriptor.getModelName() : fallbackModelId;
  }

  private static String apiKey(OpenAiAdapterFactory.OpenAiInvocation invocation) {
    return invocation == null ? null : invocation.getApiKey();
  }

  private static String openAiBody(OpenAIServiceException exception) {
    return exception.body() == null ? exception.getMessage() : exception.body().toString();
  }

  private static String sanitize(String message, String apiKey) {
    if (message == null || apiKey == null) {
      return message;
    }
    return message.replace(apiKey, "[redacted]");
  }

  private static Map<String, Object> mapOf(Object... values) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i += 2) {
      if (values[i + 1] != null) {
        map.put(String.valueOf(values[i]), values[i + 1]);
      }
    }
    return map;
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
