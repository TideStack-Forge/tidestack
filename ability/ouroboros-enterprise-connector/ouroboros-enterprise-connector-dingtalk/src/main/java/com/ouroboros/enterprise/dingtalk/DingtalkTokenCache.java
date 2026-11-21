package com.ouroboros.enterprise.dingtalk;

import com.ouroboros.enterprise.token.BoundedTokenCache;

/**
 * Dingtalk token 缓存：有界 + 惰性淘汰（见 {@link BoundedTokenCache}）。
 * 由同一个 provider bean 创建的所有客户端共享，不跨 provider 共享。
 */
public final class DingtalkTokenCache {

  private final BoundedTokenCache<BoundedTokenCache.CachedToken> delegate =
      BoundedTokenCache.forTokens();

  public BoundedTokenCache.CachedToken get(String key) {
    return delegate.get(key);
  }

  public Object lockFor(String key) {
    return delegate.lockFor(key);
  }

  public void put(String key, BoundedTokenCache.CachedToken token) {
    delegate.put(key, token);
  }

  BoundedTokenCache.CachedToken getOrLoad(String key, com.ouroboros.enterprise.EnterpriseDeadline deadline,
      java.util.concurrent.Callable<BoundedTokenCache.CachedToken> loader) throws Exception {
    return delegate.getOrLoad(key, deadline, loader);
  }

  boolean invalidate(String key, String rejectedToken) {
    return delegate.removeIf(key, token -> java.util.Objects.equals(token.token(), rejectedToken));
  }
}
