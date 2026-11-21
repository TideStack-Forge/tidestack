package com.ouroboros.dev.app.model;

public class ProfileArtifactOverlayDTO {
  private String action;
  private String targetArtifactKey;
  private AppArtifactBindingDTO replacementArtifact;
  private RepositoryRefDTO defaultRepositoryRef;

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getTargetArtifactKey() {
    return targetArtifactKey;
  }

  public void setTargetArtifactKey(String targetArtifactKey) {
    this.targetArtifactKey = targetArtifactKey;
  }

  public AppArtifactBindingDTO getReplacementArtifact() {
    return replacementArtifact;
  }

  public void setReplacementArtifact(AppArtifactBindingDTO replacementArtifact) {
    this.replacementArtifact = replacementArtifact;
  }

  public RepositoryRefDTO getDefaultRepositoryRef() {
    return defaultRepositoryRef;
  }

  public void setDefaultRepositoryRef(RepositoryRefDTO defaultRepositoryRef) {
    this.defaultRepositoryRef = defaultRepositoryRef;
  }
}
