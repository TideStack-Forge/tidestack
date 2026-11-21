package com.ouroboros.dev.runtime.kubernetes.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.Socket;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.vavr.control.Try;

import com.ouroboros.dev.runtime.kubernetes.support.KubernetesRuntimeTestSupport;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionStatus;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;

class KubernetesDebugConnectorTest {

  @Test
  void shouldOpenPortForwardSocketToPodDebugPort() {
    RecordingPortForwarder portForwarder = new RecordingPortForwarder();
    RecordingSocketOpener socketOpener = new RecordingSocketOpener();
    KubernetesDebugConnector connector = new KubernetesDebugConnector(
        runtimeKey -> readyPod(),
        portForwarder,
        socketOpener
    );

    connector.connect(testSession()).get();

    assertEquals("dev-space", portForwarder.namespace);
    assertEquals("devrt-test-app-crm-alice-pod", portForwarder.podName);
    assertEquals(Integer.valueOf(5005), portForwarder.remotePort);
    assertEquals("127.0.0.1", socketOpener.host);
    assertEquals(Integer.valueOf(42005), socketOpener.port);
  }

  @Test
  void shouldSurfacePortForwardFailureAsTryFailure() {
    KubernetesDebugConnector connector = new KubernetesDebugConnector(
        runtimeKey -> readyPod(),
        (namespace, podName, remotePort) -> {
          throw new IllegalStateException("port-forward failed");
        },
        new RecordingSocketOpener()
    );

    Try<Socket> connection = connector.connect(testSession());

    assertTrue(connection.isFailure());
  }

  private static DebugSession testSession() {
    return DebugSession.builder()
        .sessionId("dbg-k8s-001")
        .runtimeKey(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.KUBERNETES))
        .status(DebugSessionStatus.OPENING)
        .build();
  }

  private static Pod readyPod() {
    return new PodBuilder(KubernetesRuntimeTestSupport.readyPod(KubernetesRuntimeTestSupport.tempPortSpec()))
        .editMetadata()
        .withName("devrt-test-app-crm-alice-pod")
        .endMetadata()
        .build();
  }

  static class RecordingPortForwarder implements KubernetesDebugConnector.PortForwarder {
    private String namespace;
    private String podName;
    private Integer remotePort;

    @Override
    public LocalPortForward open(String namespace, String podName, int remotePort) throws Exception {
      this.namespace = namespace;
      this.podName = podName;
      this.remotePort = Integer.valueOf(remotePort);
      return new FakeLocalPortForward();
    }
  }

  static class RecordingSocketOpener implements KubernetesDebugConnector.SocketOpener {
    private String host;
    private Integer port;

    @Override
    public Socket open(String host, int port) {
      this.host = host;
      this.port = Integer.valueOf(port);
      return new Socket();
    }
  }

  static class FakeLocalPortForward implements LocalPortForward {
    private boolean closed;

    @Override
    public InetAddress getLocalAddress() {
      return InetAddress.getLoopbackAddress();
    }

    @Override
    public int getLocalPort() {
      return 42005;
    }

    @Override
    public boolean isAlive() {
      return !closed;
    }

    @Override
    public boolean errorOccurred() {
      return false;
    }

    @Override
    public java.util.Collection<Throwable> getClientThrowables() {
      return java.util.Collections.emptyList();
    }

    @Override
    public java.util.Collection<Throwable> getServerThrowables() {
      return java.util.Collections.emptyList();
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
