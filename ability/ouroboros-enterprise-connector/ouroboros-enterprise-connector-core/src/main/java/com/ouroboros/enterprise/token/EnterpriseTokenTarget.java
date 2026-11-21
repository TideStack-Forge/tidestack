package com.ouroboros.enterprise.token;

import java.net.URI;

/** Provider-owned HTTP destination and placement for an application token. */
public record EnterpriseTokenTarget(String tokenKind, String host, String pathPrefix,
    String parameterIn, String parameterName, String valuePrefix) {
  public boolean accepts(URI target) {
    String path = target.getRawPath();
    return "https".equalsIgnoreCase(target.getScheme()) && host.equalsIgnoreCase(target.getHost())
        && (target.getPort() == -1 || target.getPort() == 443)
        && target.getRawUserInfo() == null && target.getRawFragment() == null
        && path != null && path.startsWith(pathPrefix)
        && !path.contains("\\") && !path.matches("(?i).*%(2e|2f|5c).*" )
        && target.normalize().equals(target);
  }
}
