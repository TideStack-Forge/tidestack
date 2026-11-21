package com.ouroboros.organizationstructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeAggregator {
  Optional<Employee> getById(String id);

  Optional<Employee> getByUser(String userId);

  List<Employee> getByUsers(Collection<String> userIds);

  List<Employee> getByIds(Collection<String> ids);

  List<Employee> getAll();

  List<Employee> getByPosition(String positionId);

  List<Employee> getByPositions(Collection<String> positionIds);

  List<Employee> getByDepartment(String departmentId);

  List<Employee> getByDepartments(Collection<String> departmentIds);

  List<Employee> getByCompany(String companyId);

  List<Employee> getByCompanies(Collection<String> companyIds);
}
