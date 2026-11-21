package com.ouroboros.dev.runtime.docker.debug;

import java.net.InetAddress;

import io.vavr.control.Try;

import com.ouroboros.container.model.Container;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;

public class DockerDebugTargetResolver {
  private final ContainerService containerService;
  private final HostnameResolver hostnameResolver;

  public DockerDebugTargetResolver(ContainerService containerService) {
    this(containerService, host -> {
      InetAddress.getByName(host);
      return true;
    });
  }

  DockerDebugTargetResolver(ContainerService containerService, HostnameResolver hostnameResolver) {
    this.containerService = containerService;
    this.hostnameResolver = hostnameResolver;
  }

  public Try<DockerDebugTarget> resolve(DebugSession session) {
    int targetPort = session == null ? com.ouroboros.dev.runtime.model.DebugTunnelTargetType.defaultType().getTargetPort() : session.getTargetType().getTargetPort();
    return resolve(session, targetPort);
  }

  public Try<DockerDebugTarget> resolve(DebugSession session, int targetPort) {
    RuntimeIdentityKey runtimeKey = session == null ? null : session.getRuntimeKey();
    if (runtimeKey == null) {
      return Try.failure(new IllegalArgumentException("runtime key is required"));
    }
    String containerName = containerName(runtimeKey);
    return containerService.getContainerInfoByName(containerName)
        .fold(Try::failure, container -> resolveTarget(containerName, container, targetPort));
  }

  private Try<DockerDebugTarget> resolveTarget(String containerName, Container container, int targetPort) {
    String host = shouldUseContainerName(container, containerName) ? containerName : text(container == null ? null : container.getIp());
    if (host == null) {
      return Try.failure(new IllegalStateException("docker debug target host is unavailable"));
    }
    return Try.success(new DockerDebugTarget(host, targetPort));
  }

  private boolean shouldUseContainerName(Container container, String containerName) {
    if (text(container == null ? null : container.getNetwork()) == null) {
      return false;
    }
    try {
      return hostnameResolver.canResolve(containerName);
    } catch (Exception error) {
      return false;
    }
  }

  private String containerName(RuntimeIdentityKey runtimeKey) {
    if (runtimeKey.getRuntimeKind() == RuntimeKind.DEV_HELPER) {
      return "ouroboros-dev-helper-" + runtimeKey.getAppName();
    }
    return "ouroboros-test-app-" + runtimeKey.getAppName() + "-" + runtimeKey.getProfile();
  }

  private static String text(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }

  @FunctionalInterface
  interface HostnameResolver {
    boolean canResolve(String host) throws Exception;
  }

  public static class DockerDebugTarget {
    private final String host;
    private final int port;

    DockerDebugTarget(String host, int port) {
      this.host = host;
      this.port = port;
    }

    public String getHost() {
      return host;
    }

    public int getPort() {
      return port;
    }
  }
}
