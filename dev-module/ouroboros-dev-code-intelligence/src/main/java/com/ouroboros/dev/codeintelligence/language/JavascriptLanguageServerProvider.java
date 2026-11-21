package com.ouroboros.dev.codeintelligence.language;

import java.nio.file.Path;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

/** JavaScript editor semantics stay in its isolated browser worker; output parsing runs in a dedicated JVM. */
public final class JavascriptLanguageServerProvider implements LanguageServerProvider {
  private final String engineVersion = version();
  public String languageId() { return "javascript"; }
  public String engineVersion() { return engineVersion; }
  private static String version() {
    try (var bytes = com.oracle.js.parser.Parser.class.getResourceAsStream("Parser.class")) {
      if (bytes == null) throw new IllegalStateException("Missing JavaScript parser identity");
      return "graaljs-parser-" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes()));
    } catch (java.io.IOException | java.security.NoSuchAlgorithmException error) { throw new IllegalStateException("Could not identify JavaScript parser", error); }
  }
  public String fileExtension() { return ".js"; }
  public boolean available() { return true; }
  public List<JsonNode> contextMessages(Path workspace, JsonNode context) { return List.of(context); }
  public List<String> command(Path workspace, LanguageEnvironment environment) {
    String classpath = new io.github.classgraph.ClassGraph().getClasspath();
    return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xms32m", "-Xmx256m", "-XX:ActiveProcessorCount=2",
        "-cp", classpath, JavascriptOutputServer.class.getName());
  }
}
