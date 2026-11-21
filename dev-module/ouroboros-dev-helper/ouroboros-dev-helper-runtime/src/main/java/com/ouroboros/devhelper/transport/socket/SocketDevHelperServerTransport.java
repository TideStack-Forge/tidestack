package com.ouroboros.devhelper.transport.socket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.transport.DevHelperServerTransport;
import com.ouroboros.devhelper.transport.TransportSubscription;
import com.ouroboros.util.Json;
import org.slf4j.LoggerFactory;

/** Local helper endpoint. Bind to a container interface only when explicitly configured. */
public final class SocketDevHelperServerTransport implements DevHelperServerTransport, AutoCloseable {
  private final ServerSocket server;
  private final String token;
  private final java.util.concurrent.ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
  private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
  private final CopyOnWriteArrayList<Consumer<CommandRequest>> receivers = new CopyOnWriteArrayList<>();
  private volatile boolean closed;

  public SocketDevHelperServerTransport(String address, int port, String token) throws IOException {
    HelperSocketFrames.requireToken(token);
    this.token = token;
    server = new ServerSocket();
    server.setReuseAddress(true);
    server.bind(new InetSocketAddress(address, port), 8);
    workers.submit(this::acceptConnections);
  }

  public int port() { return server.getLocalPort(); }

  private void acceptConnections() {
    while (!closed) {
      try {
        var socket = server.accept();
        if (connections.size() >= 8) { socket.close(); continue; }
        try {
          var connection = new Connection(socket);
          connections.add(connection);
          workers.submit(() -> receive(connection));
        } catch (IOException | RuntimeException error) {
          socket.close();
          if (!closed) LoggerFactory.getLogger(getClass()).error("Helper connection setup failed", error);
        }
      } catch (IOException error) {
        if (!closed) LoggerFactory.getLogger(getClass()).error("Helper socket accept failed", error);
      }
    }
  }

  private void receive(Connection connection) {
    try (var socket = connection.socket) {
      socket.setSoTimeout(5000);
      var input = new DataInputStream(socket.getInputStream());
      HelperSocketFrames.authenticateServer(input, connection.output, token);
      socket.setSoTimeout(0);
      connection.authenticated = true;
      while (!closed) {
        var request = Json.tryToBean(CommandRequest.class, HelperSocketFrames.read(input, HelperSocketFrames.MAX_REQUEST_BYTES)).get();
        if (request == null || request.getCommand() == null) throw new IOException("Malformed helper request");
        receivers.forEach(receiver -> receiver.accept(request));
      }
    } catch (IOException | RuntimeException error) {
      if (!closed) LoggerFactory.getLogger(getClass()).debug("Helper socket disconnected: {}", error.toString());
    } finally {
      connections.remove(connection);
    }
  }

  @Override public TransportSubscription subscribe(Consumer<CommandRequest> receiver) {
    receivers.add(receiver);
    return () -> receivers.remove(receiver);
  }

  @Override public void send(CommandResult result) {
    String json = Json.toJsonString(result);
    for (var connection : connections) {
      if (!connection.authenticated) continue;
      try {
        connection.send(json);
      } catch (IOException error) {
        connections.remove(connection);
        closeConnection(connection);
        LoggerFactory.getLogger(getClass()).warn("Could not send helper result; connection closed", error);
      }
    }
  }

  @Override public void close() throws IOException {
    closed = true;
    try { server.close(); }
    finally {
      connections.forEach(this::closeConnection);
      receivers.clear();
      workers.shutdownNow();
    }
  }

  private void closeConnection(Connection connection) {
    try { connection.socket.close(); }
    catch (IOException error) { LoggerFactory.getLogger(getClass()).warn("Could not close helper socket", error); }
  }

  private static final class Connection {
    final Socket socket;
    final DataOutputStream output;
    volatile boolean authenticated;
    Connection(Socket socket) throws IOException { this.socket = socket; output = new DataOutputStream(socket.getOutputStream()); }
    synchronized void send(String json) throws IOException { HelperSocketFrames.write(output, json, HelperSocketFrames.MAX_RESULT_BYTES); }
  }
}
