package com.ouroboros.devhelper.dev.transport.socket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.transport.DevHelperClientTransport;
import com.ouroboros.devhelper.transport.TransportSubscription;
import com.ouroboros.devhelper.transport.socket.HelperSocketFrames;
import com.ouroboros.util.Json;
import org.slf4j.LoggerFactory;

/** Reconnects on the next send; commands are never replayed after a broken connection. */
public final class SocketDevHelperClientTransport implements DevHelperClientTransport, AutoCloseable {
  private final String host;
  private final int port;
  private final String token;
  private final Object connectionLock = new Object();
  private final CopyOnWriteArrayList<Consumer<CommandResult>> receivers = new CopyOnWriteArrayList<>();
  private final java.util.concurrent.ExecutorService reader = Executors.newVirtualThreadPerTaskExecutor();
  private Socket socket;
  private DataOutputStream output;
  private boolean closed;

  public SocketDevHelperClientTransport(String host, int port, String token) {
    HelperSocketFrames.requireToken(token);
    this.host = host; this.port = port; this.token = token;
  }

  @Override public TransportSubscription subscribe(Consumer<CommandResult> receiver) {
    receivers.add(receiver);
    return () -> receivers.remove(receiver);
  }

  @Override public void send(CommandRequest request) {
    synchronized (connectionLock) {
      if (closed) throw new IllegalStateException("Helper transport is closed");
      try {
        if (socket == null) connect();
        HelperSocketFrames.write(output, Json.toJsonString(request), HelperSocketFrames.MAX_REQUEST_BYTES);
      } catch (IOException error) {
        disconnect(socket);
        throw new UncheckedIOException("Could not send helper command", error);
      }
    }
  }

  private void connect() throws IOException {
    var candidate = new Socket();
    try {
      candidate.connect(new InetSocketAddress(host, port), 5000);
      candidate.setSoTimeout(5000);
      var input = new DataInputStream(candidate.getInputStream());
      var stream = new DataOutputStream(candidate.getOutputStream());
      HelperSocketFrames.authenticateClient(input, stream, token);
      candidate.setSoTimeout(0);
      socket = candidate; output = stream;
      reader.submit(() -> receive(candidate, input));
    } catch (IOException error) {
      candidate.close();
      throw error;
    }
  }

  private void receive(Socket connection, DataInputStream input) {
    try {
      while (!connection.isClosed()) {
        var result = Json.tryToBean(CommandResult.class, HelperSocketFrames.read(input, HelperSocketFrames.MAX_RESULT_BYTES)).get();
        if (result == null) throw new IOException("Malformed helper result");
        for (var receiver : receivers) {
          try { receiver.accept(result); }
          catch (RuntimeException error) { LoggerFactory.getLogger(getClass()).error("Helper result listener failed", error); }
        }
      }
    } catch (IOException | RuntimeException error) {
      if (!connection.isClosed()) LoggerFactory.getLogger(getClass()).debug("Helper socket disconnected: {}", error.toString());
    } finally {
      synchronized (connectionLock) { disconnect(connection); }
    }
  }

  private void disconnect(Socket connection) {
    if (connection == null) return;
    try { connection.close(); }
    catch (IOException error) { LoggerFactory.getLogger(getClass()).warn("Could not close helper connection", error); }
    if (socket == connection) { socket = null; output = null; }
  }

  @Override public void close() {
    synchronized (connectionLock) { closed = true; disconnect(socket); }
    receivers.clear();
    reader.shutdownNow();
  }
}
