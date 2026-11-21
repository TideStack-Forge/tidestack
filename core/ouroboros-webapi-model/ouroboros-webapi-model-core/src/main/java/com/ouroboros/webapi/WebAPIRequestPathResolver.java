package com.ouroboros.webapi;

import com.ouroboros.util.SPI;

/** Resolves the request path with the same rules used by the HTTP route registry. */
public interface WebAPIRequestPathResolver {
  String resolve(WebAPIModel model);

  static String requestPath(WebAPIModel model) {
    return SPI.getCachedReversedServiceStream(WebAPIRequestPathResolver.class).findFirst()
        .map(resolver -> resolver.resolve(model)).orElseGet(() -> {
          String path = model.getPath();
          String prefix = model.getScope() == null ? WebAPIScope.INTERNAL.getPrefix() : model.getScope().getPrefix();
          return path.equals(prefix) || path.startsWith(prefix + "/") ? path : prefix + path;
        });
  }
}
