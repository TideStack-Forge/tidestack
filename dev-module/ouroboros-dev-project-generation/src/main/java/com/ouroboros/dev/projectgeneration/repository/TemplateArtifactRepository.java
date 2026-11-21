package com.ouroboros.dev.projectgeneration.repository;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateArtifactDescriptor;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogRemoteException;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteFetchPolicy;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteHttpClient;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteHttpResponse;
import com.ouroboros.dev.projectgeneration.catalog.SignedProjectTemplateCatalogVerifier;

public class TemplateArtifactRepository {

  private final Path cacheDir;
  private final ProjectTemplateRemoteFetchPolicy policy;
  private final ProjectTemplateRemoteHttpClient httpClient;
  private final long maxArtifactBytes;
  private final long maxCacheBytes;
  private final long minFreeDiskBytes;
  private final Duration connectTimeout;
  private final Duration readTimeout;
  private final Map<String, AtomicInteger> pins = new HashMap<>();

  public TemplateArtifactRepository(
      Path cacheDir,
      ProjectTemplateRemoteFetchPolicy policy,
      ProjectTemplateRemoteHttpClient httpClient,
      long maxArtifactBytes,
      long maxCacheBytes,
      long minFreeDiskBytes,
      Duration connectTimeout,
      Duration readTimeout) {
    if (cacheDir == null || policy == null || httpClient == null) {
      throw new IllegalArgumentException("Template artifact repository dependencies are required");
    }
    if (maxArtifactBytes < 1 || maxCacheBytes < 1 || minFreeDiskBytes < 0) {
      throw new IllegalArgumentException("Template artifact repository limits are invalid");
    }
    this.cacheDir = cacheDir;
    this.policy = policy;
    this.httpClient = httpClient;
    this.maxArtifactBytes = maxArtifactBytes;
    this.maxCacheBytes = maxCacheBytes;
    this.minFreeDiskBytes = minFreeDiskBytes;
    this.connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
    this.readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
  }

  public synchronized TemplateArtifact fetch(ProjectTemplateArtifactDescriptor descriptor) {
    if (descriptor.compressedSize() > maxArtifactBytes) {
      throw new ProjectTemplateCatalogRemoteException("Template artifact exceeds configured byte limit");
    }
    recoverStartup();
    Path cached = cachePath(descriptor.sha256());
    if (Files.exists(cached)) {
      verifyCached(cached, descriptor);
      touch(cached);
      return pin(descriptor.sha256(), cached, size(cached));
    }
    byte[] body = download(descriptor);
    Path stored = store(descriptor, body);
    TemplateArtifact artifact = pin(descriptor.sha256(), stored, body.length);
    try {
      evictIfNeeded();
      return artifact;
    } catch (IOException e) {
      artifact.close();
      deleteIfUnpinned(stored, descriptor.sha256());
      throw new IllegalStateException("Cannot evict template artifact cache", e);
    } catch (RuntimeException e) {
      artifact.close();
      deleteIfUnpinned(stored, descriptor.sha256());
      throw e;
    }
  }

  public synchronized void recoverStartup() {
    try {
      Files.createDirectories(cacheDir);
      try (var files = Files.list(cacheDir)) {
        files.filter(path -> path.getFileName().toString().endsWith(".part"))
            .forEach(this::deletePartFile);
      }
      evictIfNeeded();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot recover template artifact cache", e);
    }
  }

  private byte[] download(ProjectTemplateArtifactDescriptor descriptor) {
    URI uri = descriptor.uri();
    ProjectTemplateRemoteHttpResponse response = httpClient.get(
        uri,
        policy,
        Math.min(maxArtifactBytes, descriptor.compressedSize()),
        connectTimeout,
        readTimeout,
        Map.of()
    );
    if (response == null) {
      throw new ProjectTemplateCatalogRemoteException("Template artifact response is empty");
    }
    try {
      policy.validatePeerAddress(uri, response.peerAddress(), response.resolvedAddresses());
    } catch (IllegalArgumentException e) {
      throw new ProjectTemplateCatalogRemoteException("Template artifact peer address failed policy validation", e);
    }
    if (response.statusCode() >= 300 && response.statusCode() < 400) {
      throw new ProjectTemplateCatalogRemoteException("Template artifact redirects are not allowed");
    }
    if (response.statusCode() != 200) {
      throw new ProjectTemplateCatalogRemoteException("Template artifact returned HTTP " + response.statusCode());
    }
    byte[] body = response.body();
    if (body.length != descriptor.compressedSize() || body.length > maxArtifactBytes) {
      throw new ProjectTemplateCatalogRemoteException("Template artifact size mismatch");
    }
    if (!SignedProjectTemplateCatalogVerifier.sha256Hex(body).equals(descriptor.sha256())) {
      throw new ProjectTemplateCatalogRemoteException("Template artifact digest mismatch");
    }
    return body;
  }

  private Path store(ProjectTemplateArtifactDescriptor descriptor, byte[] body) {
    Path target = cachePath(descriptor.sha256());
    Path temp = cacheDir.resolve(descriptor.sha256() + "." + UUID.randomUUID() + ".part");
    try {
      Files.write(temp, body);
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return target;
    } catch (IOException atomicFailure) {
      try {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
      } catch (IOException fallbackFailure) {
        atomicFailure.addSuppressed(fallbackFailure);
        deletePartFile(temp);
        throw new IllegalStateException("Cannot store template artifact", atomicFailure);
      }
    }
  }

  private TemplateArtifact pin(String sha256, Path path, long size) {
    pins.computeIfAbsent(sha256, ignored -> new AtomicInteger()).incrementAndGet();
    return new TemplateArtifact(sha256, path, size, () -> release(sha256));
  }

  private synchronized void release(String sha256) {
    AtomicInteger count = pins.get(sha256);
    if (count == null) {
      return;
    }
    if (count.decrementAndGet() <= 0) {
      pins.remove(sha256);
    }
  }

  private void verifyCached(Path path, ProjectTemplateArtifactDescriptor descriptor) {
    try {
      byte[] bytes = Files.readAllBytes(path);
      if (bytes.length != descriptor.compressedSize()
          || !SignedProjectTemplateCatalogVerifier.sha256Hex(bytes).equals(descriptor.sha256())) {
        Files.deleteIfExists(path);
        throw new ProjectTemplateCatalogRemoteException("Cached template artifact failed digest validation");
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot verify cached template artifact", e);
    }
  }

  private void evictIfNeeded() throws IOException {
    while (cacheBytes() > maxCacheBytes || freeBytes() < minFreeDiskBytes) {
      List<Path> candidates;
      try (var files = Files.list(cacheDir)) {
        candidates = files
            .filter(path -> path.getFileName().toString().endsWith(".zip"))
            .filter(path -> !isPinned(shaFromPath(path)))
            .sorted(Comparator.comparing(this::lastModified))
            .toList();
      }
      if (candidates.isEmpty()) {
        throw new IllegalStateException("Template artifact cache has no evictable entries");
      }
      Files.deleteIfExists(candidates.getFirst());
    }
  }

  private long cacheBytes() throws IOException {
    try (var files = Files.list(cacheDir)) {
      return files
          .filter(path -> path.getFileName().toString().endsWith(".zip"))
          .mapToLong(this::size)
          .sum();
    }
  }

  private long freeBytes() throws IOException {
    FileStore fileStore = Files.getFileStore(cacheDir);
    return fileStore.getUsableSpace();
  }

  private boolean isPinned(String sha256) {
    AtomicInteger count = pins.get(sha256);
    return count != null && count.get() > 0;
  }

  private Path cachePath(String sha256) {
    return cacheDir.resolve(sha256 + ".zip");
  }

  private String shaFromPath(Path path) {
    String filename = path.getFileName().toString();
    return filename.substring(0, filename.length() - ".zip".length());
  }

  private FileTime lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read artifact cache timestamp", e);
    }
  }

  private long size(Path path) {
    try {
      return Files.size(path);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read artifact cache size", e);
    }
  }

  private void touch(Path path) {
    try {
      Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot update artifact cache timestamp", e);
    }
  }

  private void deletePartFile(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot delete artifact cache temp file", e);
    }
  }

  private void deleteIfUnpinned(Path path, String sha256) {
    if (isPinned(sha256)) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot delete artifact cache file", e);
    }
  }
}
