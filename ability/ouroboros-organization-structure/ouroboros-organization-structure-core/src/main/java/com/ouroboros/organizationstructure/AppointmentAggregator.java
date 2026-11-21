package com.ouroboros.organizationstructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 任职聚合器 - 聚合多个Provider，提供便捷查询
 *
 * @author songmingxu
 */
public interface AppointmentAggregator {

  /**
   * 根据ID获取任职
   */
  Optional<Appointment> getById(String id);

  /**
   * 根据ID列表获取任职
   */
  List<Appointment> getByIds(Collection<String> ids);

  /**
   * 根据员工ID获取所有任职
   */
  List<Appointment> getByEmployee(String employeeId);

  /**
   * 根据员工ID获取当前有效的任职
   */
  List<Appointment> getCurrentByEmployee(String employeeId);

  /**
   * 根据员工ID获取主岗任职
   */
  Optional<Appointment> getPrimaryByEmployee(String employeeId);

  /**
   * 根据岗位ID获取任职列表
   */
  List<Appointment> getByPosition(String positionId);

  /**
   * 根据岗位ID获取当前有效的任职
   */
  List<Appointment> getCurrentByPosition(String positionId);

  /**
   * 获取所有任职
   */
  List<Appointment> getAll();
}
