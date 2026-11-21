package com.ouroboros.menu;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author liansz
 */
public class BaseMenuModel implements MenuModel, Cloneable {

  /**
   * url（路由路径）
   */
  private String url;

  /**
   * 路径（显示路径）
   */
  private String fullName;

  /**
   * 图标
   */
  private String icon;

  /**
   * 标题
   */
  private String title;

  /**
   * 显示模式
   */
  private MenuModelShowModeEnum showMode;

  /**
   * 顺序
   */
  private Integer order = 0;

  /**
   * 菜单区域
   */
  @JsonProperty("placement")
  @JsonAlias("scope")
  private MenuScope placement = MenuScope.MAIN;

  @JsonIgnore
  private Map<String, Object> extraProps = new LinkedHashMap<String, Object>();

  @Override
  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  @Override
  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  @Override
  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  @Override
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public MenuModelShowModeEnum getShowMode() {
    return showMode;
  }

  public void setShowMode(MenuModelShowModeEnum showMode) {
    this.showMode = showMode;
  }

  @Override
  public Integer getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  @JsonIgnore
  @Override
  public MenuScope getScope() {
    return getPlacement();
  }

  public MenuScope getPlacement() {
    return placement == null ? MenuScope.MAIN : placement;
  }

  public void setPlacement(MenuScope placement) {
    this.placement = placement;
  }

  @JsonAnyGetter
  @Override
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  public void setExtraProps(Map<String, Object> extraProps) {
    this.extraProps = extraProps == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(extraProps);
  }

  @JsonAnySetter
  public void setExtraProp(String key, Object value) {
    extraProps.put(key, value);
  }

  @Override
  public BaseMenuModel clone() {
    try {
      var cloned = (BaseMenuModel) super.clone();
      cloned.extraProps = new LinkedHashMap<String, Object>(extraProps);
      return cloned;
    } catch (CloneNotSupportedException e) {
      throw new IllegalStateException("菜单模型克隆失败", e);
    }
  }
}
