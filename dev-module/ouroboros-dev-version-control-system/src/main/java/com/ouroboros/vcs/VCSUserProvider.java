package com.ouroboros.vcs;

import com.ouroboros.util.SPI;

public interface VCSUserProvider {
  static VCSUserProvider getInstance() {
    var instance = SPI.getCachedReversedServiceStream(VCSUserProvider.class)
        .findFirst()
        .orElseGet(() -> new VCSUserProvider() {
          @Override
          public String getCurrentUser() {
            return "";
          }

          @Override
          public String getCurrentUserEmail() {
            return "";
          }
        });
    return instance;
  }

  String getCurrentUser();

  String getCurrentUserEmail();
}
