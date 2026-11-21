package com.ouroboros.vcs;

import com.ouroboros.util.SPI;

public interface VCSUserEmailResolver {
  static VCSUserEmailResolver getInstance() {
    var instance = SPI.getCachedReversedServiceStream(VCSUserEmailResolver.class)
        .findFirst()
        .orElseGet(() -> new VCSUserEmailResolver() {
          @Override
          public String resolveUserEmail(String user) {
            return "";
          }
        });
    return instance;
  }

  String resolveUserEmail(String user);
}
