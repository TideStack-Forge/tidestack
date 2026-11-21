package com.ouroboros.organizationstructure;

import java.util.List;
import java.util.Optional;

/**
 * 任职数据提供者 - 支持多数据源（内置DB、HR系统等）
 *
 * @author songmingxu
 */
public interface AppointmentProvider {

  /**
   * 提供者名称
   */
  String getProviderName();

  /**
   * 优先级（数值越小优先级越高）
   */
  default int getPriority() {
    return 100;
  }

  /**
   * 根据ID获取任职
   */
  Optional<Appointment> getById(String id);

  /**
   * 根据员工ID获取任职列表
   */
  List<Appointment> getByEmployee(String employeeId);

  /**
   * 根据岗位ID获取任职列表
   */
  List<Appointment> getByPosition(String positionId);

  /**
   * 获取所有任职
   */
  List<Appointment> getAll();
}
