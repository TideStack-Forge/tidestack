package com.ouroboros.vcs;

import com.ouroboros.util.SPI;

public interface VCSUserNameResolver {
  String resolve(String userId);

  static VCSUserNameResolver getInstance() {
    var instance = SPI.getPrimaryService(VCSUserNameResolver.class);
    if (instance != null) return instance;
    return new VCSUserNameResolver() {
      @Override
      public String resolve(String userId) {
        return userId;
      }
    };
  }
}
