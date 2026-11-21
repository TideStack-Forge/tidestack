package com.ouroboros.workflow.approval.resolver;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.organizationstructure.Appointment;
import com.ouroboros.organizationstructure.AppointmentType;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.Position;
import com.ouroboros.workflow.approval.definition.candidates.PositionCandidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PositionCandidateUserResolverTest {

  @AfterEach
  void clearSharedEmployeeAggregator() throws Exception {
    Field field = PositionCandidateUserResolver.class.getDeclaredField("sharedEmployeeAggregator");
    field.setAccessible(true);
    field.set(null, null);
  }

  @Test
  void resolveShouldReturnEmptyListWhenCandidatePositionsAreNull() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    PositionCandidate candidate = mock(PositionCandidate.class);
    when(candidate.getPositions()).thenReturn(null);

    PositionCandidateUserResolver resolver = new PositionCandidateUserResolver(employeeAggregator);

    List<String> resolved = resolver.resolve(candidate);

    assertEquals(Collections.emptyList(), resolved);
    verifyNoInteractions(employeeAggregator);
  }

  @Test
  void resolveShouldMatchCatalogBackedPositionByKey() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    PositionCandidate candidate = mock(PositionCandidate.class);
    when(candidate.getPositions()).thenReturn(Collections.singletonList("member"));

    Employee employee = employee("employee-1", "user-1", position("member", "成员"));
    when(employeeAggregator.getAll()).thenReturn(Collections.singletonList(employee));

    PositionCandidateUserResolver resolver = new PositionCandidateUserResolver(employeeAggregator);

    assertEquals(Collections.singletonList("user-1"), resolver.resolve(candidate));
  }

  @Test
  void resolveShouldMatchLegacyDefaultMemberPositionByNameWhenKeyIsNull() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    PositionCandidate candidate = mock(PositionCandidate.class);
    when(candidate.getPositions()).thenReturn(Collections.singletonList("member"));

    Employee employee = employee("employee-1", "user-1", position(null, "成员"));
    when(employeeAggregator.getAll()).thenReturn(Collections.singletonList(employee));

    PositionCandidateUserResolver resolver = new PositionCandidateUserResolver(employeeAggregator);

    assertEquals(Collections.singletonList("user-1"), resolver.resolve(candidate));
  }

  @Test
  void resolveShouldNotMatchNonDefaultLegacyPositionByNameWhenKeyIsNull() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    PositionCandidate candidate = mock(PositionCandidate.class);
    when(candidate.getPositions()).thenReturn(Collections.singletonList("member"));

    Employee employee = employee("employee-1", "user-1", position(null, "工程师"));
    when(employeeAggregator.getAll()).thenReturn(Collections.singletonList(employee));

    PositionCandidateUserResolver resolver = new PositionCandidateUserResolver(employeeAggregator);

    assertEquals(Collections.emptyList(), resolver.resolve(candidate));
  }

  @Test
  void resolveShouldAllowSpiInstanceToReuseSpringInjectedAggregator() {
    PositionCandidate candidate = mock(PositionCandidate.class);
    when(candidate.getPositions()).thenReturn(Collections.singletonList("member"));

    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    Employee employee = employee("employee-1", "user-1", position("member", "成员"));
    when(employeeAggregator.getAll()).thenReturn(Collections.singletonList(employee));

    PositionCandidateUserResolver spiResolver = new PositionCandidateUserResolver();
    new PositionCandidateUserResolver(employeeAggregator);

    assertEquals(Collections.singletonList("user-1"), spiResolver.resolve(candidate));
  }

  @Test
  void resolveShouldSkipMatchedEmployeesWithoutLinkedUser() {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    PositionCandidate candidate = mock(PositionCandidate.class);
    when(candidate.getPositions()).thenReturn(Collections.singletonList("member"));

    Employee employee = employee("employee-1", null, position("member", "成员"));
    when(employeeAggregator.getAll()).thenReturn(Collections.singletonList(employee));

    PositionCandidateUserResolver resolver = new PositionCandidateUserResolver(employeeAggregator);

    assertEquals(Collections.emptyList(), resolver.resolve(candidate));
  }

  private Employee employee(String id, String userId, Position position) {
    Employee employee = mock(Employee.class);
    Appointment appointment = appointment(position);
    when(employee.getId()).thenReturn(id);
    when(employee.getUser()).thenReturn(userId);
    when(employee.getCurrentAppointments()).thenReturn(Collections.singletonList(appointment));
    return employee;
  }

  private Appointment appointment(Position position) {
    Appointment appointment = mock(Appointment.class);
    when(appointment.getPositionDetail()).thenReturn(position);
    when(appointment.getStartDate()).thenReturn(LocalDate.now());
    when(appointment.getEndDate()).thenReturn(null);
    when(appointment.getType()).thenReturn(AppointmentType.PRIMARY);
    return appointment;
  }

  private Position position(String positionKey, String positionName) {
    Position position = mock(Position.class);
    when(position.getPositionKey()).thenReturn(positionKey);
    when(position.getPositionName()).thenReturn(positionName);
    return position;
  }

}
