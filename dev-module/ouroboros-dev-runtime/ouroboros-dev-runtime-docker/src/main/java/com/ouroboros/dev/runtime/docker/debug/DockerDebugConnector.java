package com.ouroboros.dev.runtime.docker.debug;

import java.net.Socket;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugTunnelTargetType;
import com.ouroboros.dev.runtime.service.DebugConnector;

public class DockerDebugConnector implements DebugConnector {
  private final DockerDebugTargetResolver targetResolver;
  private final SocketOpener socketOpener;

  public DockerDebugConnector(DockerDebugTargetResolver targetResolver) {
    this(targetResolver, Socket::new);
  }

  DockerDebugConnector(DockerDebugTargetResolver targetResolver, SocketOpener socketOpener) {
    this.targetResolver = targetResolver;
    this.socketOpener = socketOpener;
  }

  @Override
  public BackendType getBackendType() {
    return BackendType.DOCKER;
  }

  @Override
  public Try<Socket> connect(DebugSession session) {
    return targetResolver.resolve(session, targetPort(session))
        .flatMap(target -> Try.of(() -> socketOpener.open(target.getHost(), target.getPort())));
  }

  private static int targetPort(DebugSession session) {
    DebugTunnelTargetType targetType = session == null ? null : session.getTargetType();
    return targetType == null ? DebugTunnelTargetType.defaultType().getTargetPort() : targetType.getTargetPort();
  }

  @FunctionalInterface
  public interface SocketOpener {
    Socket open(String host, int port) throws Exception;
  }
}
