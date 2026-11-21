package com.ouroboros.devhelper.codeenvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import io.github.classgraph.ClassGraph;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/** Exports immutable analysis inputs; it neither starts a compiler nor loads exported classes. */
@Component
public final class ApplicationCodeEnvironment implements AutoCloseable {
  public static final int CHUNK_BYTES = 131072;
  private final ClassLoader loader;
  private final Map<String, Path> artifacts = new LinkedHashMap<>();
  private CodeEnvironmentManifest manifest;
  private Path directory;

  public ApplicationCodeEnvironment() { this(Thread.currentThread().getContextClassLoader()); }
  public ApplicationCodeEnvironment(ClassLoader loader) { this.loader = loader; }

  public synchronized CodeEnvironmentManifest manifest() throws IOException {
    if (manifest != null) return manifest;
    directory = Files.createTempDirectory("ouroboros-code-environment-");
    var entries = new java.util.ArrayList<CodeEnvironmentManifest.Artifact>();
    var loaders = new java.util.ArrayList<ClassLoader>();
    for (ClassLoader current = loader; current != null && current != ClassLoader.getPlatformClassLoader(); current = current.getParent()) loaders.add(current);
    // Include the system parent explicitly: overriding only a child suppresses java.class.path discovery.
    try (var scan = new ClassGraph().overrideClassLoaders(loaders.toArray(ClassLoader[]::new)).scan(2)) {
      var elements = new LinkedHashMap<java.net.URI, java.util.List<io.github.classgraph.Resource>>();
      for (var resource : scan.getAllResources()) elements.computeIfAbsent(resource.getClasspathElementURI(), key -> new java.util.ArrayList<>()).add(resource);
      for (var resources : elements.values()) {
        var resource = resources.getFirst();
        var file = resource.getClasspathElementFile();
        Path path = file == null ? null : file.toPath();
        boolean packageRoot = resources.stream().anyMatch(r -> !r.getPath().equals(r.getPathRelativeToClasspathElement()));
        if (path == null || Files.isDirectory(path) || packageRoot) path = archive(resources, entries.size());
        else if (!"file".equals(resource.getClasspathElementURI().getScheme())) {
          // ClassGraph owns extracted nested JARs; retain them before closing the scan.
          path = Files.copy(path, directory.resolve(entries.size() + ".jar"));
        }
        long size = Files.size(path);
        if (size > 268435456L || entries.size() >= 4096) throw new IOException("Application classpath exceeds export limits");
        String hash = digest(path);
        if (artifacts.putIfAbsent(hash, path) == null) entries.add(new CodeEnvironmentManifest.Artifact(hash, size));
      }
      if (entries.isEmpty()) throw new IOException("Application classpath is empty");
      int javaMajor = Runtime.version().feature();
      String version = com.ouroboros.codeintelligence.runtime.ContentVersion.of(java.util.List.of(javaMajor, entries));
      manifest = new CodeEnvironmentManifest(version, javaMajor, entries);
      return manifest;
    } catch (IOException | RuntimeException failure) {
      try { close(); } catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
      throw failure;
    }
  }

  public synchronized byte[] read(String version, String hash, long offset) throws IOException {
    if (manifest == null || !manifest.version().equals(version) || !artifacts.containsKey(hash))
      throw new IllegalArgumentException("Unknown application environment artifact");
    Path path = artifacts.get(hash);
    if (offset < 0 || offset >= Files.size(path)) throw new IllegalArgumentException("Invalid artifact offset");
    try (var channel = Files.newByteChannel(path)) {
      channel.position(offset);
      var bytes = java.nio.ByteBuffer.allocate((int) Math.min(CHUNK_BYTES, channel.size() - offset));
      while (bytes.hasRemaining() && channel.read(bytes) >= 0) { }
      return bytes.array();
    }
  }

  private Path archive(java.util.List<io.github.classgraph.Resource> resources, int index) throws IOException {
    Path target = directory.resolve(index + ".jar");
    var classes = new java.util.TreeMap<String, io.github.classgraph.Resource>();
    for (var resource : resources) if (resource.getPath().endsWith(".class")) classes.putIfAbsent(resource.getPath(), resource);
    try (var output = new JarOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(target), 65536))) {
      for (var entry : classes.entrySet()) {
        var jarEntry = new JarEntry(entry.getKey());
        jarEntry.setTime(0);
        output.putNextEntry(jarEntry);
        try (var input = entry.getValue().open()) { input.transferTo(output); }
        output.closeEntry();
      }
    }
    return target;
  }

  private static String digest(Path path) throws IOException {
    try (var input = Files.newInputStream(path)) {
      var digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[65536];
      for (int n; (n = input.read(buffer)) >= 0;) digest.update(buffer, 0, n);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
  }

  @PreDestroy public synchronized void close() {
    artifacts.clear(); manifest = null;
    if (directory == null) return;
    try (var paths = Files.walk(directory)) {
      for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
      directory = null;
    } catch (IOException e) { throw new IllegalStateException("Could not remove exported code environment", e); }
  }
}
