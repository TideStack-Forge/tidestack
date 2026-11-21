package com.ouroboros.organizationstructure.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ouroboros.organizationstructure.AppointmentType;
import com.ouroboros.organizationstructure.entity.AppointmentEntity;

/**
 * 任职管理服务接口
 *
 * @author songmingxu
 */
public interface AppointmentManageService {

  /**
   * 创建任职
   *
   * @param appointment 任职信息
   * @return 创建后的任职信息
   */
  AppointmentEntity create(AppointmentEntity appointment);

  /**
   * 创建任职（便捷方法）
   *
   * @param employeeId 员工ID
   * @param positionId 岗位ID
   * @param type       任职类型
   * @param startDate  开始日期
   * @return 创建后的任职信息
   */
  AppointmentEntity create(String employeeId, String positionId, AppointmentType type, LocalDate startDate);

  /**
   * 创建任职（完整参数）
   *
   * @param employeeId 员工ID
   * @param positionId 岗位ID
   * @param type       任职类型
   * @param startDate  开始日期
   * @param endDate    结束日期（可选）
   * @return 创建后的任职信息
   */
  AppointmentEntity create(String employeeId, String positionId, AppointmentType type, LocalDate startDate, LocalDate endDate);

  /**
   * 批量创建任职
   *
   * @param appointments 任职信息列表
   * @return 创建后的任职信息列表
   */
  List<AppointmentEntity> createBatch(Collection<AppointmentEntity> appointments);

  /**
   * 移除任职
   *
   * @param id 任职ID
   * @return 是否移除成功
   */
  Boolean remove(String id);

  /**
   * 根据员工ID查找任职列表
   *
   * @param employeeId 员工ID
   * @return 任职列表
   */
  List<AppointmentEntity> findByEmployee(String employeeId);

  /**
   * 根据岗位ID查找任职列表
   *
   * @param positionId 岗位ID
   * @return 任职列表
   */
  List<AppointmentEntity> findByPosition(String positionId);

  /**
   * 查找员工的主岗任职
   *
   * @param employeeId 员工ID
   * @return 主岗任职信息
   */
  Optional<AppointmentEntity> findPrimaryAppointment(String employeeId);

  /**
   * 查找员工当前有效的所有任职
   *
   * @param employeeId 员工ID
   * @return 当前有效的任职列表
   */
  List<AppointmentEntity> findCurrentAppointments(String employeeId);

  /**
   * 结束任职（设置结束日期为当前日期）
   *
   * @param id 任职ID
   * @return 是否成功
   */
  Boolean endAppointment(String id);

  /**
   * 根据ID查询任职
   *
   * @param id 任职ID
   * @return 任职信息
   */
  Optional<AppointmentEntity> findById(String id);

  /**
   * 查询所有任职
   *
   * @return 任职列表
   */
  List<AppointmentEntity> findAll();
}
