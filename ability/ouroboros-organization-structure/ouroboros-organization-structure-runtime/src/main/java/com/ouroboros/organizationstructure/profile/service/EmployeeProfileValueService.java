package com.ouroboros.organizationstructure.profile.service;

import java.util.Map;
import java.util.Optional;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.organizationstructure.profile.entity.EmployeeProfileValueEntity;

/**
 * 员工档案扩展值存取服务。
 */
public interface EmployeeProfileValueService {

  Optional<EmployeeProfileValueEntity> findByEmployeeId(String employeeId);

  Map<String, Object> getProfileValuesByEmployeeId(String employeeId);

  void saveProfileValuesByEmployeeId(String employeeId, Map<String, Object> profileValues) throws OuroborosException;

  void deleteByEmployeeId(String employeeId) throws OuroborosException;
}
