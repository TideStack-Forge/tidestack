package com.ouroboros.organizationstructure.service.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.organizationstructure.Appointment;
import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.Position;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.organizationstructure.model.EmployeeModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeServiceImplCurrentAppointmentFilteringTest {

  @Test
  void getByPositionShouldIgnoreEmployeesWhoseMatchingAppointmentIsNotCurrent() {
    EmployeeEntity currentEntity = mock(EmployeeEntity.class);
    EmployeeEntity endedEntity = mock(EmployeeEntity.class);
    Appointment currentAppointment = singlePositionAppointment("position-1");
    Appointment endedAppointment = singlePositionAppointment("position-1");
    EmployeeModel currentEmployee = employeeWithAppointments(Collections.singletonList(currentAppointment), currentAppointment);
    EmployeeModel endedEmployee = employeeWithAppointments(Collections.emptyList(), endedAppointment);

    EmployeeServiceImpl service = newService(
        Arrays.asList(currentEntity, endedEntity),
        Arrays.asList(currentEmployee, endedEmployee));

    List<? extends Employee> employees = service.getByPosition("position-1");

    assertEquals(Collections.singletonList(currentEmployee), employees);
  }

  @Test
  void getByDepartmentShouldIgnoreEmployeesWhoseMatchingAppointmentIsNotCurrent() {
    EmployeeEntity currentEntity = mock(EmployeeEntity.class);
    EmployeeEntity endedEntity = mock(EmployeeEntity.class);
    Department department = mock(Department.class);
    when(department.getId()).thenReturn("department-1");
    Appointment currentAppointment = positionDetailAppointment(department);
    Appointment endedAppointment = positionDetailAppointment(department);
    EmployeeModel currentEmployee = employeeWithAppointments(Collections.singletonList(currentAppointment), currentAppointment);
    EmployeeModel endedEmployee = employeeWithAppointments(Collections.emptyList(), endedAppointment);

    EmployeeServiceImpl service = newService(
        Arrays.asList(currentEntity, endedEntity),
        Arrays.asList(currentEmployee, endedEmployee));

    List<? extends Employee> employees = service.getByDepartment("department-1");

    assertEquals(Collections.singletonList(currentEmployee), employees);
  }

  @Test
  void getByCompanyShouldIgnoreEmployeesWhoseMatchingAppointmentIsNotCurrent() {
    EmployeeEntity currentEntity = mock(EmployeeEntity.class);
    EmployeeEntity endedEntity = mock(EmployeeEntity.class);
    Company company = mock(Company.class);
    when(company.getId()).thenReturn("company-1");
    Appointment currentAppointment = positionDetailAppointment(company);
    Appointment endedAppointment = positionDetailAppointment(company);
    EmployeeModel currentEmployee = employeeWithAppointments(Collections.singletonList(currentAppointment), currentAppointment);
    EmployeeModel endedEmployee = employeeWithAppointments(Collections.emptyList(), endedAppointment);

    EmployeeServiceImpl service = newService(
        Arrays.asList(currentEntity, endedEntity),
        Arrays.asList(currentEmployee, endedEmployee));

    List<? extends Employee> employees = service.getByCompany("company-1");

    assertEquals(Collections.singletonList(currentEmployee), employees);
  }

  private EmployeeServiceImpl newService(List<EmployeeEntity> entities, List<EmployeeModel> employees) {
    EmployeeServiceImpl service = new EmployeeServiceImpl();
    EmployeeServiceImpl self = mock(EmployeeServiceImpl.class);
    OrganizationModelResolver modelResolver = mock(OrganizationModelResolver.class);
    ReflectionTestUtils.setField(service, "self", self);
    ReflectionTestUtils.setField(service, "modelResolver", modelResolver);

    when(self.findAll()).thenReturn(entities);
    for (int i = 0; i < entities.size(); i++) {
      when(modelResolver.toEmployeeModel(entities.get(i))).thenReturn(employees.get(i));
    }
    return service;
  }

  private EmployeeModel employeeWithAppointments(List<Appointment> currentAppointments, Appointment... allAppointments) {
    EmployeeModel employee = mock(EmployeeModel.class);
    when(employee.getAppointments()).thenReturn(Arrays.asList(allAppointments));
    when(employee.getCurrentAppointments()).thenReturn(currentAppointments);
    return employee;
  }

  private Appointment singlePositionAppointment(String positionId) {
    Appointment appointment = mock(Appointment.class);
    when(appointment.getPosition()).thenReturn(positionId);
    return appointment;
  }

  private Appointment positionDetailAppointment(OrganizationNode parent) {
    Position position = mock(Position.class);
    when(position.getParentDetail()).thenReturn(parent);
    Appointment appointment = mock(Appointment.class);
    when(appointment.getPositionDetail()).thenReturn(position);
    return appointment;
  }
}
