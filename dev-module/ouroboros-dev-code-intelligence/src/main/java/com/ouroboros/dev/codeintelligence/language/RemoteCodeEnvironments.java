package com.ouroboros.dev.codeintelligence.language;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.ouroboros.devhelper.codeenvironment.CodeEnvironmentManifest;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

/** Transfers only missing bytes over the official helper command transport, verifying every artifact. */
public final class RemoteCodeEnvironments implements CodeEnvironmentResolver {
  private final DevHelperCommandService commands;
  private final Path storage;
  private final long maxBytes;
  private final long manifestTimeoutMillis;
  private final List<Path> localClasspath;
  private final Map<String, CompletableFuture<LanguageEnvironment>> environments = new ConcurrentHashMap<>();
  private Map<String, Path> localArtifacts;

  public RemoteCodeEnvironments(DevHelperCommandService commands, Path storage, long maxBytes, List<Path> localClasspath) {
    this(commands, storage, maxBytes, localClasspath, 90_000);
  }
  public RemoteCodeEnvironments(DevHelperCommandService commands, Path storage, long maxBytes, List<Path> localClasspath, long manifestTimeoutMillis) {
    if (manifestTimeoutMillis <= 0) throw new IllegalArgumentException("Invalid environment manifest timeout");
    this.commands = commands; this.storage = storage; this.maxBytes = maxBytes; this.localClasspath = List.copyOf(localClasspath);
    this.manifestTimeoutMillis = manifestTimeoutMillis;
  }

  public CompletableFuture<LanguageEnvironment> resolve(String app, Map<String, Object> identity) {
    if (!(identity.get("helperInstanceId") instanceof String)) return CompletableFuture.failedFuture(new BusinessException("缺少应用环境身份"));
    String key = app + ":" + identity.get("helperInstanceId") + ":" + identity.get("appIncarnationId") + ":" + identity.get("runtimeGeneration");
    synchronized (environments) {
      var existing = environments.get(key);
      if (existing != null && !existing.isCompletedExceptionally()) return existing;
      if (environments.size() >= 128) environments.entrySet().removeIf(e -> e.getValue().isDone());
      if (environments.size() >= 128) return CompletableFuture.failedFuture(new BusinessException("应用环境同步已达容量上限"));
      var pending = CompletableFuture.supplyAsync(() -> materialize(app, identity));
      environments.put(key, pending);
      return pending;
    }
  }

  private LanguageEnvironment materialize(String app, Map<String, Object> identity) {
    var data = command(app, identity, Map.of("action", "manifest"));
    var manifest = Json.toBean(CodeEnvironmentManifest.class, Json.toJsonString(data));
    if (manifest == null || manifest.artifacts().isEmpty() || manifest.artifacts().size() > 4096
        || manifest.javaMajor() != Runtime.version().feature()
        || !com.ouroboros.codeintelligence.runtime.ContentVersion.of(List.of(manifest.javaMajor(), manifest.artifacts())).equals(manifest.version()))
      throw new BusinessException("应用分析环境不完整或 Java 版本不匹配");
    var paths = new java.util.ArrayList<Path>();
    for (var artifact : manifest.artifacts()) {
      if (artifact.sha256() == null || !artifact.sha256().matches("[a-f0-9]{64}") || artifact.size() < 0 || artifact.size() > 268435456L)
        throw new BusinessException("应用依赖制品清单无效");
      paths.add(artifact(app, identity, manifest.version(), artifact));
    }
    return new LanguageEnvironment(manifest.version(), manifest.javaMajor(), paths);
  }

  private synchronized Path artifact(String app, Map<String, Object> identity, String version, CodeEnvironmentManifest.Artifact artifact) {
    Path target = storage.resolve(artifact.sha256() + ".jar");
    Path partial = null;
    try {
      Files.createDirectories(storage);
      if (Files.isRegularFile(target) && Files.size(target) == artifact.size() && hash(target).equals(artifact.sha256())) return target;
      if (Files.exists(target)) Files.delete(target);
      long used;
      try (var files = Files.list(storage)) { used = files.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum(); }
      if (used + artifact.size() > maxBytes) throw new BusinessException("代码分析依赖缓存已达容量上限");
      partial = Files.createTempFile(storage, "transfer-", ".part");
      Path local = localArtifacts().get(artifact.sha256());
      if (local != null) Files.copy(local, partial, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      else try (var output = Files.newOutputStream(partial)) {
        long offset = 0;
        while (offset < artifact.size()) {
          var content = command(app, identity, Map.of("action", "read", "version", version, "sha256", artifact.sha256(), "offset", offset));
          if (!(content instanceof String encoded) || encoded.length() > 174764) throw new BusinessException("应用依赖分块无效");
          byte[] bytes = Base64.getDecoder().decode(encoded);
          if (bytes.length == 0 || bytes.length > 131072 || offset + bytes.length > artifact.size()) throw new BusinessException("应用依赖分块大小无效");
          output.write(bytes); offset += bytes.length;
        }
      }
      if (Files.size(partial) != artifact.size() || !hash(partial).equals(artifact.sha256())) throw new BusinessException("应用依赖制品校验失败");
      Files.move(partial, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      return target;
    } catch (IOException e) { throw new BusinessException("无法同步应用分析依赖", e); }
    finally {
      if (partial != null) try { Files.deleteIfExists(partial); }
      catch (IOException e) { org.slf4j.LoggerFactory.getLogger(RemoteCodeEnvironments.class).warn("Could not remove incomplete code artifact", e); }
    }
  }

  private Map<String, Path> localArtifacts() throws IOException {
    if (localArtifacts == null) {
      var index = new LinkedHashMap<String, Path>();
      for (Path path : localClasspath) if (Files.isRegularFile(path)) index.putIfAbsent(hash(path), path);
      localArtifacts = Map.copyOf(index);
    }
    return localArtifacts;
  }
  private Object command(String app, Map<String, Object> identity, Map<String, Object> operation) {
    var params = new LinkedHashMap<>(identity); params.putAll(operation);
    var result = commands.exec(app, "codeEnvironment", params, "manifest".equals(operation.get("action")) ? manifestTimeoutMillis : 30_000).join();
    if (!result.getSuccess()) throw new BusinessException(result.getMessage() == null ? "应用代码环境不可用" : result.getMessage());
    return result.getData();
  }
  private static String hash(Path path) throws IOException {
    try (var input = Files.newInputStream(path)) {
      var digest = MessageDigest.getInstance("SHA-256"); byte[] bytes = new byte[65536];
      for (int n; (n = input.read(bytes)) >= 0;) digest.update(bytes, 0, n);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
  }
}
