package com.ouroboros.organizationstructure.claims;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Priority;

import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.Position;
import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.ClaimsBuilder;
import com.ouroboros.security.claims.ClaimsContext;
import com.ouroboros.security.claims.ClaimsProvider;
import com.ouroboros.security.claims.StandardClaims;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserSubjectCenter;

@Priority(200)
public class EmployeeClaimsProvider implements ClaimsProvider {

  private final EmployeeAggregator employeeAggregator;
  private final UserSubjectCenter userSubjectCenter;

  public EmployeeClaimsProvider(EmployeeAggregator employeeAggregator) {
    this.employeeAggregator = employeeAggregator;
    this.userSubjectCenter = UserSubjectCenter.instance();
  }

  @Override
  public Claims provide(User user, ClaimsContext context) {
    Optional<com.ouroboros.security.rbac.UserSubject> userSubject = userSubjectCenter.getByUser(user.getUserIdentity(), "employee");
    if (!userSubject.isPresent()) {
      return Claims.empty();
    }

    Employee employee = employeeAggregator.getById(userSubject.get().getSubjectId()).orElse(null);
    if (employee == null) {
      return Claims.empty();
    }

    ClaimsBuilder builder = Claims.builder();
    builder.claim("employee", employee);

    Department dept = employee.getDepartmentDetail();
    if (dept != null) {
      builder.claim(StandardClaims.DEPARTMENT, dept);
      builder.claim(StandardClaims.DEPARTMENT_ID, dept.getId());
    }

    Position position = employee.getPrimaryPosition();
    if (position != null) {
      builder.claim(StandardClaims.POSITION, position);
      builder.claim(StandardClaims.POSITION_ID, position.getId());
    }

    Company company = employee.getCompanyDetail();
    if (company != null) {
      builder.claim(StandardClaims.ORGANIZATION, company);
      builder.claim(StandardClaims.ORGANIZATION_ID, company.getId());
    }

    return builder.build();
  }

  @Override
  public Set<String> getProvidedClaimTypes() {
    return new HashSet<>(Arrays.asList(
        "employee",
        StandardClaims.DEPARTMENT,
        StandardClaims.DEPARTMENT_ID,
        StandardClaims.POSITION,
        StandardClaims.POSITION_ID,
        StandardClaims.ORGANIZATION,
        StandardClaims.ORGANIZATION_ID
    ));
  }
}
