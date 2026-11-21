package com.ouroboros.organizationstructure.context;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.OrganizationNode;

/**
 * @author liansz
 */
public class LazyLoadOrganizationContext implements OrganizationContext {

  /**
   * 员工
   */
  private final String userIdentity;

  /**
   * 根据userIdentity获取员工
   */
  private final Supplier<Employee> employeeSupplier;

  /**
   * 员工
   */
  private Employee employee;

  /**
   * 员工所在部门
   */
  private Department department;

  /**
   * 员工所在公司
   */
  private Company company;

  public LazyLoadOrganizationContext(String userIdentity, Supplier<Employee> employeeSupplier) {
    this.userIdentity = userIdentity;
    this.employeeSupplier = employeeSupplier;
  }

  @Override
  public Optional<Employee> getEmployee() {
    if (employee == null) {
      employee = employeeSupplier.get();
    }

    return Optional.ofNullable(employee);
  }

  @Override
  public Optional<Department> getDepartment() {
    if (department == null) {
      getEmployee()
          .ifPresent(employee -> department = employee.getDepartmentDetail());
    }

    return Optional.ofNullable(department);
  }

  @Override
  public Optional<Company> getCompany() {
    if (company == null) {
      getEmployee()
          .ifPresent(employee -> company = employee.getCompanyDetail());
    }
    return Optional.ofNullable(company);
  }

  @Override
  public List<OrganizationNode> getOrgChain() {
    return getEmployee()
        .map(Employee::getOrgChain)
        .orElseGet(Collections::emptyList);
  }
}
