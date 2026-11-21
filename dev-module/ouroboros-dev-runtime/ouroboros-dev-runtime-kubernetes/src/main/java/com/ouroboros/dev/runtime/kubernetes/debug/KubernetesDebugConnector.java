package com.ouroboros.dev.runtime.kubernetes.debug;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.vavr.control.Try;

import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.service.DebugConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesDebugConnector implements DebugConnector {
  private static final Logger logger = LoggerFactory.getLogger(KubernetesDebugConnector.class);
  private final PodResolver podResolver;
  private final PortForwarder portForwarder;
  private final SocketOpener socketOpener;

  public KubernetesDebugConnector(KubernetesClient kubernetesClient, KubernetesRuntimeProperties properties) {
    this(
        new ClientPodResolver(kubernetesClient, properties),
        new ClientPortForwarder(kubernetesClient),
        Socket::new
    );
  }

  KubernetesDebugConnector(PodResolver podResolver, PortForwarder portForwarder, SocketOpener socketOpener) {
    this.podResolver = podResolver;
    this.portForwarder = portForwarder;
    this.socketOpener = socketOpener;
  }

  @Override
  public BackendType getBackendType() {
    return BackendType.KUBERNETES;
  }

  @Override
  public Try<Socket> connect(DebugSession session) {
    return Try.of(() -> {
      Pod pod = podResolver.resolve(session.getRuntimeKey());
      String namespace = namespaceOf(pod);
      String podName = podNameOf(pod);
      LocalPortForward portForward = portForwarder.open(namespace, podName, targetPort(session));
      try {
        InetAddress localAddress = portForward.getLocalAddress();
        String host = localAddress == null ? "127.0.0.1" : localAddress.getHostAddress();
        Socket socket = socketOpener.open(host, portForward.getLocalPort());
        return new PortForwardSocket(socket, portForward);
      } catch (Exception error) {
        closeQuietly(portForward);
        throw error;
      }
    });
  }

  private static int targetPort(DebugSession session) {
    DebugTunnelTargetType targetType = session == null ? null : session.getTargetType();
    return targetType == null ? DebugTunnelTargetType.defaultType().getTargetPort() : targetType.getTargetPort();
  }

  private static String namespaceOf(Pod pod) {
    if (pod == null || pod.getMetadata() == null || pod.getMetadata().getNamespace() == null) {
      throw new IllegalStateException("debug pod namespace is missing");
    }
    return pod.getMetadata().getNamespace();
  }

  private static String podNameOf(Pod pod) {
    if (pod == null || pod.getMetadata() == null || pod.getMetadata().getName() == null) {
      throw new IllegalStateException("debug pod name is missing");
    }
    return pod.getMetadata().getName();
  }

  private static void closeQuietly(LocalPortForward portForward) {
    if (portForward == null) {
      return;
    }
    try {
      portForward.close();
    } catch (IOException error) {
      logger.warn("Failed to close port forward: {}", error.getMessage());
    }
  }

  interface PodResolver {
    Pod resolve(RuntimeIdentityKey runtimeKey) throws Exception;
  }

  interface PortForwarder {
    LocalPortForward open(String namespace, String podName, int remotePort) throws Exception;
  }

  @FunctionalInterface
  interface SocketOpener {
    Socket open(String host, int port) throws Exception;
  }

  private static class ClientPodResolver implements PodResolver {
    private final KubernetesClient kubernetesClient;
    private final KubernetesRuntimeProperties properties;

    private ClientPodResolver(KubernetesClient kubernetesClient, KubernetesRuntimeProperties properties) {
      this.kubernetesClient = kubernetesClient;
      this.properties = properties;
    }

    @Override
    public Pod resolve(RuntimeIdentityKey runtimeKey) {
      Map<String, String> labels = KubernetesResourceNameFactory.labels(runtimeKey);
      List<Pod> pods = kubernetesClient.pods()
          .inNamespace(properties.getNamespace())
          .withLabels(labels)
          .list()
          .getItems();
      if (pods == null || pods.isEmpty()) {
        throw new IllegalStateException("debug pod not found for " + runtimeKey);
      }
      Pod ready = firstReadyPod(pods);
      return ready == null ? pods.get(0) : ready;
    }

    private Pod firstReadyPod(List<Pod> pods) {
      for (Pod pod : pods) {
        if (isReady(pod)) {
          return pod;
        }
      }
      return null;
    }

    private boolean isReady(Pod pod) {
      if (pod == null || pod.getStatus() == null || pod.getStatus().getConditions() == null) {
        return false;
      }
      for (PodCondition condition : pod.getStatus().getConditions()) {
        if ("Ready".equals(condition.getType()) && "True".equalsIgnoreCase(condition.getStatus())) {
          return true;
        }
      }
      return false;
    }
  }

  private static class ClientPortForwarder implements PortForwarder {
    private final KubernetesClient kubernetesClient;

    private ClientPortForwarder(KubernetesClient kubernetesClient) {
      this.kubernetesClient = kubernetesClient;
    }

    @Override
    public LocalPortForward open(String namespace, String podName, int remotePort) {
      return kubernetesClient.pods()
          .inNamespace(namespace)
          .withName(podName)
          .portForward(remotePort);
    }
  }

  static class PortForwardSocket extends Socket {
    private final Socket delegate;
    private final LocalPortForward portForward;

    PortForwardSocket(Socket delegate, LocalPortForward portForward) {
      this.delegate = delegate;
      this.portForward = portForward;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return delegate.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
      return delegate.getOutputStream();
    }

    @Override
    public synchronized void close() throws IOException {
      IOException failure = null;
      try {
        delegate.close();
      } catch (IOException error) {
        failure = error;
      }
      try {
        portForward.close();
      } catch (IOException error) {
        if (failure == null) {
          failure = error;
        }
      }
      if (failure != null) {
        throw failure;
      }
    }
  }
}
