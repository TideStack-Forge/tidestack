package com.ouroboros.ability.auth.oidc;

import java.net.URI;
import java.util.Collection;
import java.util.Set;
import com.ouroboros.exception.ValidationException;

/** Exact matching except for the RFC 8252 HTTP loopback IP port exception. */
public final class OidcRedirectUris {
  private OidcRedirectUris() { }
  public static URI validate(String value) {
    try {
      URI uri = URI.create(value);
      if (uri.getHost() == null || uri.getFragment() != null || uri.getUserInfo() != null || uri.getPort() > 65535
          || value.contains("*") || !("https".equals(uri.getScheme())
              || "http".equals(uri.getScheme()) && (loopbackIp(uri) || "localhost".equals(uri.getHost())))) { throw invalid(); }
      return uri;
    } catch (IllegalArgumentException | NullPointerException error) { throw invalid(); }
  }
  public static boolean loopbackIp(URI uri) { return Set.of("127.0.0.1", "[::1]").contains(uri.getHost()); }
  public static boolean matches(Collection<String> registered, String requested) {
    if (requested == null) { return false; }
    URI target;
    try { target = validate(requested); }
    catch (ValidationException error) { return false; }
    if (registered.contains(requested)) { return true; }
    if (!"http".equals(target.getScheme()) || !loopbackIp(target)) { return false; }
    return registered.stream().anyMatch(value -> {
      URI original = validate(value);
      return "http".equals(original.getScheme()) && original.getHost().equals(target.getHost())
          && org.springframework.web.util.UriComponentsBuilder.fromUriString(value).port(target.getPort()).build().toUriString().equals(requested);
    });
  }
  private static ValidationException invalid() { return new ValidationException("[AUTH_CLIENT_INVALID] 回调地址必须是精确 HTTPS 地址（本机允许 HTTP）"); }
}
