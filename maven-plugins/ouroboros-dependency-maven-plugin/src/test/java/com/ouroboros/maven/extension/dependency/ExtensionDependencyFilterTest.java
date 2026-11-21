package com.ouroboros.maven.extension.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionDependencyFilterTest {
  @TempDir
  Path tempDir;

  @Test
  void filtersExactMatchesAndKeepsVersionConflictsByDefault() throws Exception {
    Path input = tempDir.resolve("lib");
    Path output = tempDir.resolve("filtered");
    Files.createDirectories(input);
    writeJar(input.resolve("jackson-databind-2.13.4.1.jar"),
        "com.fasterxml.jackson.core", "jackson-databind", "2.13.4.1");
    writeJar(input.resolve("mybatis-3.5.4.jar"), "org.mybatis", "mybatis", "3.5.4");
    writeJar(input.resolve("custom-lib-1.0.0.jar"), "com.example", "custom-lib", "1.0.0");

    FilterResult result = new ExtensionDependencyFilter(new SystemStreamLog())
        .filter(input.toFile(), output.toFile(), manifest(), false, true);

    List<String> outputFiles = listFileNames(output);
    assertFalse(outputFiles.contains("jackson-databind-2.13.4.1.jar"));
    assertTrue(outputFiles.contains("mybatis-3.5.4.jar"));
    assertTrue(outputFiles.contains("custom-lib-1.0.0.jar"));
    assertEquals(1, result.getExcluded().size());
    assertEquals(2, result.getCopied().size());
  }

  @Test
  void canIgnoreVersionDifferencesWhenConfigured() throws Exception {
    Path input = tempDir.resolve("lib");
    Path output = tempDir.resolve("filtered");
    Files.createDirectories(input);
    writeJar(input.resolve("mybatis-3.5.4.jar"), "org.mybatis", "mybatis", "3.5.4");

    FilterResult result = new ExtensionDependencyFilter(new SystemStreamLog())
        .filter(input.toFile(), output.toFile(), manifest(), true, true);

    assertTrue(listFileNames(output).isEmpty());
    assertEquals(1, result.getExcluded().size());
    assertEquals("runtime-manifest-group-artifact-match-version-ignored",
        result.getExcluded().get(0).getReason());
  }

  @Test
  void treatsMissingInputDirectoryAsEmptyDependencySet() throws Exception {
    Path input = tempDir.resolve("missing-lib");
    Path output = tempDir.resolve("filtered");

    FilterResult result = new ExtensionDependencyFilter(new SystemStreamLog())
        .filter(input.toFile(), output.toFile(), manifest(), false, true);

    assertTrue(Files.isDirectory(output));
    assertTrue(result.getDecisions().isEmpty());
  }

  private RuntimeManifest manifest() {
    RuntimeManifest manifest = new RuntimeManifest();
    manifest.setSchemaVersion(1);
    manifest.setPlatform("ouroboros");
    manifest.setRuntime("mothership");
    manifest.setEntries(Arrays.asList(
        entry("com.fasterxml.jackson.core", "jackson-databind", "2.13.4.1", "third-party"),
        entry("org.mybatis", "mybatis", "3.5.3", "third-party")));
    return manifest;
  }

  private RuntimeManifestEntry entry(String groupId, String artifactId, String version, String category) {
    RuntimeManifestEntry entry = new RuntimeManifestEntry();
    entry.setGroupId(groupId);
    entry.setArtifactId(artifactId);
    entry.setVersion(version);
    entry.setCategory(category);
    entry.setFileName(artifactId + "-" + version + ".jar");
    entry.setSha256("test");
    return entry;
  }

  private void writeJar(Path target, String groupId, String artifactId, String version) throws IOException {
    try (OutputStream outputStream = Files.newOutputStream(target);
         JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
      String pomPropertiesPath = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
      jarOutputStream.putNextEntry(new JarEntry(pomPropertiesPath));
      Properties properties = new Properties();
      properties.setProperty("groupId", groupId);
      properties.setProperty("artifactId", artifactId);
      properties.setProperty("version", version);
      properties.store(jarOutputStream, null);
      jarOutputStream.closeEntry();
    }
  }

  private List<String> listFileNames(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return Arrays.asList();
    }
    try (Stream<Path> stream = Files.list(directory)) {
      return stream.map(path -> path.getFileName().toString()).sorted().collect(Collectors.toList());
    }
  }
}
