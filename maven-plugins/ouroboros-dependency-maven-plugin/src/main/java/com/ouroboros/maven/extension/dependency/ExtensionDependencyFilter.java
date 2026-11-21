package com.ouroboros.maven.extension.dependency;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.logging.Log;

final class ExtensionDependencyFilter {
  private static final String ACTION_COPY = "copy";
  private static final String ACTION_EXCLUDE = "exclude";

  private final Log log;
  private final JarCoordinateReader jarCoordinateReader;

  ExtensionDependencyFilter(Log log) {
    this(log, new JarCoordinateReader());
  }

  ExtensionDependencyFilter(Log log, JarCoordinateReader jarCoordinateReader) {
    this.log = log;
    this.jarCoordinateReader = jarCoordinateReader;
  }

  FilterResult filter(File inputDirectory, File outputDirectory, RuntimeManifest runtimeManifest,
      boolean ignoreVersionDifferences, boolean cleanOutputDirectory) throws IOException {
    if (!inputDirectory.exists()) {
      if (cleanOutputDirectory) {
        deleteDirectory(outputDirectory.toPath());
      }
      Files.createDirectories(outputDirectory.toPath());
      log.info("No dependency input directory found: " + inputDirectory.getAbsolutePath());
      return new FilterResult(Collections.emptyList());
    }
    if (!inputDirectory.isDirectory()) {
      throw new IOException("Input path is not a directory: " + inputDirectory.getAbsolutePath());
    }
    if (cleanOutputDirectory) {
      deleteDirectory(outputDirectory.toPath());
    }
    Files.createDirectories(outputDirectory.toPath());

    RuntimeManifestIndex manifestIndex = RuntimeManifestIndex.from(runtimeManifest);
    List<DependencyDecision> decisions = new ArrayList<>();
    List<Path> jarFiles = listJarFiles(inputDirectory.toPath());

    for (Path jarFile : jarFiles) {
      ArtifactCoordinate coordinate = jarCoordinateReader.read(jarFile.toFile());
      RuntimeManifestEntry exactMatch = coordinate.hasGroupArtifactVersion()
          ? manifestIndex.findByGroupArtifactVersion(coordinate.groupArtifactVersionKey())
          : null;

      if (exactMatch != null) {
        decisions.add(new DependencyDecision(jarFile.getFileName().toString(), coordinate,
            ACTION_EXCLUDE, "runtime-manifest-exact-match", exactMatch));
        log.info("Excluded platform-provided dependency: " + jarFile.getFileName());
        continue;
      }

      RuntimeManifestEntry versionMismatch = coordinate.hasGroupArtifact()
          ? manifestIndex.findByGroupArtifact(coordinate.groupArtifactKey())
          : null;
      if (versionMismatch != null) {
        log.warn("Dependency version differs from runtime manifest: " + coordinate.groupArtifactKey()
            + " extensionVersion=" + coordinate.getVersion()
            + " runtimeVersion=" + versionMismatch.getVersion());
        if (ignoreVersionDifferences) {
          decisions.add(new DependencyDecision(jarFile.getFileName().toString(), coordinate,
              ACTION_EXCLUDE, "runtime-manifest-group-artifact-match-version-ignored",
              versionMismatch));
          continue;
        }
        decisions.add(new DependencyDecision(jarFile.getFileName().toString(), coordinate,
            ACTION_COPY, "runtime-manifest-version-differs", versionMismatch));
        copy(jarFile, outputDirectory.toPath());
        continue;
      }

      String reason = coordinate.hasGroupArtifact()
          ? "not-in-runtime-manifest"
          : "missing-jar-maven-coordinate";
      decisions.add(new DependencyDecision(jarFile.getFileName().toString(), coordinate,
          ACTION_COPY, reason, null));
      copy(jarFile, outputDirectory.toPath());
    }

    return new FilterResult(decisions);
  }

  private static List<Path> listJarFiles(Path inputDirectory) throws IOException {
    try (Stream<Path> stream = Files.list(inputDirectory)) {
      return stream
          .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .collect(Collectors.toList());
    }
  }

  private static void copy(Path source, Path outputDirectory) throws IOException {
    Files.copy(source, outputDirectory.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
  }

  private static void deleteDirectory(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }
    try (Stream<Path> stream = Files.walk(directory)) {
      List<Path> paths = stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
      for (Path path : paths) {
        Files.delete(path);
      }
    }
  }

  private static final class RuntimeManifestIndex {
    private final Map<String, RuntimeManifestEntry> byGroupArtifactVersion = new HashMap<>();
    private final Map<String, RuntimeManifestEntry> byGroupArtifact = new HashMap<>();

    static RuntimeManifestIndex from(RuntimeManifest runtimeManifest) {
      RuntimeManifestIndex index = new RuntimeManifestIndex();
      if (runtimeManifest.getEntries() == null) {
        return index;
      }
      for (RuntimeManifestEntry entry : runtimeManifest.getEntries()) {
        ArtifactCoordinate coordinate = new ArtifactCoordinate(
            entry.getGroupId(), entry.getArtifactId(), entry.getVersion());
        if (!coordinate.hasGroupArtifact()) {
          continue;
        }
        index.byGroupArtifact.putIfAbsent(coordinate.groupArtifactKey(), entry);
        if (coordinate.hasGroupArtifactVersion()) {
          index.byGroupArtifactVersion.put(coordinate.groupArtifactVersionKey(), entry);
        }
      }
      return index;
    }

    RuntimeManifestEntry findByGroupArtifactVersion(String key) {
      return byGroupArtifactVersion.get(key);
    }

    RuntimeManifestEntry findByGroupArtifact(String key) {
      return byGroupArtifact.get(key);
    }
  }
}
