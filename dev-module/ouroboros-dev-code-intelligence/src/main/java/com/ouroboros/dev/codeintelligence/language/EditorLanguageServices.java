package com.ouroboros.dev.codeintelligence.language;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;

/** Platform-owned, bounded pool shared by language and immutable application environment. */
public final class EditorLanguageServices implements AutoCloseable {
  public record Descriptor(String languageId, String fileExtension, boolean available) {}
  private record Key(String language, String engine, String environment) {}
  private final Map<String, LanguageServerProvider> providers;
  private final Map<Key, LanguageServerRuntime> runtimes = new LinkedHashMap<>();
  private final LanguageServerDeployment deployment;
  private final Path storage;
  private final int capacity;
  private final long idleMillis;
  private final Clock clock;
  private boolean closed;
  private final java.util.concurrent.ScheduledExecutorService maintenance = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
    var thread = new Thread(r, "platform-language-maintenance"); thread.setDaemon(true); return thread;
  });

  public EditorLanguageServices(List<LanguageServerProvider> plugins, Path storage, int capacity, Duration idle, Clock clock) {
    this(plugins, storage, capacity, idle, clock, LanguageServerDeployment.stdio());
  }
  public EditorLanguageServices(List<LanguageServerProvider> plugins, Path storage, int capacity, Duration idle, Clock clock,
      LanguageServerDeployment deployment) {
    this.deployment = deployment;
    if (capacity < 1 || idle.isNegative() || idle.isZero()) throw new IllegalArgumentException("Invalid language pool limits");
    var index = new LinkedHashMap<String, LanguageServerProvider>();
    for (var provider : plugins) {
      String language = provider.languageId();
      if (language == null || !language.matches("[a-z][a-z0-9-]{0,31}") || index.putIfAbsent(language, provider) != null
          || provider.fileExtension() == null || !provider.fileExtension().matches("\\.[a-zA-Z0-9]{1,16}"))
        throw new IllegalArgumentException("Invalid or duplicate editor language plugin: " + language);
    }
    this.providers = Map.copyOf(index); this.storage = storage; this.capacity = capacity; this.idleMillis = idle.toMillis(); this.clock = clock;
    maintenance.scheduleWithFixedDelay(this::reap, 1, 1, java.util.concurrent.TimeUnit.MINUTES);
  }
  public List<Descriptor> languages() {
    return providers.values().stream().sorted(java.util.Comparator.comparing(LanguageServerProvider::languageId))
        .map(p -> new Descriptor(p.languageId(), p.fileExtension(), deployment.available(p))).toList();
  }
  public synchronized CompletableFuture<List<JsonNode>> exchange(String language, LanguageEnvironment environment, String session, List<JsonNode> messages) {
    if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Language pool is closed"));
    var provider = providers.get(language);
    if (provider == null) throw new IllegalArgumentException("Editor language plugin is not registered: " + language);
    if (!environment.version().matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid code environment version");
    var key = new Key(language, providers.get(language).engineVersion(), environment.version());
    var runtime = runtimes.get(key);
    if (runtime == null) {
      reap();
      if (runtimes.size() >= capacity) {
        var victim = runtimes.entrySet().stream().filter(e -> e.getValue().idle())
            .min(java.util.Comparator.comparingLong(e -> e.getValue().lastActivity()));
        victim.ifPresent(e -> { e.getValue().close(); runtimes.remove(e.getKey()); });
      }
      if (runtimes.size() >= capacity) return CompletableFuture.failedFuture(new IllegalStateException("代码分析进程已达容量上限，请稍后重试"));
      runtime = new LanguageServerRuntime(provider, storage.resolve(language).resolve(environment.version()), environment, clock, deployment);
      runtimes.put(key, runtime);
    }
    return runtime.exchange(session, messages);
  }
  public synchronized CompletableFuture<Void> closeSession(String language, LanguageEnvironment environment, String session) {
    var runtime = runtimes.get(new Key(language, providers.get(language).engineVersion(), environment.version()));
    return runtime == null ? CompletableFuture.completedFuture(null) : runtime.closeSession(session);
  }
  public synchronized void reap() {
    long cutoff = clock.millis() - idleMillis;
    var iterator = runtimes.entrySet().iterator();
    while (iterator.hasNext()) {
      var runtime = iterator.next().getValue(); runtime.expireSessions(cutoff);
      if (runtime.idle() && runtime.lastActivity() < cutoff) { runtime.close(); iterator.remove(); }
    }
  }
  @PreDestroy public synchronized void close() {
    closed = true; maintenance.shutdownNow(); runtimes.values().forEach(LanguageServerRuntime::close); runtimes.clear();
  }
}
