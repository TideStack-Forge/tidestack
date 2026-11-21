package com.ouroboros.dev.runtime.broker;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;
import com.ouroboros.dev.runtime.model.TransportMode;
import com.ouroboros.dev.runtime.service.DebugConnector;
import com.ouroboros.dev.runtime.service.TunnelTransport;

public class TcpTunnelTransport implements TunnelTransport {
  private static final Logger logger = LoggerFactory.getLogger(TcpTunnelTransport.class);
  private final BrokerPortAllocator portAllocator;
  private final String brokerHost;
  private final DebugSessionBindingScope bindScope;
  private final ExecutorService executorService;
  private final ConcurrentHashMap<String, TunnelSession> tunnelSessions = new ConcurrentHashMap<String, TunnelSession>();

  public TcpTunnelTransport(BrokerPortAllocator portAllocator, String brokerHost, DebugSessionBindingScope bindScope) {
    this.portAllocator = portAllocator;
    this.brokerHost = brokerHost;
    this.bindScope = bindScope;
    this.executorService = Executors.newCachedThreadPool(new BrokerThreadFactory());
  }

  @Override
  public TransportMode getTransportMode() {
    return TransportMode.TCP;
  }

  @Override
  public Try<Void> open(DebugSession session, DebugConnector connector) {
    if (session == null || session.getSessionId() == null || session.getSessionId().trim().isEmpty()) {
      return Try.failure(new IllegalArgumentException("sessionId is required"));
    }
    if (connector == null) {
      return Try.failure(new IllegalArgumentException("debug connector is required"));
    }
    if (tunnelSessions.containsKey(session.getSessionId())) {
      return Try.failure(new IllegalStateException("debug session already exists"));
    }
    return portAllocator.allocate()
        .flatMap(port -> openTunnel(session, connector, port.intValue())
            .onFailure(error -> portAllocator.release(port.intValue())))
        .map(binding -> null);
  }

  @Override
  public Try<Void> close(String sessionId) {
    TunnelSession tunnelSession = tunnelSessions.remove(sessionId);
    if (tunnelSession == null) {
      return Try.success(null);
    }
    tunnelSession.closed = true;
    closeQuietly(tunnelSession.listener);
    for (Socket socket : tunnelSession.sockets) {
      closeQuietly(socket);
    }
    portAllocator.release(tunnelSession.port);
    return Try.success(null);
  }

  @Override
  public Try<DebugSessionBinding> getAccessInfo(String sessionId) {
    TunnelSession tunnelSession = tunnelSessions.get(sessionId);
    if (tunnelSession == null) {
      return Try.failure(new IllegalStateException("tunnel session not found"));
    }
    return Try.success(tunnelSession.binding);
  }

  private Try<DebugSessionBinding> openTunnel(DebugSession session, DebugConnector connector, int port) {
    return Try.of(() -> {
      ServerSocket listener = new ServerSocket();
      listener.setReuseAddress(true);
      listener.bind(new InetSocketAddress(resolveBindAddress(bindScope), port));
      DebugSessionBinding binding = DebugSessionBinding.builder()
          .host(brokerHost)
          .port(Integer.valueOf(port))
          .bindingScope(bindScope)
          .targetType(session.getTargetType())
          .transportType(getTransportMode())
          .clientAccessHint(buildClientAccessHint(session, port))
          .metadata(bindingMetadata(session, port))
          .build();
      TunnelSession tunnelSession = new TunnelSession(session, connector, listener, port, binding);
      TunnelSession existing = tunnelSessions.putIfAbsent(session.getSessionId(), tunnelSession);
      if (existing != null) {
        closeQuietly(listener);
        throw new IllegalStateException("debug session already exists");
      }
      startAcceptLoop(tunnelSession);
      return binding;
    });
  }

  private Map<String, String> bindingMetadata(DebugSession session, int port) {
    Map<String, String> metadata = new LinkedHashMap<String, String>();
    metadata.put("brokerHost", brokerHost);
    metadata.put("brokerPort", String.valueOf(port));
    metadata.put("bindingScope", bindScope == null ? null : bindScope.name());
    metadata.put("targetType", session.getTargetType().name());
    metadata.put("targetPort", String.valueOf(session.getTargetType().getTargetPort()));
    metadata.put("transportType", getTransportMode().name());
    return metadata;
  }

  private void startAcceptLoop(TunnelSession tunnelSession) {
    executorService.submit(() -> {
      while (!tunnelSession.closed) {
        try {
          Socket clientSocket = tunnelSession.listener.accept();
          Socket upstreamSocket = tunnelSession.connector.connect(tunnelSession.session).get();
          trackSocket(tunnelSession, clientSocket);
          trackSocket(tunnelSession, upstreamSocket);
          startCopyLoop(tunnelSession, clientSocket, upstreamSocket);
          startCopyLoop(tunnelSession, upstreamSocket, clientSocket);
        } catch (SocketException error) {
          if (!tunnelSession.closed) {
            close(tunnelSession.session.getSessionId());
          }
          return;
        } catch (Exception error) {
          if (!tunnelSession.closed) {
            close(tunnelSession.session.getSessionId());
          }
          return;
        }
      }
    });
  }

  private void startCopyLoop(TunnelSession tunnelSession, Socket sourceSocket, Socket targetSocket) {
    executorService.submit(() -> {
      byte[] buffer = new byte[8192];
      try {
        int read;
        while ((read = sourceSocket.getInputStream().read(buffer)) >= 0) {
          targetSocket.getOutputStream().write(buffer, 0, read);
          targetSocket.getOutputStream().flush();
        }
      } catch (IOException error) {
        logger.warn("Stream copy interrupted for tunnel session {}: {}", tunnelSession.session.getSessionId(), error.getMessage());
      } finally {
        closeSocketPair(tunnelSession, sourceSocket, targetSocket);
      }
    });
  }

  private void trackSocket(TunnelSession tunnelSession, Socket socket) {
    tunnelSession.sockets.add(socket);
  }

  private void closeSocketPair(TunnelSession tunnelSession, Socket sourceSocket, Socket targetSocket) {
    tunnelSession.sockets.remove(sourceSocket);
    tunnelSession.sockets.remove(targetSocket);
    closeQuietly(sourceSocket);
    closeQuietly(targetSocket);
  }

  private String buildClientAccessHint(DebugSession session, int port) {
    if (bindScope != DebugSessionBindingScope.LOOPBACK_ONLY || brokerHost == null || brokerHost.trim().isEmpty()) {
      return null;
    }
    return "ssh -L " + session.getTargetType().getTargetPort() + ":127.0.0.1:" + port + " " + brokerHost;
  }

  private static InetAddress resolveBindAddress(DebugSessionBindingScope bindScope) throws IOException {
    if (bindScope == DebugSessionBindingScope.INTERNAL_NETWORK) {
      return InetAddress.getByName("0.0.0.0");
    }
    return InetAddress.getByName("127.0.0.1");
  }

  private static void closeQuietly(ServerSocket listener) {
    if (listener == null) {
      return;
    }
    try {
      listener.close();
    } catch (IOException error) {
      logger.warn("Failed to close server socket: {}", error.getMessage());
    }
  }

  private static void closeQuietly(Socket socket) {
    if (socket == null) {
      return;
    }
    try {
      socket.close();
    } catch (IOException error) {
      logger.warn("Failed to close socket: {}", error.getMessage());
    }
  }

  private static class TunnelSession {
    private final DebugSession session;
    private final DebugConnector connector;
    private final ServerSocket listener;
    private final int port;
    private final DebugSessionBinding binding;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    private TunnelSession(
        DebugSession session,
        DebugConnector connector,
        ServerSocket listener,
        int port,
        DebugSessionBinding binding
    ) {
      this.session = session;
      this.connector = connector;
      this.listener = listener;
      this.port = port;
      this.binding = binding;
    }
  }

  private static class BrokerThreadFactory implements ThreadFactory {
    private final AtomicInteger sequence = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable runnable) {
      Thread thread = new Thread(runnable, "dev-runtime-broker-" + sequence.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    }
  }
}
