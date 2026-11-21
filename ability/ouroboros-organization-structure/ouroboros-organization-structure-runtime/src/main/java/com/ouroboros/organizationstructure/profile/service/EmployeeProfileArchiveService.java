package com.ouroboros.organizationstructure.profile.service;

import java.util.Map;

import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.organizationstructure.profile.dto.EmployeeProfileArchiveDTO;
import com.ouroboros.organizationstructure.profile.entity.EmployeeProfileValueEntity;

/**
 * 员工档案核心字段与扩展字段拆分服务
 */
public interface EmployeeProfileArchiveService {

  EmployeeProfileArchiveDTO split(Map<String, Object> archiveValues);

  EmployeeEntity toEmployeeEntity(Map<String, Object> archiveValues);

  EmployeeProfileValueEntity toProfileValueEntity(String employeeId, Map<String, Object> archiveValues);

  Map<String, Object> merge(EmployeeEntity employee, EmployeeProfileValueEntity profileValue);
}
