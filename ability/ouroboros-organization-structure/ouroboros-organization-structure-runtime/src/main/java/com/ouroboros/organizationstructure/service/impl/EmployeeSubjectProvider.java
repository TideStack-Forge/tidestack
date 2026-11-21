package com.ouroboros.organizationstructure.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.SubjectProvider;

/**
 * 人员主体提供者 - 将内建Employee作为EmployeeSubject提供
 *
 * @author songmingxu
 */
@Component
public class EmployeeSubjectProvider implements SubjectProvider {

  @Autowired
  private EmployeeAggregator employeeAggregator;

  @Override
  public String getSupportedType() {
    return "employee";
  }

  @Override
  public String getProviderName() {
    return "BuiltinEmployeeSubjectProvider";
  }

  @Override
  public Optional<Subject> getById(String id) {
    return employeeAggregator.getById(id)
        .map(employee -> (Subject) employee);
  }

  @Override
  public List<Subject> getByIds(Collection<String> ids) {
    return employeeAggregator.getByIds(ids).stream()
        .collect(Collectors.toList());
  }

  @Override
  public List<Subject> getAll() {
    return employeeAggregator.getAll().stream()
        .collect(Collectors.toList());
  }
}
