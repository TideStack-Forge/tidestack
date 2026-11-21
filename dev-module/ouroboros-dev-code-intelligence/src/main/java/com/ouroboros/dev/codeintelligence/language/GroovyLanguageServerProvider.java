package com.ouroboros.dev.codeintelligence.language;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Groovy-specific distribution and Java classpath projection; no session or transport logic. */
public final class GroovyLanguageServerProvider implements LanguageServerProvider {
  private final Path distribution;
  private final int maxHeapMiB;
  private final int processors;

  public GroovyLanguageServerProvider(Path distribution) { this(distribution, 768, 4); }
  public GroovyLanguageServerProvider(Path distribution, int maxHeapMiB, int processors) {
    this.distribution = distribution; this.maxHeapMiB = maxHeapMiB; this.processors = processors;
    if (maxHeapMiB < 64 || processors < 1) throw new IllegalArgumentException("Invalid language process resource limits");
  }
  public String languageId() { return "groovy"; }
  public String engineVersion() { return "groovy-4.0.30"; }
  public String fileExtension() { return ".groovy"; }
  public boolean available() { return distribution != null && Files.isRegularFile(distribution.resolve("launcher.jar")); }

  public List<JsonNode> contextMessages(Path workspace, JsonNode context) { return List.of(context); }
  public Optional<String> synchronizationMethod() { return Optional.of("ouroboros/barrier"); }

  public List<String> command(Path workspace, LanguageEnvironment environment) throws Exception {
    if (!available()) throw new IllegalStateException("Groovy language server distribution is not installed");
    var paths = environment.classpath().stream().map(path -> path.toAbsolutePath().toString()).toList();
    if (paths.isEmpty()) throw new IllegalStateException("Application classpath is empty");
    Path classpath = workspace.resolve("classpath.json");
    new ObjectMapper().writeValue(classpath.toFile(), paths);
    return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-Xms64m", "-Xmx" + maxHeapMiB + "m", "-XX:ActiveProcessorCount=" + processors, "-Djava.awt.headless=true", "-jar",
        distribution.resolve("launcher.jar").toString(), workspace.toString(), classpath.toString());
  }
}
