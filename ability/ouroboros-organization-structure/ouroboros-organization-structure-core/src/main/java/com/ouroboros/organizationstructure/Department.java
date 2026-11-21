package com.ouroboros.organizationstructure;

import java.beans.Transient;
import java.util.Objects;

import com.ouroboros.organizationstructure.enums.OrganizationNodeType;

/**
 * @author liansz
 */
public interface Department extends OrganizationNode {

  /**
   * 上级部门详情（如果上级部门为公司则返回null）
   *
   * @return
   */
  @Transient
  default Department getParentDepartmentDetail() {
    OrganizationNode parentDetail = getParentDetail();
    if (parentDetail == null || !Objects.equals(parentDetail.getType(), OrganizationNodeType.DEPARTMENT.getType())) {
      return null;
    }

    return (Department) parentDetail;
  }

  /**
   * 所属公司详情
   *
   * @return
   */
  @Transient
  Company getCompanyDetail();
}
