package com.ouroboros.devhelper.transport.socket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Length-prefixed UTF-8 frames; no Java object deserialization is accepted. */
public final class HelperSocketFrames {
  public static final int MAX_REQUEST_BYTES = 262144;
  public static final int MAX_RESULT_BYTES = 32 * 1024 * 1024;
  private static final String PROTOCOL = "ouroboros-helper/1";

  private HelperSocketFrames() {}

  public static String read(DataInputStream input, int limit) throws IOException {
    int length = input.readInt();
    if (length <= 0 || length > limit) throw new IOException("Invalid helper frame length: " + length);
    byte[] bytes = input.readNBytes(length);
    if (bytes.length != length) throw new java.io.EOFException("Incomplete helper frame");
    return new String(bytes, StandardCharsets.UTF_8);
  }

  public static void write(DataOutputStream output, String value, int limit) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length == 0 || bytes.length > limit) throw new IOException("Helper frame exceeds limit");
    output.writeInt(bytes.length);
    output.write(bytes);
    output.flush();
  }

  public static void authenticateClient(DataInputStream input, DataOutputStream output, String token) throws IOException {
    write(output, PROTOCOL, 128);
    write(output, token, 4096);
    if (!"ready".equals(read(input, 128))) throw new IOException("Helper authentication rejected");
  }

  public static void authenticateServer(DataInputStream input, DataOutputStream output, String token) throws IOException {
    if (!PROTOCOL.equals(read(input, 128)) || !MessageDigest.isEqual(
        token.getBytes(StandardCharsets.UTF_8), read(input, 4096).getBytes(StandardCharsets.UTF_8))) {
      throw new IOException("Helper authentication rejected");
    }
    write(output, "ready", 128);
  }

  public static void requireToken(String token) {
    if (token == null || token.isBlank() || token.getBytes(StandardCharsets.UTF_8).length > 4096)
      throw new IllegalArgumentException("ouroboros.dev-helper.socket.token is required (maximum 4096 bytes)");
  }
}
