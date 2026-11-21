package com.ouroboros.dev.componentlib.config;

import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.DevelopWorkspaceContext;

@Component
public class PlatformComponentLibConfig {
  private final DevelopWorkspaceContext developWorkspaceContext;
  @Value("${ouroboros.microWeb.deployPath:${ouroboros.web-root:}}")
  private String deployRoot;

  public PlatformComponentLibConfig(DevelopWorkspaceContext developWorkspaceContext) {
    this.developWorkspaceContext = developWorkspaceContext;
  }

  public String getComponentLibRoot() {
    return Paths.get(developWorkspaceContext.getRootDir(), "component-lib").toString();
  }

  public String getComponentLibDeployRoot() {
    return deployRoot;
  }
}
