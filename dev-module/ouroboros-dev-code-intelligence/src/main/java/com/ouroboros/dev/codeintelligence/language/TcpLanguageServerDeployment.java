package com.ouroboros.dev.codeintelligence.language;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Content-addressed environment delivery; no platform path crosses the wire. */
public final class TcpLanguageServerDeployment implements LanguageServerDeployment {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final InetSocketAddress address;
  public TcpLanguageServerDeployment(InetSocketAddress address) { this.address = address; }

  public Started start(LanguageServerProvider provider, Path workspace, LanguageEnvironment environment, Consumer<JsonNode> notifications) throws Exception {
    var connection = LanguageServerConnection.tcp(address, notifications);
    try {
      var manifest = JSON.createObjectNode().put("protocolVersion", 1).put("language", provider.languageId())
          .put("engineVersion", provider.engineVersion()).put("environmentVersion", environment.version()).put("javaMajor", environment.javaMajor());
      var artifacts = manifest.putArray("artifacts");
      var paths = new LinkedHashMap<String, Path>();
      for (Path path : environment.classpath()) {
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Remote language environments require immutable JAR artifacts");
        String digest;
        try (var input = Files.newInputStream(path)) {
          var hash = java.security.MessageDigest.getInstance("SHA-256");
          byte[] buffer = new byte[65536];
          for (int n; (n = input.read(buffer)) >= 0;) hash.update(buffer, 0, n);
          digest = HexFormat.of().formatHex(hash.digest());
        }
        paths.put(digest, path);
        artifacts.addObject().put("sha256", digest).put("size", Files.size(path));
      }
      var prepared = request(connection, "ouroboros/prepareEnvironment", manifest);
      for (var missing : prepared.path("missing")) {
        String digest = missing.asText(); Path path = paths.get(digest);
        if (path == null) throw new IllegalStateException("Language host requested an undeclared artifact");
        try (var input = Files.newInputStream(path)) {
          long offset = 0; byte[] chunk = new byte[131072];
          for (int n; (n = input.read(chunk)) > 0;) {
            request(connection, "ouroboros/putArtifact", JSON.createObjectNode().put("sha256", digest).put("offset", offset)
                .put("data", Base64.getEncoder().encodeToString(Arrays.copyOf(chunk, n))));
            offset += n;
          }
        }
      }
      var started = request(connection, "ouroboros/start", JSON.createObjectNode());
      return new Started(connection, URI.create(started.path("documentRoot").asText()));
    } catch (Exception error) {
      try { connection.close(); } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
      throw error;
    }
  }
  private static JsonNode request(LanguageServerConnection connection, String method, JsonNode params) {
    var request = JSON.createObjectNode().put("jsonrpc", "2.0").put("id", UUID.randomUUID().toString()).put("method", method);
    request.set("params", params);
    var response = connection.send(request).join();
    if (response.has("error")) throw new IllegalStateException("Language environment failed: " + response.get("error"));
    return response.path("result");
  }
}
