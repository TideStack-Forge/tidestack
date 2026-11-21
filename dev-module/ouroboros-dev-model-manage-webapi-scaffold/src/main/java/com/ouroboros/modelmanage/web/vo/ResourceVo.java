package com.ouroboros.modelmanage.web.vo;

import java.util.List;

/**
 * @author liansz
 */
public class ResourceVo {

  /**
   * 全名称
   */
  private String fullName;

  /**
   * 简称
   */
  private String simpleName;

  /**
   * 类型
   */
  private String type;

  /**
   * 子集
   */
  private List<ResourceVo> children;

  public ResourceVo() {
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getSimpleName() {
    return simpleName;
  }

  public void setSimpleName(String simpleName) {
    this.simpleName = simpleName;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public List<ResourceVo> getChildren() {
    return children;
  }

  public void setChildren(List<ResourceVo> children) {
    this.children = children;
  }
}
