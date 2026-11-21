package com.ouroboros.dev.codeintelligence.language;

import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.runtime.model.*;
import com.ouroboros.dev.runtime.service.DevRuntimeService;

/** Uses registered RuntimeBackends; neither language plugins nor the pool know container APIs. */
public final class ManagedLanguageServerDeployment implements LanguageServerDeployment {
  private final DevRuntimeService runtimes;
  private final BackendType backend;
  private final Map<String, String> images;
  private final long memoryBytes;
  private final long nanoCpus;
  public ManagedLanguageServerDeployment(DevRuntimeService runtimes, BackendType backend, Map<String, String> images, long memoryBytes, long nanoCpus) {
    this.runtimes = runtimes; this.backend = backend; this.images = Map.copyOf(images); this.memoryBytes = memoryBytes; this.nanoCpus = nanoCpus;
    if (memoryBytes <= 0 || nanoCpus <= 0) throw new IllegalArgumentException("Invalid language container limits");
  }
  public boolean available(LanguageServerProvider provider) { return images.containsKey(provider.languageId()); }
  public Started start(LanguageServerProvider provider, Path workspace, LanguageEnvironment environment, Consumer<JsonNode> notifications) throws Exception {
    String image = images.get(provider.languageId());
    if (image == null || image.isBlank()) throw new IllegalArgumentException("Language container image is not configured: " + provider.languageId());
    var spec = RuntimeSpec.builder().runtimeKind(RuntimeKind.LANGUAGE_SERVICE).backendType(backend).workspaceMode(WorkspaceMode.NONE)
        .workloadId(provider.languageId() + "-" + environment.version().substring(0, 24)).profile(UUID.randomUUID().toString().substring(0, 8))
        .image(image).exposureStrategy(ExposureStrategy.INTERNAL_ONLY).resourceLimits(Map.of("memoryBytes", String.valueOf(memoryBytes), "nanoCpus", String.valueOf(nanoCpus)))
        .env(Map.of("OUROBOROS_LANGUAGE_LEASED", "true")).ttl(java.time.Duration.ofHours(1)).build();
    var key = RuntimeIdentityKey.from(spec);
    try {
      runtimes.create(spec).get(); runtimes.start(key).get();
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
      while (true) {
        var runtime = runtimes.inspect(key).get();
        String endpoint = runtime.getAccessRef().get("language");
        if (runtime.getStatus() == RuntimeStatus.RUNNING && endpoint != null) {
          var uri = URI.create(endpoint);
          try {
            var started = new TcpLanguageServerDeployment(new InetSocketAddress(uri.getHost(), uri.getPort())).start(provider, workspace, environment, notifications);
            started.connection().onClose(() -> runtimes.delete(key).get());
            return started;
          } catch (java.net.ConnectException notReady) {
            if (System.nanoTime() >= deadline) throw notReady;
          }
        }
        if (System.nanoTime() >= deadline) throw new IllegalStateException("Language container did not become ready");
        Thread.sleep(100);
      }
    } catch (Exception error) {
      try { runtimes.delete(key).get(); } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
      throw error;
    }
  }
}
