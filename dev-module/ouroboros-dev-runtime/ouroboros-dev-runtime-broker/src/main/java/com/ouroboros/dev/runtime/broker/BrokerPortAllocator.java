package com.ouroboros.dev.runtime.broker;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.vavr.control.Try;

public class BrokerPortAllocator {
  private final int minPort;
  private final int maxPort;
  private final Set<Integer> reservedPorts = ConcurrentHashMap.newKeySet();

  public BrokerPortAllocator(int minPort, int maxPort) {
    if (minPort <= 0 || maxPort <= 0 || minPort > maxPort) {
      throw new IllegalArgumentException("invalid broker port range");
    }
    this.minPort = minPort;
    this.maxPort = maxPort;
  }

  public Try<Integer> allocate() {
    for (int port = minPort; port <= maxPort; port++) {
      if (!reservedPorts.add(Integer.valueOf(port))) {
        continue;
      }
      if (isAvailable(port)) {
        return Try.success(Integer.valueOf(port));
      }
      reservedPorts.remove(Integer.valueOf(port));
    }
    return Try.failure(new IllegalStateException("no broker port available"));
  }

  public void release(int port) {
    reservedPorts.remove(Integer.valueOf(port));
  }

  private boolean isAvailable(int port) {
    try (ServerSocket serverSocket = new ServerSocket()) {
      serverSocket.setReuseAddress(true);
      serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
      return true;
    } catch (IOException error) {
      return false;
    }
  }
}
