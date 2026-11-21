package com.ouroboros.dev.app.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.ouroboros.model.annotation.ProCodeModel;
import com.ouroboros.dev.annotation.DevModel;
import com.ouroboros.menu.BaseMenuModel;

@ProCodeModel
@DevModel
public class DevMenuModel extends BaseMenuModel implements Cloneable {
  public enum MenuStage {
    WELCOME("welcome"), DEV("dev"), ALL("all");
    private String value;

    MenuStage(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @JsonCreator
    public static MenuStage fromValue(String value) {
      for (var stage : MenuStage.values()) {
        if (stage.getValue().equals(value)) {
          return stage;
        }
      }
      return DEV;
    }
  }

  private MenuStage stage;
  List<DevMenuModel> children;

  @Override
  public DevMenuModel clone() {
    return (DevMenuModel) super.clone();
  }

  public DevMenuModel toAppMenuModel(String appName, String profile) {
    var appMenuModel = this.clone();
    if (this.getUrl() != null) {
      appMenuModel.setUrl(this.getUrl().replace("{appName}", appName).replace("{profile}", profile));
    }
    return appMenuModel;
  }

  public MenuStage getStage() {
    return stage;
  }

  public List<DevMenuModel> getChildren() {
    return children;
  }

  public void setChildren(List<DevMenuModel> children) {
    this.children = children;
  }
}
