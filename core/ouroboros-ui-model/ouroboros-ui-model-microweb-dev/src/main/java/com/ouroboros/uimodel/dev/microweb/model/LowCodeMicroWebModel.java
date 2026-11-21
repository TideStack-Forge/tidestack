package com.ouroboros.uimodel.dev.microweb.model;

import static com.ouroboros.uimodel.microweb.MicroWebModel.MicroWebType;

import java.util.Optional;

public class LowCodeMicroWebModel {
  String name;
  MicroWebType type = MicroWebType.APP;
  String entry;
  String publicPath;
  String description;
  String packagePath;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public MicroWebType getType() {
    return Optional.ofNullable(type).orElse(MicroWebType.APP);
  }

  public void setType(MicroWebType type) {
    this.type = type;
  }

  public String getEntry() {
    return entry;
  }

  public void setEntry(String entry) {
    this.entry = entry;
  }

  public String getPublicPath() {
    return publicPath;
  }

  public void setPublicPath(String publicPath) {
    this.publicPath = publicPath;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getPackagePath() {
    return packagePath;
  }

  public void setPackagePath(String packagePath) {
    this.packagePath = packagePath;
  }
}
