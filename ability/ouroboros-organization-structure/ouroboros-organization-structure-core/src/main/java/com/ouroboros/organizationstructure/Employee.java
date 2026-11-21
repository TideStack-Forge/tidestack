package com.ouroboros.organizationstructure;

import java.beans.Transient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ouroboros.security.rbac.PersonSubject;
import com.ouroboros.security.rbac.User;

/**
 * @author liansz
 */
public interface Employee extends PersonSubject {
  default String source() { return "EXTERNAL"; }

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

  default String getType() {
    return "employee";
  }

  /**
   * 性别
   *
   * @return
   */
  String getGender();

  /**
   * 手机号
   *
   * @return
   */
  String getMobile();

  /**
   * 邮箱
   *
   * @return
   */
  String getEmail();

  /**
   * 生日
   *
   * @return
   */
  String getBirthday();

  /**
   * 获取关联的用户身份标识（便利方法）
   * 具体实现由实现类决定如何获取
   *
   * @return 用户身份标识，如果没有关联则返回null
   */
  @Transient
  default String getUser() {
    return null;
  }

  /**
   * 获取关联的用户详情（便利方法）
   * 具体实现由实现类决定如何获取
   *
   * @return 用户对象，如果没有关联则返回null
   */
  @Transient
  default User getUserDetail() {
    return null;
  }

  // ==================== 简化模式：直接关联部门（兼容旧数据） ====================

  /**
   * 获取部门ID（计算属性：从主岗位获取）
   *
   * @return 部门ID
   */
  @Transient
  default String getDepartment() {
    Position primary = getPrimaryPosition();
    if (primary != null) {
      return primary.getParent();
    }
    return null;
  }

  /**
   * 部门详情（计算属性）
   *
   * @return 部门详情
   */
  @Transient
  default Department getDepartmentDetail() {
    return null; // 由实现类提供
  }

  /**
   * 公司详情
   *
   * @return
   */
  @Transient
  Company getCompanyDetail();

  // ==================== 岗位模式：通过任职关联 ====================

  /**
   * 获取所有任职（代码层面关联，非数据库字段）
   *
   * @return 任职列表，默认返回空列表
   */
  @Transient
  default List<Appointment> getAppointments() {
    return Collections.emptyList();
  }

  /**
   * 获取当前有效的任职
   *
   * @return 当前有效的任职列表
   */
  @Transient
  default List<Appointment> getCurrentAppointments() {
    List<Appointment> result = new ArrayList<>();
    for (Appointment appointment : getAppointments()) {
      if (appointment.isCurrent()) {
        result.add(appointment);
      }
    }
    return result;
  }

  /**
   * 获取主岗任职
   *
   * @return 主岗任职，如果没有则返回null
   */
  @Transient
  default Appointment getPrimaryAppointment() {
    for (Appointment appointment : getCurrentAppointments()) {
      if (appointment.isPrimary()) {
        return appointment;
      }
    }
    return null;
  }

  /**
   * 获取主岗位
   *
   * @return 主岗位，如果没有则返回null
   */
  @Transient
  default Position getPrimaryPosition() {
    Appointment primary = getPrimaryAppointment();
    return primary != null ? primary.getPositionDetail() : null;
  }

  // ==================== 组织链路 ====================

  default List<OrganizationNode> getOrgChain() {
    var chain = new ArrayList<OrganizationNode>();
    var department = getDepartmentDetail();
    OrganizationNode currentNode;
    if (department != null) {
      currentNode = department;
    } else {
      currentNode = getCompanyDetail();
    }
    while (currentNode != null) {
      chain.add(currentNode);
      currentNode = currentNode.getParentDetail();
    }
    return chain;
  }
}
