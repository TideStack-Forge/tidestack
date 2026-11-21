package com.ouroboros.ability.auth.oidc.registration;

import java.time.Duration;
import com.ouroboros.cache.CacheManager;

/** Fixed-window request accounting through the original shared atomic cache. */
public final class DcrRateLimiter {
  private static final String NAMESPACE = "ouroboros:oauth2:dcr:rate";
  private final CacheManager cache;
  public DcrRateLimiter(CacheManager cache) { this.cache = cache; }
  public void count(String issuer, String source, DcrSettings.RateLimit policy) {
    if (!cache.capabilities().atomicUpdates()) { throw DcrFailure.unavailable(); }
    long window = policy.window().toMillis();
    long now = System.currentTimeMillis();
    long bucket = now / window;
    long remaining = window - now % window;
    String prefix = DcrCredentials.digest(issuer) + ":" + window + ":" + bucket;
    boolean global = increment(prefix + ":global", policy.global(), remaining);
    boolean perSource = increment(prefix + ":" + DcrCredentials.digest(source), policy.perSource(), remaining);
    if (!global || !perSource) { throw new DcrFailure(429, "too_many_requests", Math.max(1, (remaining + 999) / 1000)); }
  }
  private boolean increment(String key, int limit, long millis) {
    Duration ttl = Duration.ofMillis(millis);
    for (int attempt = 0; attempt < 32; attempt++) {
      var previous = cache.get(NAMESPACE, key);
      if (previous.isEmpty()) {
        if (cache.putIfAbsent(NAMESPACE, key, "1", ttl)) { return true; }
      } else {
        if (!(previous.get() instanceof String raw)) { throw DcrFailure.unavailable(); }
        int count;
        try { count = Integer.parseInt(raw); }
        catch (NumberFormatException error) { throw DcrFailure.unavailable(); }
        int next = Math.min(count + 1, 1_000_001);
        if (cache.compareAndSet(NAMESPACE, key, raw, String.valueOf(next), ttl)) { return next <= limit; }
      }
    }
    throw DcrFailure.unavailable();
  }
}
