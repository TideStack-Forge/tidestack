package com.ouroboros.workflow.approval.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;

class EmployeeUsernameResolverTest {

  @Test
  void resolveShouldPreferUserIdentityForWorkflowUserIds() throws Exception {
    UserCenter userCenter = mock(UserCenter.class);
    when(userCenter.getUser("admin")).thenReturn(Optional.of(user("admin")));
    when(userCenter.getUsers(List.of("admin"))).thenReturn(List.of(user("admin")));
    EmployeeUsernameResolver resolver = resolver(userCenter, null);

    assertEquals("admin", resolver.resolve("admin").orElse(null));
    assertEquals("admin", resolver.resolve(List.of("admin")).get("admin"));
  }

  @Test
  void resolveShouldMapLegacyEmployeeIdsToLinkedUserIdentity() throws Exception {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    Employee employee = employee("employee-1", "reviewer-a", "张三");
    when(employeeAggregator.getById("employee-1")).thenReturn(Optional.of(employee));
    when(employeeAggregator.getByIds(List.of("employee-1"))).thenReturn(List.of(employee));
    EmployeeUsernameResolver resolver = resolver(null, employeeAggregator);

    assertEquals("reviewer-a", resolver.resolve("employee-1").orElse(null));
    assertEquals("reviewer-a", resolver.resolve(List.of("employee-1")).get("employee-1"));
  }

  @Test
  void resolveShouldLeaveLegacyEmployeeIdUnresolvedWhenNoLinkedUserExists() throws Exception {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    Employee employee = employee("employee-1", null, "张三");
    when(employeeAggregator.getById("employee-1")).thenReturn(Optional.of(employee));
    when(employeeAggregator.getByIds(List.of("employee-1"))).thenReturn(List.of(employee));
    EmployeeUsernameResolver resolver = resolver(null, employeeAggregator);

    assertEquals(Optional.empty(), resolver.resolve("employee-1"));
    assertEquals("employee-1", resolver.resolve(List.of("employee-1")).get("employee-1"));
  }

  private EmployeeUsernameResolver resolver(UserCenter userCenter, EmployeeAggregator employeeAggregator) throws Exception {
    EmployeeUsernameResolver resolver = new EmployeeUsernameResolver();
    setField(resolver, "userCenter", userCenter);
    setField(resolver, "employeeAggregator", employeeAggregator);
    return resolver;
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private User user(String userIdentity) {
    return () -> userIdentity;
  }

  private Employee employee(String id, String userId, String name) {
    Employee employee = mock(Employee.class);
    when(employee.getId()).thenReturn(id);
    when(employee.getUser()).thenReturn(userId);
    when(employee.getName()).thenReturn(name);
    return employee;
  }
}
