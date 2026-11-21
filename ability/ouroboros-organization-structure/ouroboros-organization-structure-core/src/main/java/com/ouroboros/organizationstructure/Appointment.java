package com.ouroboros.organizationstructure;

import java.beans.Transient;
import java.time.LocalDate;

/**
 * 任职接口 - 表示员工在某岗位的任职关系
 * 
 * @author songmingxu
 */
public interface Appointment {
  default String source() { return "EXTERNAL"; }


  /**
   * 任职ID
   */
  String getId();

  /**
   * 员工ID
   */
  String getEmployee();

  /**
   * 员工详情
   */
  @Transient
  Employee getEmployeeDetail();

  /**
   * 岗位ID
   */
  String getPosition();

  /**
   * 岗位详情
   */
  @Transient
  Position getPositionDetail();

  /**
   * 任职类型：PRIMARY(主岗)/SECONDARY(兼职)/ACTING(代理)
   */
  AppointmentType getType();

  /**
   * 任职开始日期
   */
  LocalDate getStartDate();

  /**
   * 任职结束日期，null表示当前在任
   */
  LocalDate getEndDate();

  /**
   * 是否当前在任
   */
  @Transient
  default boolean isCurrent() {
    LocalDate now = LocalDate.now();
    LocalDate start = getStartDate();
    LocalDate end = getEndDate();
    
    if (start != null && now.isBefore(start)) {
      return false;
    }
    if (end != null && now.isAfter(end)) {
      return false;
    }
    return true;
  }

  /**
   * 是否主岗
   */
  default boolean isPrimary() {
    return getType() == AppointmentType.PRIMARY;
  }
}
