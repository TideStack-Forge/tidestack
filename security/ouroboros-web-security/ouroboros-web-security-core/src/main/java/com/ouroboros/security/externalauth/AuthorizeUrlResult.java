package com.ouroboros.security.externalauth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Result of building an authorization URL for redirect flow.
 * Contains the URL to redirect to, the state token, and the cache entry to store.
 */
public class AuthorizeUrlResult {

  private final String authorizeUrl;
  private final String state;
  private final Map<String, Object> cacheEntry;

  private AuthorizeUrlResult(String authorizeUrl, String state, Map<String, Object> cacheEntry) {
    this.authorizeUrl = authorizeUrl;
    this.state = state;
    this.cacheEntry = cacheEntry != null ? Collections.unmodifiableMap(new HashMap<>(cacheEntry)) : Collections.emptyMap();
  }

  public static AuthorizeUrlResult of(String authorizeUrl, String state, Map<String, Object> cacheEntry) {
    return new AuthorizeUrlResult(authorizeUrl, state, cacheEntry);
  }

  public String getAuthorizeUrl() { return authorizeUrl; }
  public String getState() { return state; }
  public Map<String, Object> getCacheEntry() { return cacheEntry; }
}
