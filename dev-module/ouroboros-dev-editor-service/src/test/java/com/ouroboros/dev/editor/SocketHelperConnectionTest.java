package com.ouroboros.dev.editor;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import com.ouroboros.devhelper.transport.socket.SocketDevHelperServerTransport;
import com.ouroboros.devhelper.dev.transport.socket.SocketDevHelperClientTransport;
import com.ouroboros.devhelper.command.*;
import static org.awaitility.Awaitility.await;

class SocketHelperConnectionTest {
  @Test void reconnectsWithoutReplayingCommandsAndClosesSubscriptions() throws Exception {
    var server = new SocketDevHelperServerTransport("127.0.0.1", 0, "secret");
    int port = server.port();
    try (var client = new SocketDevHelperClientTransport("127.0.0.1", port, "secret")) {
      var results = new LinkedBlockingQueue<CommandResult>();
      var subscription = client.subscribe(results::add);
      server.subscribe(request -> server.send(new CommandResult(request, "first")));
      client.send(new CommandRequest().setCommand("hello").setSequenceId("a"));
      assertEquals("first", results.poll(2, TimeUnit.SECONDS).getData());
      server.close();
      try (var restarted = new SocketDevHelperServerTransport("127.0.0.1", port, "secret")) {
        restarted.subscribe(request -> restarted.send(new CommandResult(request, "restarted")));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
          client.send(new CommandRequest().setCommand("hello").setSequenceId("b"));
          var result = results.poll(200, TimeUnit.MILLISECONDS);
          assertNotNull(result); assertEquals("restarted", result.getData()); assertEquals("b", result.getSequenceId());
        });
        subscription.close(); client.close();
        assertThrows(IllegalStateException.class, () -> client.send(new CommandRequest().setCommand("closed")));
      }
    } finally { server.close(); }
  }
  @Test void rejectsWrongAuthenticationBeforeDispatch() throws Exception {
    try (var server = new SocketDevHelperServerTransport("127.0.0.1", 0, "secret");
         var client = new SocketDevHelperClientTransport("127.0.0.1", server.port(), "wrong")) {
      assertThrows(java.io.UncheckedIOException.class, () -> client.send(new CommandRequest().setCommand("hello")));
    }
  }
}
