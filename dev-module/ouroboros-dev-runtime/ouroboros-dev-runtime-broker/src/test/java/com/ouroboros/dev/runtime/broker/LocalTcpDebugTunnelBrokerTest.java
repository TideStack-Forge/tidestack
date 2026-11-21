package com.ouroboros.dev.runtime.broker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.EOFException;
import java.io.InputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.SpringFactoriesLoader;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.DevRuntimeCoreAutoConfiguration;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.TransportMode;
import com.ouroboros.dev.runtime.service.DebugConnector;
import com.ouroboros.dev.runtime.service.TunnelBroker;

class LocalTcpDebugTunnelBrokerTest {
  private static final int TEST_PORT_RANGE_START = 43125;
  private static final int TEST_PORT_RANGE_END = 43135;


  @Test
  void shouldDiscoverAutoConfigurationThroughSpringFactories() {
    List<String> factoryNames = SpringFactoriesLoader.loadFactoryNames(
        EnableAutoConfiguration.class,
        Thread.currentThread().getContextClassLoader()
    );

    assertTrue(factoryNames.contains(DebugRuntimeBrokerAutoConfiguration.class.getName()));
  }

  @Test
  void shouldProvideTunnelBrokerBean() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class, DebugRuntimeBrokerAutoConfiguration.class))
        .run(context -> {
          assertEquals(1, context.getBeansOfType(TunnelBroker.class).size());
          assertEquals(1, context.getBeansOfType(TcpTunnelTransport.class).size());
        });
  }

  @Test
  void shouldForwardTcpTrafficAndReleaseListenerPortOnClose() throws Exception {
    try (ServerSocket upstreamServer = new ServerSocket(0)) {
      CompletableFuture<String> upstreamPayload = CompletableFuture.supplyAsync(() -> acceptAndReply(upstreamServer));
      TcpTunnelTransport transport = new TcpTunnelTransport(
          new BrokerPortAllocator(TEST_PORT_RANGE_START, TEST_PORT_RANGE_END),
          "127.0.0.1",
          DebugSessionBindingScope.LOOPBACK_ONLY
      );
      DebugSession session = DebugSession.builder()
          .sessionId("dbg-001")
          .runtimeKey(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER))
          .targetType(DebugTunnelTargetType.DEBUG)
          .transportMode(TransportMode.TCP)
          .status(DebugSessionStatus.ACTIVE)
          .build();

      transport.open(session, new SocketDebugConnector(upstreamServer.getLocalPort())).get();
      DebugSessionBinding binding = transport.getAccessInfo(session.getSessionId()).get();
      assertEquals("127.0.0.1", binding.getHost());
      assertTrue(binding.getPort().intValue() >= TEST_PORT_RANGE_START && binding.getPort().intValue() <= TEST_PORT_RANGE_END);
      assertEquals(DebugSessionBindingScope.LOOPBACK_ONLY, binding.getBindingScope());

      try (Socket client = new Socket(binding.getHost(), binding.getPort())) {
        client.getOutputStream().write("ping".getBytes(StandardCharsets.UTF_8));
        client.getOutputStream().flush();

        byte[] response = new byte[4];
        readFully(client.getInputStream(), response);
        assertEquals("pong", new String(response, StandardCharsets.UTF_8));
      }

      assertEquals("ping", upstreamPayload.get(5, TimeUnit.SECONDS));
      transport.close(session.getSessionId()).get();

      assertPortEventuallyRebindable(binding.getPort().intValue());
    }
  }

  @Test
  void shouldUseBackendApiClientHintForBackendApiTargetSessions() throws Exception {
    try (ServerSocket upstreamServer = new ServerSocket(0)) {
      TcpTunnelTransport transport = new TcpTunnelTransport(
          new BrokerPortAllocator(TEST_PORT_RANGE_START, TEST_PORT_RANGE_END),
          "127.0.0.1",
          DebugSessionBindingScope.LOOPBACK_ONLY
      );
      DebugSession session = DebugSession.builder()
          .sessionId("dbg-api-001")
          .runtimeKey(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER))
          .targetType(DebugTunnelTargetType.BACKEND_API)
          .transportMode(TransportMode.HTTP)
          .status(DebugSessionStatus.ACTIVE)
          .metadata(Collections.singletonMap("targetType", "BACKEND_API"))
          .build();

      transport.open(session, new SocketDebugConnector(upstreamServer.getLocalPort())).get();
      DebugSessionBinding binding = transport.getAccessInfo(session.getSessionId()).get();
      try {
        assertEquals("ssh -L 8080:127.0.0.1:" + binding.getPort() + " 127.0.0.1", binding.getClientAccessHint());
        assertEquals("BACKEND_API", binding.getMetadata().get("targetType"));
      } finally {
        transport.close(session.getSessionId()).get();
      }
    }
  }

  private static void assertPortEventuallyRebindable(int port) throws Exception {
    BindException lastError = null;
    for (int attempt = 0; attempt < 20; attempt++) {
      try (ServerSocket rebound = new ServerSocket()) {
        rebound.setReuseAddress(true);
        rebound.bind(new java.net.InetSocketAddress("127.0.0.1", port));
        assertEquals(port, rebound.getLocalPort());
        return;
      } catch (BindException error) {
        lastError = error;
        TimeUnit.MILLISECONDS.sleep(100);
      }
    }
    throw lastError == null ? new IllegalStateException("Port " + port + " was not rebound") : lastError;
  }

  private static String acceptAndReply(ServerSocket upstreamServer) {
    return Try.of(() -> {
      try (Socket upstream = upstreamServer.accept()) {
        byte[] payload = new byte[4];
        readFully(upstream.getInputStream(), payload);
        upstream.getOutputStream().write("pong".getBytes(StandardCharsets.UTF_8));
        upstream.getOutputStream().flush();
        return new String(payload, StandardCharsets.UTF_8);
      }
    }).get();
  }

  private static void readFully(InputStream inputStream, byte[] buffer) throws Exception {
    int offset = 0;
    while (offset < buffer.length) {
      int read = inputStream.read(buffer, offset, buffer.length - offset);
      if (read < 0) {
        throw new EOFException();
      }
      offset += read;
    }
  }

  static class SocketDebugConnector implements DebugConnector {
    private final int upstreamPort;

    SocketDebugConnector(int upstreamPort) {
      this.upstreamPort = upstreamPort;
    }

    @Override
    public BackendType getBackendType() {
      return BackendType.DOCKER;
    }

    @Override
    public Try<Socket> connect(DebugSession session) {
      return Try.of(() -> new Socket("127.0.0.1", upstreamPort));
    }
  }
}
