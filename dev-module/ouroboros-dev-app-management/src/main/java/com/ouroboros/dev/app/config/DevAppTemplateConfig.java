package com.ouroboros.dev.app.config;

import java.nio.file.Paths;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.ouroboros.dev.DevelopWorkspaceContext;

@ConfigurationProperties(prefix="ouroboros-dev.template")
public class DevAppTemplateConfig {
  private String dir;
  @Autowired
  private DevelopWorkspaceContext developWorkspaceContext;

  public String getDir() {
    return Optional.ofNullable(dir)
        .filter(s -> !s.isEmpty())
        .orElseGet(() -> Paths.get(developWorkspaceContext.getRootDir(), "templates").toString());
  }

  public DevAppTemplateConfig setDir(String dir) {
    this.dir = dir;
    return this;
  }
}
