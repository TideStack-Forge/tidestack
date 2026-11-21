package com.ouroboros.dev.app.model;

public class AppTemplateDTO {
  private String name;
  private String title;
  private String icon;
  private String description;
  private String version;
  private String author;

  public String getName() {
    return name;
  }

  public AppTemplateDTO setName(String name) {
    this.name = name;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public AppTemplateDTO setTitle(String title) {
    this.title = title;
    return this;
  }

  public String getIcon() {
    return icon;
  }

  public AppTemplateDTO setIcon(String icon) {
    this.icon = icon;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public AppTemplateDTO setDescription(String description) {
    this.description = description;
    return this;
  }

  public String getVersion() {
    return version;
  }

  public AppTemplateDTO setVersion(String version) {
    this.version = version;
    return this;
  }

  public String getAuthor() {
    return author;
  }

  public AppTemplateDTO setAuthor(String author) {
    this.author = author;
    return this;
  }
}
