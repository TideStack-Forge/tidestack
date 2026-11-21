package com.ouroboros.dev.app.model;

public class BaseImagePullRequest {
  private String imageName;
  private String tag;

  public String getImageName() {
    return imageName;
  }

  public BaseImagePullRequest setImageName(String imageName) {
    this.imageName = imageName;
    return this;
  }

  public String getTag() {
    return tag;
  }

  public BaseImagePullRequest setTag(String tag) {
    this.tag = tag;
    return this;
  }
}
