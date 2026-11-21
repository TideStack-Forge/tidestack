package com.ouroboros.dev.app.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class BaseImageReference {
  private final String repositoryWithName;
  private final String tag;
  private final String imageName;

  public BaseImageReference(String repositoryWithName, String tag, String imageName) {
    this.repositoryWithName = repositoryWithName;
    this.tag = tag;
    this.imageName = imageName;
  }

  @JsonIgnore
  public String getRepositoryWithName() {
    return repositoryWithName;
  }

  public String getTag() {
    return tag;
  }

  public String getImageName() {
    return imageName;
  }
}
