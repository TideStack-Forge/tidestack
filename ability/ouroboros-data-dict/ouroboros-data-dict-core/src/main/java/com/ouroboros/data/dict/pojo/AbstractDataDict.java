package com.ouroboros.data.dict.pojo;

import com.ouroboros.data.dict.DataDict;

/**
 * @author liansz
 **/
public abstract class AbstractDataDict implements DataDict {

  /**
   * 名称
   */
  private String name;

  /**
   * 标题
   */
  private String title;

  /**
   * 类型
   */
  private String type;

  /**
   * 分组
   */
  private String group;

  /**
   * 用户是否可编辑
   */
  private boolean editable;

  /**
   * 是否支持多租户
   */
  private boolean multiTenant;

  /**
   * 是否支持树形结构
   */
  private boolean treeMode;


  public AbstractDataDict() {
  }

  public AbstractDataDict(DataDict dict) {
    setName(dict.getName());
    setTitle(dict.getTitle());
    setType(dict.getType());
    setGroup(dict.getGroup());
    setEditable(dict.isEditable());
    setTreeMode(dict.isTreeMode());
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }


  @Override
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  @Override
  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  @Override
  public boolean isEditable() {
    return editable;
  }

  public void setEditable(boolean editable) {
    this.editable = editable;
  }

  @Override
  public boolean isTreeMode() {
    return treeMode;
  }

  public void setTreeMode(boolean treeMode) {
    this.treeMode = treeMode;
  }
}
