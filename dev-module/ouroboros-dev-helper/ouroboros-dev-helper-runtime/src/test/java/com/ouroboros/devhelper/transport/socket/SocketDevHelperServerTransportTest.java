package com.ouroboros.devhelper.transport.socket;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import com.ouroboros.devhelper.command.*;
import com.ouroboros.util.Json;
import org.junit.jupiter.api.Test;

class SocketDevHelperServerTransportTest {
  @Test void authenticatedFramesRoundTripAndUnsubscribe() throws Exception {
    try (var server = new SocketDevHelperServerTransport("127.0.0.1", 0, "secret"); var socket = new Socket("127.0.0.1", server.port())) {
      socket.setSoTimeout(2000);
      var input = new DataInputStream(socket.getInputStream()); var output = new DataOutputStream(socket.getOutputStream());
      var received = new LinkedBlockingQueue<CommandRequest>();
      var subscription = server.subscribe(request -> { received.add(request); server.send(new CommandResult(request, "你好")); });
      HelperSocketFrames.authenticateClient(input, output, "secret");
      var request = new CommandRequest().setCommand("hello").setSequenceId("one");
      HelperSocketFrames.write(output, Json.toJsonString(request), HelperSocketFrames.MAX_REQUEST_BYTES);
      assertEquals("one", received.poll(2, TimeUnit.SECONDS).getSequenceId());
      var result = Json.toBean(CommandResult.class, HelperSocketFrames.read(input, HelperSocketFrames.MAX_RESULT_BYTES));
      assertEquals("你好", result.getData());
      subscription.close();
      HelperSocketFrames.write(output, Json.toJsonString(request), HelperSocketFrames.MAX_REQUEST_BYTES);
      assertNull(received.poll(100, TimeUnit.MILLISECONDS));
    }
  }
  @Test void rejectsBadTokenAndOversizedFrames() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> new SocketDevHelperServerTransport("127.0.0.1", 0, ""));
    try (var server = new SocketDevHelperServerTransport("127.0.0.1", 0, "secret"); var socket = new Socket("127.0.0.1", server.port())) {
      socket.setSoTimeout(2000);
      assertThrows(IOException.class, () -> HelperSocketFrames.authenticateClient(new DataInputStream(socket.getInputStream()), new DataOutputStream(socket.getOutputStream()), "wrong"));
    }
    var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes); output.writeInt(Integer.MAX_VALUE);
    assertThrows(IOException.class, () -> HelperSocketFrames.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 1024));
    assertThrows(IOException.class, () -> HelperSocketFrames.write(output, "long", 2));
    bytes.reset(); output.writeInt(8); output.writeByte(1);
    assertThrows(EOFException.class, () -> HelperSocketFrames.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 1024));
  }
}
