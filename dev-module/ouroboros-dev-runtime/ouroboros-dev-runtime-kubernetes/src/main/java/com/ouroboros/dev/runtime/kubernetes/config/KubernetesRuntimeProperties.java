package com.ouroboros.dev.runtime.kubernetes.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ouroboros.dev.runtime.kubernetes")
public class KubernetesRuntimeProperties {
  private String namespace = "default";
  private String sharedPvcName = "ouroboros-dev-runtime-workspace";
  private String mountedWorkspaceRoot = "/workspace";
  private Duration helperReadyTimeout = Duration.ofSeconds(45);
  private String ingressHostSuffix = "";

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getSharedPvcName() {
    return sharedPvcName;
  }

  public void setSharedPvcName(String sharedPvcName) {
    this.sharedPvcName = sharedPvcName;
  }

  public String getMountedWorkspaceRoot() {
    return mountedWorkspaceRoot;
  }

  public void setMountedWorkspaceRoot(String mountedWorkspaceRoot) {
    this.mountedWorkspaceRoot = mountedWorkspaceRoot;
  }

  public Duration getHelperReadyTimeout() {
    return helperReadyTimeout;
  }

  public void setHelperReadyTimeout(Duration helperReadyTimeout) {
    this.helperReadyTimeout = helperReadyTimeout;
  }

  public String getIngressHostSuffix() {
    return ingressHostSuffix;
  }

  public void setIngressHostSuffix(String ingressHostSuffix) {
    this.ingressHostSuffix = ingressHostSuffix;
  }
}
