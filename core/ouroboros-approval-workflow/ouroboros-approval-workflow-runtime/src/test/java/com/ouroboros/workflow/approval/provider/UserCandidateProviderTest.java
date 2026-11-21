package com.ouroboros.workflow.approval.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.workflow.approval.CandidateProvider.CandidateItem;

class UserCandidateProviderTest {

  @Test
  void valueSearchShouldReturnEmptyUsersForNullOrEmptyValues() throws Exception {
    UserCandidateProvider provider = providerWithEmployees(employee("employee-a", "user-a", "张三"));

    assertEquals(List.of(), provider.search((List<String>) null));
    assertEquals(List.of(), provider.search(List.of()));
  }

  @Test
  void valueSearchShouldReturnOnlyMatchingVisibleEmployees() throws Exception {
    UserCandidateProvider provider = providerWithEmployees(
        employee("employee-a", "user-a", "张三"),
        employee("employee-b", "user-b", "李四"));

    List<CandidateItem> result = provider.search(List.of("user-a"));

    assertEquals(List.of("user-a"), values(result));
    assertEquals(List.of("张三"), labels(result));
  }

  @Test
  void valueSearchShouldReturnUnknownPlaceholderForMissingValues() throws Exception {
    UserCandidateProvider provider = providerWithEmployees(employee("employee-a", "user-a", "张三"));

    List<CandidateItem> result = provider.search(List.of("unknown"));

    assertEquals(List.of("unknown"), values(result));
    assertEquals(List.of("未知用户（unknown）"), labels(result));
  }

  @Test
  void valueSearchShouldReturnKnownEmployeesAndUnknownPlaceholdersTogether() throws Exception {
    UserCandidateProvider provider = providerWithEmployees(
        employee("employee-a", "user-a", "张三"),
        employee("employee-b", "user-b", "李四"));

    List<CandidateItem> result = provider.search(List.of("user-a", "unknown"));

    assertEquals(List.of("user-a", "unknown"), values(result));
    assertEquals(List.of("张三", "未知用户（unknown）"), labels(result));
  }

  @Test
  void categoryKeywordSearchShouldKeepExistingBehavior() throws Exception {
    UserCandidateProvider provider = providerWithEmployees(
        employee("employee-a", "user-a", "张三", "dept-a"),
        employee("employee-b", "user-b", "李四", "dept-b"),
        employee("employee-c", "user-c", "张五", "dept-a"));

    List<CandidateItem> result = provider.search("dept-a", "张");

    assertEquals(List.of("user-a", "user-c"), values(result));
    assertEquals(List.of("张三", "张五"), labels(result));
  }

  @Test
  void categoryKeywordSearchShouldMatchLinkedUserIdentity() throws Exception {
    UserCandidateProvider provider = providerWithEmployees(
        employee("employee-a", "user-a", "张三", "dept-a"),
        employee("employee-b", "reviewer-b", "李四", "dept-a"));

    List<CandidateItem> result = provider.search("dept-a", "reviewer");

    assertEquals(List.of("reviewer-b"), values(result));
    assertEquals(List.of("李四"), labels(result));
  }

  @Test
  void getItemsShouldSkipEmployeesWithoutLinkedUser() throws Exception {
    UserCandidateProvider provider = providerWithEmployees(
        employee("employee-a", "user-a", "张三"),
        employee("employee-b", null, "李四"));

    List<CandidateItem> result = provider.getItems();

    assertEquals(List.of("user-a"), values(result));
    assertEquals(List.of("张三"), labels(result));
  }

  private UserCandidateProvider providerWithEmployees(Employee... employees) throws Exception {
    UserCandidateProvider provider = new UserCandidateProvider();
    setField(provider, "employeeAggregator", new StaticEmployeeAggregator(List.of(employees)));
    return provider;
  }

  private Employee employee(String id, String userId, String name) {
    return employee(id, userId, name, null);
  }

  private Employee employee(String id, String userId, String name, String department) {
    return new TestEmployee(id, userId, name, department);
  }

  private List<Object> values(List<CandidateItem> items) {
    return items.stream().map(CandidateItem::getValue).toList();
  }

  private List<String> labels(List<CandidateItem> items) {
    return items.stream().map(CandidateItem::getLabel).toList();
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private record TestEmployee(String id, String userId, String name, String department) implements Employee {

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getUser() {
      return userId;
    }

    @Override
    public String getGender() {
      return null;
    }

    @Override
    public String getMobile() {
      return null;
    }

    @Override
    public String getEmail() {
      return null;
    }

    @Override
    public String getBirthday() {
      return null;
    }

    @Override
    public String getDepartment() {
      return department;
    }

    @Override
    public Company getCompanyDetail() {
      return null;
    }
  }

  private record StaticEmployeeAggregator(List<Employee> employees) implements EmployeeAggregator {

    @Override
    public Optional<Employee> getById(String id) {
      return employees.stream().filter(employee -> employee.getId().equals(id)).findFirst();
    }

    @Override
    public Optional<Employee> getByUser(String userId) {
      return Optional.empty();
    }

    @Override
    public List<Employee> getByUsers(Collection<String> userIds) {
      return List.of();
    }

    @Override
    public List<Employee> getByIds(Collection<String> ids) {
      return employees.stream().filter(employee -> ids.contains(employee.getId())).toList();
    }

    @Override
    public List<Employee> getAll() {
      return employees;
    }

    @Override
    public List<Employee> getByPosition(String positionId) {
      return List.of();
    }

    @Override
    public List<Employee> getByPositions(Collection<String> positionIds) {
      return List.of();
    }

    @Override
    public List<Employee> getByDepartment(String departmentId) {
      return employees.stream().filter(employee -> departmentId.equals(employee.getDepartment())).toList();
    }

    @Override
    public List<Employee> getByDepartments(Collection<String> departmentIds) {
      return employees.stream().filter(employee -> departmentIds.contains(employee.getDepartment())).toList();
    }

    @Override
    public List<Employee> getByCompany(String companyId) {
      return List.of();
    }

    @Override
    public List<Employee> getByCompanies(Collection<String> companyIds) {
      return List.of();
    }
  }
}
