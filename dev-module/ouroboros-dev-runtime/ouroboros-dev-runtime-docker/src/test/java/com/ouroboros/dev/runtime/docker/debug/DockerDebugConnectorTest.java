package com.ouroboros.dev.runtime.docker.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Socket;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.container.model.Container;
import com.ouroboros.dev.runtime.docker.support.DockerRuntimeTestSupport;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;

class DockerDebugConnectorTest {

  @Test
  void shouldResolveTargetByContainerNameOnConfiguredNetwork() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    containerService.put(testContainer("ouroboros-test-app-crm-alice", "172.18.0.5", "dev-runtime-network"));
    RecordingSocketOpener socketOpener = new RecordingSocketOpener();
    DockerDebugConnector connector = new DockerDebugConnector(
        new DockerDebugTargetResolver(containerService, host -> true),
        socketOpener
    );

    connector.connect(testSession()).get();

    assertEquals("ouroboros-test-app-crm-alice", socketOpener.host);
    assertEquals(Integer.valueOf(5005), socketOpener.port);
  }

  @Test
  void shouldFallBackToContainerIpWhenContainerNameCannotBeResolved() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    containerService.put(testContainer("ouroboros-test-app-crm-alice", "172.18.0.5", "dev-runtime-network"));
    RecordingSocketOpener socketOpener = new RecordingSocketOpener();
    DockerDebugConnector connector = new DockerDebugConnector(
        new DockerDebugTargetResolver(containerService, host -> false),
        socketOpener
    );

    connector.connect(testSession()).get();

    assertEquals("172.18.0.5", socketOpener.host);
    assertEquals(Integer.valueOf(5005), socketOpener.port);
  }

  @Test
  void shouldSurfaceConnectorFailureAsTryFailure() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    containerService.put(testContainer("ouroboros-test-app-crm-alice", "172.18.0.5", "dev-runtime-network"));
    DockerDebugConnector connector = new DockerDebugConnector(
        new DockerDebugTargetResolver(containerService, host -> true),
        (host, port) -> {
          throw new IllegalStateException("socket open failed");
        }
    );

    Try<Socket> connection = connector.connect(testSession());

    assertTrue(connection.isFailure());
  }

  private static DebugSession testSession() {
    return DebugSession.builder()
        .sessionId("dbg-001")
        .runtimeKey(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER))
        .status(DebugSessionStatus.OPENING)
        .build();
  }

  private static Container testContainer(String name, String ip, String network) {
    Container container = new Container();
    container.setId("cid-1");
    container.setName(name);
    container.setIp(ip);
    container.setNetwork(network);
    container.setStatus(ContainerStatus.RUNNING);
    return container;
  }

  static class RecordingSocketOpener implements DockerDebugConnector.SocketOpener {
    private String host;
    private Integer port;

    @Override
    public Socket open(String host, int port) {
      this.host = host;
      this.port = Integer.valueOf(port);
      return new Socket();
    }
  }
}
