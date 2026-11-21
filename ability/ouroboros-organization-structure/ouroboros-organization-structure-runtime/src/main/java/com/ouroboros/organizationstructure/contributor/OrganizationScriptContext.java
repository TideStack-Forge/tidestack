package com.ouroboros.organizationstructure.contributor;

import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;

public class OrganizationScriptContext {

  public Employee getEmployee() {
    return OrganizationContextHolder.getContext().flatMap(com.ouroboros.organizationstructure.context.OrganizationContext::getEmployee).orElse(null);
  }

  public String getEmployeeId() {
    return OrganizationContextHolder.getContext()
        .flatMap(com.ouroboros.organizationstructure.context.OrganizationContext::getEmployee)
        .map(Employee::getId)
        .orElse(null);
  }

  public Department getDepartment() {
    return OrganizationContextHolder.getContext().flatMap(com.ouroboros.organizationstructure.context.OrganizationContext::getDepartment).orElse(null);
  }

  public String getDepartmentId() {
    return OrganizationContextHolder.getContext()
        .flatMap(com.ouroboros.organizationstructure.context.OrganizationContext::getDepartment)
        .map(Department::getId)
        .orElse(null);
  }

  public Company getCompany() {
    return OrganizationContextHolder.getContext().flatMap(com.ouroboros.organizationstructure.context.OrganizationContext::getCompany).orElse(null);
  }

  public String getCompanyId() {
    return OrganizationContextHolder.getContext()
        .flatMap(com.ouroboros.organizationstructure.context.OrganizationContext::getCompany)
        .map(Company::getId)
        .orElse(null);
  }
}
