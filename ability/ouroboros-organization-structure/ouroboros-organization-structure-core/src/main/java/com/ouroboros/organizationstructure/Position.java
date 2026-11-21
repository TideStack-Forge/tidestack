package com.ouroboros.organizationstructure;

import java.beans.Transient;

/**
 * 岗位接口 - 既可作为组织节点，也可作为员工-部门关系实体
 *
 * @author songmingxu
 */
public interface Position extends OrganizationNode {

  /**
   * 岗位名称
   *
   * @return 岗位名称
   */
  String getPositionName();

  default String getPositionKey() {
    return null;
  }

  /**
   * 岗位级别
   *
   * @return 岗位级别
   */
  String getLevel();

  /**
   * 岗位描述
   *
   * @return 岗位描述
   */
  String getDescription();

  /**
   * 编制人数
   *
   * @return 编制人数
   */
  Integer getHeadcount();

  /**
   * 实际人数
   *
   * @return 实际人数
   */
  @Transient
  Integer getActualCount();

  /**
   * 实现OrganizationNode的getName方法
   */
  @Override
  default String getName() {
    return getPositionName();
  }

  /**
   * 岗位类型固定为 "position"
   */
  @Override
  default String getType() {
    return "position";
  }

  /**
   * 岗位是职能性节点
   */
  @Override
  default NodeCategory getCategory() {
    return NodeCategory.FUNCTIONAL;
  }
}
