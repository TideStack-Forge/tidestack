package com.ouroboros.dev.runtime.docker.tunnel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.vavr.control.Try;

import com.ouroboros.container.model.Container;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.runtime.broker.BrokerPortAllocator;
import com.ouroboros.dev.runtime.docker.debug.DockerDebugTargetResolver;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;
import com.ouroboros.dev.runtime.model.TransportMode;
import com.ouroboros.dev.runtime.service.DebugConnector;
import com.ouroboros.dev.runtime.service.TunnelTransport;

/**
 * Docker-specific TCP tunnel transport that uses temporary socat broker containers.
 * Each debug session gets its own ephemeral broker container with a dynamically allocated port.
 */
public class DockerTcpTunnelTransport implements TunnelTransport {
  private final ContainerService containerService;
  private final DockerDebugTargetResolver targetResolver;
  private final BrokerPortAllocator portAllocator;
  private final String brokerHost;
  private final ConcurrentHashMap<String, TunnelBrokerContainer> activeBrokers = new ConcurrentHashMap<>();

  public DockerTcpTunnelTransport(
      ContainerService containerService,
      DockerDebugTargetResolver targetResolver,
      BrokerPortAllocator portAllocator,
      String brokerHost
  ) {
    this.containerService = containerService;
    this.targetResolver = targetResolver;
    this.portAllocator = portAllocator;
    this.brokerHost = brokerHost;
  }

  @Override
  public TransportMode getTransportMode() {
    return TransportMode.TCP;
  }

  @Override
  public Try<Void> open(DebugSession session, DebugConnector connector) {
    if (session == null || session.getSessionId() == null) {
      return Try.failure(new IllegalArgumentException("session and sessionId are required"));
    }
    if (activeBrokers.containsKey(session.getSessionId())) {
      return Try.failure(new IllegalStateException("TCP tunnel broker already exists for session"));
    }

    return targetResolver.resolve(session)
        .flatMap(target -> portAllocator.allocate()
            .flatMap(brokerPort -> startBrokerContainer(session, target.getHost(), target.getPort(), brokerPort)
                .map(containerName -> {
                  activeBrokers.put(session.getSessionId(), new TunnelBrokerContainer(containerName, brokerPort));
                  return null;
                })));
  }

  @Override
  public Try<Void> close(String sessionId) {
    TunnelBrokerContainer broker = activeBrokers.remove(sessionId);
    if (broker == null) {
      return Try.success(null);
    }
    portAllocator.release(broker.brokerPort);
    return stopBrokerContainer(broker.containerName);
  }

  @Override
  public Try<DebugSessionBinding> getAccessInfo(String sessionId) {
    TunnelBrokerContainer broker = activeBrokers.get(sessionId);
    if (broker == null) {
      return Try.failure(new IllegalStateException("TCP tunnel broker not found for session"));
    }
    DebugSessionBinding binding = DebugSessionBinding.builder()
        .bindingScope(DebugSessionBindingScope.INTERNAL_NETWORK)
        .host(brokerHost)
        .port(broker.brokerPort)
        .transportType(TransportMode.TCP)
        .clientAccessHint("Connect via: " + brokerHost + ":" + broker.brokerPort)
        .metadata(bindingMetadata(broker))
        .build();
    return Try.success(binding);
  }

  private Try<String> startBrokerContainer(DebugSession session, String targetHost, int targetPort, int brokerPort) {
    String containerName = "ouroboros-tcp-broker-" + session.getSessionId();
    
    return getTargetContainerNetwork(session)
        .flatMap(network -> {
          Container brokerContainer = buildBrokerContainer(containerName, targetHost, targetPort, brokerPort, network, session);
          return containerService.create(brokerContainer)
              .fold(
                  error -> Try.failure(new RuntimeException("Failed to create broker container: " + error.getMessage(), error)),
                  containerId -> containerService.start(containerId)
                      .fold(
                          error -> {
                            containerService.delete(containerId);
                            return Try.failure(new RuntimeException("Failed to start broker container: " + error.getMessage(), error));
                          },
                          v -> Try.success(containerName)
                      )
              );
        });
  }

  private Try<Void> stopBrokerContainer(String containerName) {
    return containerService.stop(containerName)
        .flatMap(v -> containerService.delete(containerName))
        .fold(
            error -> Try.success(null),
            v -> Try.success(null)
        );
  }

  private Try<String> getTargetContainerNetwork(DebugSession session) {
    String targetContainerName = resolveTargetContainerName(session);
    return containerService.getContainerInfoByName(targetContainerName)
        .fold(
            error -> Try.failure(new RuntimeException("Failed to get target container info: " + error.getMessage(), error)),
            container -> {
              String network = container.getNetwork();
              if (network == null || network.trim().isEmpty()) {
                return Try.success("bridge");
              }
              return Try.success(network);
            }
        );
  }

  private String resolveTargetContainerName(DebugSession session) {
    if (session.getRuntimeKey().getRuntimeKind() == com.ouroboros.dev.runtime.model.RuntimeKind.DEV_HELPER) {
      return "ouroboros-dev-helper-" + session.getRuntimeKey().getAppName();
    }
    return "ouroboros-test-app-" + session.getRuntimeKey().getAppName() + "-" + session.getRuntimeKey().getProfile();
  }

  private Container buildBrokerContainer(String containerName, String targetHost, int targetPort, int brokerPort, String network, DebugSession session) {
    Container container = new Container();
    container.setName(containerName);
    container.setImage("alpine/socat:latest");
    container.setNetwork(network);
    
    Map<String, Map<Integer, Integer>> portBindings = new LinkedHashMap<>();
    Map<Integer, Integer> tcpPorts = new LinkedHashMap<>();
    tcpPorts.put(Integer.valueOf(targetPort), Integer.valueOf(brokerPort));
    portBindings.put("tcp", tcpPorts);
    container.setPortBindings(portBindings);
    
    Map<String, String> labels = new LinkedHashMap<>();
    labels.put("session-id", session.getSessionId());
    labels.put("managed-by", "ouroboros-tcp-tunnel");
    labels.put("runtime-key", session.getRuntimeKey().toString());
    labels.put("target-type", session.getTargetType().name());
    container.setLabels(labels);
    
    Map<String, String> env = new LinkedHashMap<>();
    env.put("SOCAT_COMMAND", "TCP-LISTEN:" + targetPort + ",fork,reuseaddr TCP:" + targetHost + ":" + targetPort);
    container.setEnvironmentalVariables(env);
    
    return container;
  }

  private Map<String, String> bindingMetadata(TunnelBrokerContainer broker) {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("brokerContainerName", broker.containerName);
    metadata.put("brokerPort", String.valueOf(broker.brokerPort));
    metadata.put("brokerHost", brokerHost);
    metadata.put("transportType", TransportMode.TCP.name());
    return metadata;
  }

  private static class TunnelBrokerContainer {
    private final String containerName;
    private final int brokerPort;

    TunnelBrokerContainer(String containerName, int brokerPort) {
      this.containerName = containerName;
      this.brokerPort = brokerPort;
    }
  }
}
