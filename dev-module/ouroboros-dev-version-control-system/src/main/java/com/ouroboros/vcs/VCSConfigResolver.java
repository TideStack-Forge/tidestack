package com.ouroboros.vcs;

public interface VCSConfigResolver {

  VCSConfig resolve(String appName);
}
