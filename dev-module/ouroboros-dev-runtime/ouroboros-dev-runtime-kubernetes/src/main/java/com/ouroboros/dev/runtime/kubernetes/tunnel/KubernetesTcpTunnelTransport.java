package com.ouroboros.dev.runtime.kubernetes.tunnel;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.fabric8.kubernetes.client.LocalPortForward;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.dev.runtime.broker.BrokerPortAllocator;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;
import com.ouroboros.dev.runtime.model.TransportMode;
import com.ouroboros.dev.runtime.service.DebugConnector;
import com.ouroboros.dev.runtime.service.TunnelTransport;

/**
 * Kubernetes-specific TCP tunnel transport that uses port-forward to connect to target pods.
 * Each debug session gets a local port that forwards to the target pod via Kubernetes API.
 */
public class KubernetesTcpTunnelTransport implements TunnelTransport {
  private static final Logger logger = LoggerFactory.getLogger(KubernetesTcpTunnelTransport.class);
  private final BrokerPortAllocator portAllocator;
  private final String brokerHost;
  private final ExecutorService executorService;
  private final ConcurrentHashMap<String, TunnelBroker> activeBrokers = new ConcurrentHashMap<>();

  public KubernetesTcpTunnelTransport(
      BrokerPortAllocator portAllocator,
      String brokerHost
  ) {
    this.portAllocator = portAllocator;
    this.brokerHost = brokerHost;
    this.executorService = Executors.newCachedThreadPool(r -> {
      Thread thread = new Thread(r);
      thread.setDaemon(true);
      thread.setName("k8s-tcp-tunnel-" + thread.getId());
      return thread;
    });
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

    return portAllocator.allocate()
        .flatMap(brokerPort -> startBroker(session, connector, brokerPort)
            .map(broker -> {
              activeBrokers.put(session.getSessionId(), broker);
              return null;
            }));
  }

  @Override
  public Try<Void> close(String sessionId) {
    TunnelBroker broker = activeBrokers.remove(sessionId);
    if (broker == null) {
      return Try.success(null);
    }
    portAllocator.release(broker.brokerPort);
    return stopBroker(broker);
  }

  @Override
  public Try<DebugSessionBinding> getAccessInfo(String sessionId) {
    TunnelBroker broker = activeBrokers.get(sessionId);
    if (broker == null) {
      return Try.failure(new IllegalStateException("TCP tunnel broker not found for session"));
    }
    DebugSessionBinding binding = DebugSessionBinding.builder()
        .bindingScope(DebugSessionBindingScope.LOOPBACK_ONLY)
        .host(brokerHost)
        .port(broker.brokerPort)
        .transportType(TransportMode.TCP)
        .clientAccessHint("Connect via: " + brokerHost + ":" + broker.brokerPort)
        .metadata(bindingMetadata(broker))
        .build();
    return Try.success(binding);
  }

  private Try<TunnelBroker> startBroker(DebugSession session, DebugConnector connector, int brokerPort) {
    return Try.of(() -> {
      ServerSocket serverSocket = new ServerSocket(brokerPort);
      TunnelBroker broker = new TunnelBroker(session.getSessionId(), brokerPort, serverSocket);
      
      executorService.submit(() -> acceptConnections(broker, session, connector));
      
      return broker;
    });
  }

  private void acceptConnections(TunnelBroker broker, DebugSession session, DebugConnector connector) {
    while (!broker.serverSocket.isClosed()) {
      try {
        Socket clientSocket = broker.serverSocket.accept();
        executorService.submit(() -> handleConnection(clientSocket, session, connector));
      } catch (IOException error) {
        if (!broker.serverSocket.isClosed()) {
          error.printStackTrace();
        }
        break;
      }
    }
  }

  private void handleConnection(Socket clientSocket, DebugSession session, DebugConnector connector) {
    try {
      Socket targetSocket = connector.connect(session).get();
      
      executorService.submit(() -> copyStream(clientSocket, targetSocket, "client->target"));
      executorService.submit(() -> copyStream(targetSocket, clientSocket, "target->client"));
      
    } catch (Exception error) {
      closeQuietly(clientSocket);
      error.printStackTrace();
    }
  }

  private void copyStream(Socket source, Socket target, String direction) {
    try {
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = source.getInputStream().read(buffer)) != -1) {
        target.getOutputStream().write(buffer, 0, bytesRead);
        target.getOutputStream().flush();
      }
    } catch (IOException error) {
      logger.warn("Stream copy interrupted for direction {}: {}", direction, error.getMessage());
    } finally {
      closeQuietly(source);
      closeQuietly(target);
    }
  }

  private Try<Void> stopBroker(TunnelBroker broker) {
    return Try.run(() -> {
      if (broker.serverSocket != null && !broker.serverSocket.isClosed()) {
        broker.serverSocket.close();
      }
    });
  }

  private void closeQuietly(Socket socket) {
    if (socket == null) {
      return;
    }
    try {
      socket.close();
    } catch (IOException error) {
      logger.warn("Failed to close socket: {}", error.getMessage());
    }
  }

  private Map<String, String> bindingMetadata(TunnelBroker broker) {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("brokerPort", String.valueOf(broker.brokerPort));
    metadata.put("brokerHost", brokerHost);
    metadata.put("transportType", TransportMode.TCP.name());
    metadata.put("mechanism", "kubernetes-port-forward");
    return metadata;
  }

  private static class TunnelBroker {
    private final String sessionId;
    private final int brokerPort;
    private final ServerSocket serverSocket;

    TunnelBroker(String sessionId, int brokerPort, ServerSocket serverSocket) {
      this.sessionId = sessionId;
      this.brokerPort = brokerPort;
      this.serverSocket = serverSocket;
    }
  }
}
