package com.ouroboros.dev.projectgeneration.catalog;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class JavaNetProjectTemplateRemoteHttpClient implements ProjectTemplateRemoteHttpClient {

  private static final int MAX_HEADER_LINE_BYTES = 16 * 1024;
  private static final int MAX_HEADER_BYTES = 64 * 1024;

  private final SSLSocketFactory sslSocketFactory;

  public JavaNetProjectTemplateRemoteHttpClient() {
    this((SSLSocketFactory) SSLSocketFactory.getDefault());
  }

  JavaNetProjectTemplateRemoteHttpClient(SSLSocketFactory sslSocketFactory) {
    if (sslSocketFactory == null) {
      throw new IllegalArgumentException("SSL socket factory is required");
    }
    this.sslSocketFactory = sslSocketFactory;
  }

  @Override
  public ProjectTemplateRemoteHttpResponse get(
      URI uri,
      ProjectTemplateRemoteFetchPolicy policy,
      long maxBytes,
      Duration connectTimeout,
      Duration readTimeout,
      Map<String, String> requestHeaders) {
    if (policy == null) {
      throw new IllegalArgumentException("Remote fetch policy is required");
    }
    if (maxBytes < 1) {
      throw new IllegalArgumentException("Remote fetch byte limit must be positive");
    }
    List<InetAddress> resolvedAddresses = resolve(uri);
    policy.validate(uri, resolvedAddresses);
    IOException lastFailure = null;
    for (InetAddress address : resolvedAddresses) {
      try {
        return fetchFromAddress(
            uri,
            policy,
            address,
            resolvedAddresses,
            maxBytes,
            connectTimeout,
            readTimeout,
            requestHeaders
        );
      } catch (IOException e) {
        lastFailure = e;
      }
    }
    throw new ProjectTemplateCatalogRemoteException("Remote catalog HTTPS request failed", lastFailure);
  }

  private ProjectTemplateRemoteHttpResponse fetchFromAddress(
      URI uri,
      ProjectTemplateRemoteFetchPolicy policy,
      InetAddress address,
      List<InetAddress> resolvedAddresses,
      long maxBytes,
      Duration connectTimeout,
      Duration readTimeout,
      Map<String, String> requestHeaders) throws IOException {
    int port = uri.getPort() == -1 ? 443 : uri.getPort();
    String host = uri.getHost();
    Socket plainSocket = new Socket();
    plainSocket.connect(new InetSocketAddress(address, port), millis(connectTimeout, 3_000));
    plainSocket.setSoTimeout(millis(readTimeout, 5_000));
    policy.validatePeerAddress(uri, plainSocket.getInetAddress(), resolvedAddresses);
    try (SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(plainSocket, host, port, true)) {
      configureTlsHostVerification(sslSocket, host);
      sslSocket.startHandshake();
      policy.validatePeerAddress(uri, sslSocket.getInetAddress(), resolvedAddresses);
      writeRequest(sslSocket.getOutputStream(), uri, requestHeaders);
      return readResponse(sslSocket.getInputStream(), maxBytes, resolvedAddresses, sslSocket.getInetAddress());
    }
  }

  private static void configureTlsHostVerification(SSLSocket socket, String host) {
    SSLParameters parameters = socket.getSSLParameters();
    parameters.setEndpointIdentificationAlgorithm("HTTPS");
    parameters.setServerNames(List.of(new SNIHostName(host)));
    socket.setSSLParameters(parameters);
  }

  private static void writeRequest(OutputStream outputStream, URI uri, Map<String, String> requestHeaders)
      throws IOException {
    StringBuilder builder = new StringBuilder();
    builder.append("GET ").append(requestTarget(uri)).append(" HTTP/1.1\r\n");
    builder.append("Host: ").append(hostHeader(uri)).append("\r\n");
    builder.append("Accept: application/json, */*;q=0.1\r\n");
    builder.append("Connection: close\r\n");
    if (requestHeaders != null) {
      for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
        builder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
      }
    }
    builder.append("\r\n");
    outputStream.write(builder.toString().getBytes(StandardCharsets.ISO_8859_1));
    outputStream.flush();
  }

  private static String requestTarget(URI uri) {
    String rawPath = uri.getRawPath();
    String target = rawPath == null || rawPath.isBlank() ? "/" : rawPath;
    if (uri.getRawQuery() != null) {
      target = target + "?" + uri.getRawQuery();
    }
    return target;
  }

  private static String hostHeader(URI uri) {
    int port = uri.getPort();
    if (port == -1 || port == 443) {
      return uri.getHost();
    }
    return uri.getHost() + ":" + port;
  }

  private static ProjectTemplateRemoteHttpResponse readResponse(
      InputStream inputStream,
      long maxBytes,
      List<InetAddress> resolvedAddresses,
      InetAddress peerAddress) throws IOException {
    HeaderReader reader = new HeaderReader(inputStream);
    String statusLine = reader.readLine();
    int statusCode = parseStatusCode(statusLine);
    Map<String, List<String>> headers = readHeaders(reader);
    byte[] body = readBody(inputStream, headers, maxBytes);
    return new ProjectTemplateRemoteHttpResponse(statusCode, body, resolvedAddresses, peerAddress, headers);
  }

  private static Map<String, List<String>> readHeaders(HeaderReader reader) throws IOException {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    String line;
    while (!(line = reader.readLine()).isEmpty()) {
      int separator = line.indexOf(':');
      if (separator <= 0) {
        throw new IOException("Malformed HTTP response header");
      }
      String name = line.substring(0, separator).toLowerCase(Locale.ROOT);
      String value = line.substring(separator + 1).trim();
      headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
    }
    return headers;
  }

  private static byte[] readBody(InputStream inputStream, Map<String, List<String>> headers, long maxBytes)
      throws IOException {
    if (containsHeaderValue(headers, "transfer-encoding", "chunked")) {
      return readChunkedBody(inputStream, maxBytes);
    }
    String contentLength = firstHeader(headers, "content-length");
    if (contentLength != null) {
      long expectedLength = parseContentLength(contentLength);
      if (expectedLength > maxBytes) {
        throw new ProjectTemplateCatalogRemoteException("Remote catalog exceeded byte limit");
      }
      return readExactly(inputStream, expectedLength);
    }
    return readUntilEof(inputStream, maxBytes);
  }

  private static byte[] readChunkedBody(InputStream inputStream, long maxBytes) throws IOException {
    HeaderReader reader = new HeaderReader(inputStream);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    while (true) {
      String sizeLine = reader.readLine();
      int extensionIndex = sizeLine.indexOf(';');
      String sizeText = extensionIndex == -1 ? sizeLine : sizeLine.substring(0, extensionIndex);
      long chunkSize;
      try {
        chunkSize = Long.parseLong(sizeText.trim(), 16);
      } catch (NumberFormatException e) {
        throw new IOException("Malformed HTTP chunk size", e);
      }
      if (chunkSize == 0) {
        while (!reader.readLine().isEmpty()) {
          // consume trailers
        }
        return output.toByteArray();
      }
      copyLimited(inputStream, output, chunkSize, maxBytes);
      expectCrlf(inputStream);
    }
  }

  private static void expectCrlf(InputStream inputStream) throws IOException {
    int carriageReturn = inputStream.read();
    int lineFeed = inputStream.read();
    if (carriageReturn != '\r' || lineFeed != '\n') {
      throw new IOException("Malformed HTTP chunk terminator");
    }
  }

  private static byte[] readExactly(InputStream inputStream, long expectedLength) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    copyLimited(inputStream, output, expectedLength, expectedLength);
    return output.toByteArray();
  }

  private static byte[] readUntilEof(InputStream inputStream, long maxBytes) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = inputStream.read(buffer)) != -1) {
      appendLimited(output, buffer, read, maxBytes);
    }
    return output.toByteArray();
  }

  private static void copyLimited(InputStream inputStream, ByteArrayOutputStream output, long bytes, long maxBytes)
      throws IOException {
    byte[] buffer = new byte[8192];
    long remaining = bytes;
    while (remaining > 0) {
      int requested = (int) Math.min(buffer.length, remaining);
      int read = inputStream.read(buffer, 0, requested);
      if (read == -1) {
        throw new EOFException("Unexpected end of HTTP response");
      }
      appendLimited(output, buffer, read, maxBytes);
      remaining -= read;
    }
  }

  private static void appendLimited(ByteArrayOutputStream output, byte[] buffer, int read, long maxBytes) {
    if ((long) output.size() + read > maxBytes) {
      throw new ProjectTemplateCatalogRemoteException("Remote catalog exceeded byte limit");
    }
    output.write(buffer, 0, read);
  }

  private static int parseStatusCode(String statusLine) throws IOException {
    if (statusLine == null || !statusLine.startsWith("HTTP/")) {
      throw new IOException("Malformed HTTP response status");
    }
    String[] parts = statusLine.split(" ", 3);
    if (parts.length < 2) {
      throw new IOException("Malformed HTTP response status");
    }
    try {
      return Integer.parseInt(parts[1]);
    } catch (NumberFormatException e) {
      throw new IOException("Malformed HTTP response status", e);
    }
  }

  private static String firstHeader(Map<String, List<String>> headers, String name) {
    List<String> values = headers.get(name);
    if (values == null || values.isEmpty()) {
      return null;
    }
    return values.getFirst();
  }

  private static boolean containsHeaderValue(Map<String, List<String>> headers, String name, String expectedValue) {
    List<String> values = headers.get(name);
    if (values == null) {
      return false;
    }
    for (String value : values) {
      if (expectedValue.equalsIgnoreCase(value)) {
        return true;
      }
    }
    return false;
  }

  private static long parseContentLength(String value) throws IOException {
    try {
      long parsed = Long.parseLong(value);
      if (parsed < 0) {
        throw new IOException("Negative HTTP content length");
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new IOException("Malformed HTTP content length", e);
    }
  }

  private static List<InetAddress> resolve(URI uri) {
    try {
      return List.of(InetAddress.getAllByName(uri.getHost()));
    } catch (IOException e) {
      throw new ProjectTemplateCatalogRemoteException("Remote catalog host cannot be resolved", e);
    }
  }

  private static int millis(Duration duration, int fallback) {
    if (duration == null || duration.isNegative() || duration.isZero()) {
      return fallback;
    }
    long millis = duration.toMillis();
    return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
  }

  private static final class HeaderReader {
    private final InputStream inputStream;
    private int totalHeaderBytes;

    private HeaderReader(InputStream inputStream) {
      this.inputStream = inputStream;
    }

    private String readLine() throws IOException {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      while (true) {
        int value = inputStream.read();
        if (value == -1) {
          throw new EOFException("Unexpected end of HTTP headers");
        }
        totalHeaderBytes++;
        if (totalHeaderBytes > MAX_HEADER_BYTES || output.size() > MAX_HEADER_LINE_BYTES) {
          throw new IOException("HTTP response headers are too large");
        }
        if (value == '\n') {
          byte[] lineBytes = output.toByteArray();
          int length = lineBytes.length;
          if (length > 0 && lineBytes[length - 1] == '\r') {
            length--;
          }
          return new String(lineBytes, 0, length, StandardCharsets.ISO_8859_1);
        }
        output.write(value);
      }
    }
  }
}
