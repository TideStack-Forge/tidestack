package com.ouroboros.organizationstructure;

import java.beans.Transient;

/**
 * @author liansz
 */
public interface OrganizationNode {

  /**
   * 主键
   *
   * @return
   */
  String getId();

  /**
   * 名称
   *
   * @return
   */
  String getName();

  /**
   * 类型
   *
   * @return
   */
  String getType();

  /**
   * 负责人
   *
   * @return
   */
  String getLeaderEmployee();

  /**
   * 负责人详情
   *
   * @return
   */
  @Transient
  Employee getLeaderEmployeeDetail();

  /**
   * 描述信息
   *
   * @return 描述信息
   */
  String getDescription();

  /**
   * 父级
   *
   * @return
   */
  String getParent();

  /**
   * 父级详情
   *
   * @return
   */
  @Transient
  OrganizationNode getParentDetail();

  /**
   * 行政级别（部门/处室/科室/股），与物理深度独立
   *
   * @return 行政级别，可为null
   */
  default String getAdminLevel() {
    return null;
  }

  /** Organization provenance; non-bean accessor keeps domain JSON unchanged. */
  default String source() {
    return "EXTERNAL";
  }

  /**
   * 节点分类：STRUCTURAL(结构性) / FUNCTIONAL(职能性)
   * 结构性节点构成组织骨架，职能性节点（如岗位）可选显示
   *
   * @return 节点分类，默认为STRUCTURAL
   */
  default NodeCategory getCategory() {
    return NodeCategory.STRUCTURAL;
  }
}
