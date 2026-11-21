package com.ouroboros.organizationstructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeProvider {
  Optional<? extends Employee> getById(String id);

  List<? extends Employee> getAll();

  List<? extends Employee> getByIds(Collection<String> ids);

  List<? extends Employee> getByPosition(String id);

  List<? extends Employee> getByPositions(Collection<String> ids);

  List<? extends Employee> getByDepartment(String id);

  List<? extends Employee> getByDepartments(Collection<String> ids);

  List<? extends Employee> getByCompany(String id);

  List<? extends Employee> getByCompanies(Collection<String> ids);
}
