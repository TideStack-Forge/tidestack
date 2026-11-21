package com.ouroboros.organizationstructure.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.EmployeeProvider;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;

@Component
public class EmployeeAggregatorImpl implements EmployeeAggregator {
  private final List<EmployeeProvider> employeeProviders;
  private final UserCenter userCenter;

  public EmployeeAggregatorImpl(ObjectProvider<EmployeeProvider> employeeProvider) {
    employeeProviders = employeeProvider.orderedStream().collect(Collectors.toList());
    this.userCenter = UserCenter.instance();
  }

  @Override
  public Optional<Employee> getById(String id) {
    return employeeProviders.stream()
        .map(employeeProvider -> employeeProvider.getById(id))
        .filter(Optional::isPresent)
        .map(employee -> (Employee) employee.get())
        .findAny();
  }

  @Override
  public Optional<Employee> getByUser(String userId) {
    var user = userCenter.getUser(userId);
    return user
        .map(User::getSubject)
        .filter(subject -> "EMPLOYEE".equalsIgnoreCase(subject.getType()))
        .flatMap(subject -> getById(subject.getId()));
  }

  @Override
  public List<Employee> getByUsers(Collection<String> userIds) {
    var users = userCenter.getUsers(userIds);
    return users.stream()
        .map(User::getSubject)
        .filter(subject -> subject != null && "EMPLOYEE".equalsIgnoreCase(subject.getType()))
        .map(subject -> getById(subject.getId()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  @Override
  public List<Employee> getByIds(Collection<String> ids) {
    return getAll()
        .stream()
        .filter(employee -> ids.contains(employee.getId()))
        .collect(Collectors.toList());
  }

  @Override
  public List<Employee> getAll() {
    return employeeProviders.stream()
        .map(EmployeeProvider::getAll)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  @Override
  public List<Employee> getByDepartment(String departmentId) {
    return employeeProviders.stream()
        .map(employeeProvider -> employeeProvider.getByDepartment(departmentId))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  @Override
  public List<Employee> getByPosition(String positionId) {
    return employeeProviders.stream()
        .map(employeeProvider -> employeeProvider.getByPosition(positionId))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  @Override
  public List<Employee> getByPositions(Collection<String> positionIds) {
    return employeeProviders.stream()
        .map(employeeProvider -> employeeProvider.getByPositions(positionIds))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  @Override
  public List<Employee> getByDepartments(Collection<String> departmentIds) {
    return employeeProviders.stream()
        .map(employeeProvider -> employeeProvider.getByDepartments(departmentIds))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  @Override
  public List<Employee> getByCompany(String companyId) {
    return employeeProviders.stream()
        .map(employeeProvider -> employeeProvider.getByCompany(companyId))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  @Override
  public List<Employee> getByCompanies(Collection<String> companyIds) {
    return employeeProviders.stream()
        .map(employeeProvider -> employeeProvider.getByCompanies(companyIds))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }
}
