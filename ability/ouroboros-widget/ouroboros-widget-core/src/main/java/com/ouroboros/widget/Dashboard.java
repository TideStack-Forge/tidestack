package com.ouroboros.widget;

import java.util.List;

public class Dashboard {
  private String id;
  private String name;
  private String scene;
  private List<WidgetInstance> widgets;

  public String getId() {
    return id;
  }

  public Dashboard setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public Dashboard setName(String name) {
    this.name = name;
    return this;
  }

  public String getScene() {
    return scene;
  }

  public Dashboard setScene(String scene) {
    this.scene = scene;
    return this;
  }

  public List<WidgetInstance> getWidgets() {
    return widgets;
  }

  public Dashboard setWidgets(List<WidgetInstance> widgets) {
    this.widgets = widgets;
    return this;
  }
}
