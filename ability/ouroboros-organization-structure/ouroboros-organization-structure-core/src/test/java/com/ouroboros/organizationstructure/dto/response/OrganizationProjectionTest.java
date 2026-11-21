package com.ouroboros.organizationstructure.dto.response;

import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.Appointment;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizationProjectionTest {
  @Test
  void projectionsUseProviderProvenanceWithoutDependingOnLocalModels() throws Exception {
    var company = mock(Company.class, CALLS_REAL_METHODS);
    when(company.getId()).thenReturn("company-1");
    when(company.getName()).thenReturn("Example");
    when(company.getType()).thenReturn("company");
    assertEquals("EXTERNAL", CompanyDTO.from(company).getSource());
    when(company.source()).thenReturn("LOCAL");
    var projection = OrganizationNodeDTO.from(company);
    assertEquals("LOCAL", CompanyDTO.from(company).getSource());
    assertEquals("LOCAL", projection.getSource());
    assertEquals("company-1", projection.getId());
    var json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(projection);
    assertEquals("LOCAL", json.path("source").asText());
    assertEquals("fa fa-building", json.path("icon").asText());
    var employee = mock(Employee.class, CALLS_REAL_METHODS);
    var appointment = mock(Appointment.class, CALLS_REAL_METHODS);
    assertEquals("EXTERNAL", EmployeeDTO.from(employee).getSource());
    assertEquals("EXTERNAL", AppointmentDTO.from(appointment).getSource());
    when(employee.source()).thenReturn("LOCAL");
    when(appointment.source()).thenReturn("LOCAL");
    assertEquals("LOCAL", EmployeeDTO.from(employee).getSource());
    assertEquals("LOCAL", AppointmentDTO.from(appointment).getSource());
  }
}
