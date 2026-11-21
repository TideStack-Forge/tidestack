package com.ouroboros.ai.mcp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapperSupplier;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import com.ouroboros.ai.descriptor.AiMcpEndpointDescriptor;
import com.ouroboros.ai.descriptor.AiToolDescriptor;
import com.ouroboros.ai.exception.AiProviderProtocolException;
import com.ouroboros.ai.runtime.provider.AiMcpEndpointDescriptorProvider;

public class AiMcpClientRegistry {
  private final List<AiMcpEndpointDescriptorProvider> endpointProviders;
  private final ToolDiscovery toolDiscovery;

  public AiMcpClientRegistry(List<AiMcpEndpointDescriptorProvider> endpointProviders) {
    this(endpointProviders, new SdkToolDiscovery());
  }

  public AiMcpClientRegistry(List<AiMcpEndpointDescriptorProvider> endpointProviders, ToolDiscovery toolDiscovery) {
    this.endpointProviders = orderedProviders(endpointProviders);
    this.toolDiscovery = toolDiscovery == null ? new SdkToolDiscovery() : toolDiscovery;
  }

  public List<AiMcpEndpointDescriptor> getEndpointDescriptors() {
    return endpointProviders.stream()
        .map(AiMcpEndpointDescriptorProvider::getMcpEndpointDescriptors)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .filter(Objects::nonNull)
        .filter(AiMcpEndpointDescriptor::isEnabled)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  public Map<String, AiToolDescriptor> discoverTools() {
    Map<String, AiToolDescriptor> discoveredTools = new LinkedHashMap<>();
    Set<String> normalizedNames = new LinkedHashSet<>();
    for (AiMcpEndpointDescriptor endpointDescriptor : getEndpointDescriptors()) {
      for (AiToolDescriptor toolDescriptor : discoverEndpointTools(endpointDescriptor)) {
        if (toolDescriptor == null || !hasText(toolDescriptor.getToolName())) {
          continue;
        }
        if (!isExposed(endpointDescriptor, toolDescriptor.getToolName())) {
          continue;
        }
        var normalizedToolName = normalize(toolDescriptor.getToolName());
        if (normalizedNames.add(normalizedToolName)) {
          discoveredTools.put(toolDescriptor.getToolName(), toolDescriptor);
        }
      }
    }
    return Collections.unmodifiableMap(discoveredTools);
  }

  private List<AiToolDescriptor> discoverEndpointTools(AiMcpEndpointDescriptor endpointDescriptor) {
    try {
      var descriptors = toolDiscovery.discover(endpointDescriptor);
      return descriptors == null ? Collections.emptyList() : descriptors;
    } catch (AiProviderProtocolException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new AiProviderProtocolException("Failed to discover MCP tools from endpoint: " + endpointDescriptor.getEndpointId(), exception);
    }
  }

  private static boolean isExposed(AiMcpEndpointDescriptor endpointDescriptor, String toolName) {
    var exposedTools = endpointDescriptor.getExposedTools();
    if (exposedTools == null || exposedTools.isEmpty()) {
      return true;
    }
    return exposedTools.stream().anyMatch(exposedTool -> normalize(exposedTool).equals(normalize(toolName)));
  }

  private static List<AiMcpEndpointDescriptorProvider> orderedProviders(List<AiMcpEndpointDescriptorProvider> providers) {
    return Optional.ofNullable(providers)
        .orElse(Collections.emptyList())
        .stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(AiMcpEndpointDescriptorProvider::getOrder))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  @FunctionalInterface
  public interface ToolDiscovery {

    List<AiToolDescriptor> discover(AiMcpEndpointDescriptor endpointDescriptor);
  }

  static final class SdkToolDiscovery implements ToolDiscovery {
    private static final String TRANSPORT_SSE = "sse";
    private static final String TRANSPORT_STREAMABLE_HTTP = "streamable-http";
    private static final String TRANSPORT_STREAMABLEHTTP = "streamablehttp";
    private static final String TRANSPORT_HTTP = "http";
    private static final String TRANSPORT_STDIO = "stdio";

    @Override
    public List<AiToolDescriptor> discover(AiMcpEndpointDescriptor endpointDescriptor) {
      try (var client = createClient(endpointDescriptor)) {
        client.initialize();
        return client.listTools()
            .tools()
            .stream()
            .map(tool -> toToolDescriptor(endpointDescriptor, tool))
            .collect(Collectors.toCollection(ArrayList::new));
      } catch (RuntimeException exception) {
        if (exception instanceof AiProviderProtocolException) {
          throw exception;
        }
        throw new AiProviderProtocolException("Failed to discover MCP tools from endpoint: " + endpointDescriptor.getEndpointId(), exception);
      }
    }

    private McpSyncClient createClient(AiMcpEndpointDescriptor endpointDescriptor) {
      return McpClient.sync(createTransport(endpointDescriptor)).build();
    }

    private McpClientTransport createTransport(AiMcpEndpointDescriptor endpointDescriptor) {
      String transport = normalize(endpointDescriptor.getTransport());
      if (TRANSPORT_SSE.equals(transport)) {
        return HttpClientSseClientTransport.builder(endpointDescriptor.getUrl()).build();
      }
      if (TRANSPORT_STREAMABLE_HTTP.equals(transport) || TRANSPORT_STREAMABLEHTTP.equals(transport) || TRANSPORT_HTTP.equals(transport)) {
        return HttpClientStreamableHttpTransport.builder(endpointDescriptor.getUrl()).build();
      }
      if (TRANSPORT_STDIO.equals(transport)) {
        return new StdioClientTransport(toServerParameters(endpointDescriptor), jsonMapper());
      }
      throw new AiProviderProtocolException("Unsupported MCP transport: " + endpointDescriptor.getTransport());
    }

    private ServerParameters toServerParameters(AiMcpEndpointDescriptor endpointDescriptor) {
      var metadata = endpointDescriptor.getMetadata();
      String command = metadata == null ? null : stringValue(metadata.get("command"));
      if (!hasText(command)) {
        throw new AiProviderProtocolException("Stdio MCP endpoint requires metadata.command: " + endpointDescriptor.getEndpointId());
      }

      var builder = ServerParameters.builder(command);
      Object args = metadata.get("args");
      if (args instanceof Iterable) {
        for (Object arg : (Iterable<?>) args) {
          if (arg != null) {
            builder.arg(String.valueOf(arg));
          }
        }
      }
      Object env = metadata.get("env");
      if (env instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> envMap = (Map<String, Object>) env;
        envMap.forEach((key, value) -> {
          if (key != null && value != null) {
            builder.addEnvVar(key, String.valueOf(value));
          }
        });
      }
      return builder.build();
    }

    private McpJsonMapper jsonMapper() {
      return ServiceLoader.load(McpJsonMapperSupplier.class)
          .findFirst()
          .map(McpJsonMapperSupplier::get)
          .orElseThrow(() -> new AiProviderProtocolException("No MCP JSON mapper implementation found on classpath"));
    }

    private AiToolDescriptor toToolDescriptor(AiMcpEndpointDescriptor endpointDescriptor, McpSchema.Tool tool) {
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("endpointId", endpointDescriptor.getEndpointId());
      metadata.put("transport", endpointDescriptor.getTransport());
      metadata.put("url", endpointDescriptor.getUrl());
      if (tool.meta() != null && !tool.meta().isEmpty()) {
        metadata.put("mcpMeta", new LinkedHashMap<>(tool.meta()));
      }
      if (tool.annotations() != null) {
        metadata.put("annotations", tool.annotations());
      }

      return AiToolDescriptor.builder()
          .toolName(tool.name())
          .description(hasText(tool.description()) ? tool.description() : tool.title())
          .inputSchema(copyMap(tool.inputSchema()))
          .outputSchema(copyMap(tool.outputSchema()))
          .allowModelInvocation(true)
          .metadata(metadata)
          .build();
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
      return source == null ? Collections.emptyMap() : new LinkedHashMap<>(source);
    }

    private String stringValue(Object value) {
      return value == null ? null : String.valueOf(value);
    }
  }
}
