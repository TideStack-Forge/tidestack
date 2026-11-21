package com.ouroboros.businessmodel.meta.custom;

public class CustomConfig {
  String name;
  String title;
  CustomUiConfig ui;
  CustomApiConfig api;

  public String getName() {
    return name;
  }

  public CustomConfig setName(String name) {
    this.name = name;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public CustomConfig setTitle(String title) {
    this.title = title;
    return this;
  }

  public CustomUiConfig getUi() {
    return ui;
  }

  public CustomConfig setUi(CustomUiConfig ui) {
    this.ui = ui;
    return this;
  }

  public CustomApiConfig getApi() {
    return api;
  }

  public CustomConfig setApi(CustomApiConfig api) {
    this.api = api;
    return this;
  }
}
