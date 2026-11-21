package com.ouroboros.organizationstructure.directory;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import com.ouroboros.enterprise.directory.EnterpriseDirectoryMember;
import com.ouroboros.organizationstructure.entity.*;
import com.ouroboros.organizationstructure.service.*;

class DirectoryOrganizationTest {
  private final OrganizationFacadeService facade = mock(OrganizationFacadeService.class);
  private final StructureManageService nodes = mock(StructureManageService.class);
  private final EmployeeManageService employees = mock(EmployeeManageService.class);
  private final PositionManageService positions = mock(PositionManageService.class);
  private final AppointmentManageService appointments = mock(AppointmentManageService.class);
  private final DirectoryOrganization organization = new DirectoryOrganization(facade, nodes, employees, positions, appointments);

  @Test void directoryProfileWhitelistDoesNotSetGenderCredentialsOrImplicitDepartment() {
    var scope = new DirectorySyncScope(); scope.setSyncMobile(false); scope.setSyncEmail(true);
    var member = new EnterpriseDirectoryMember("m", "姓名", "mobile", "mail", List.of("d"), "d", true);
    organization.createMember(member, scope);
    var argument = org.mockito.ArgumentCaptor.forClass(EmployeeEntity.class);
    verify(facade).createEmployee(argument.capture(), isNull());
    assertThat(argument.getValue().getName()).isEqualTo("姓名"); assertThat(argument.getValue().getMobile()).isNull();
    assertThat(argument.getValue().getEmail()).isEqualTo("mail"); assertThat(argument.getValue().getGender()).isNull();
  }

  @Test void neverChoosesAnImplicitPrimaryDepartmentOrReplacesALocalPrimary() {
    var unknown = new EnterpriseDirectoryMember("m", "name", null, null, List.of("d"), null, true);
    assertThatThrownBy(() -> organization.appointments("e", unknown, Map.of("d", "local-d"), List.of()))
        .hasMessageContaining("未明确主部门");
    var known = new EnterpriseDirectoryMember("m", "name", null, null, List.of("d"), "d", true);
    var local = new AppointmentEntity(); local.setId("local-job"); local.setType("PRIMARY");
    when(appointments.findCurrentAppointments("e")).thenReturn(List.of(local));
    assertThatThrownBy(() -> organization.appointments("e", known, Map.of("d", "local-d"), List.of()))
        .hasMessageContaining("本地主岗"); verify(facade, never()).createAppointment(any());
  }

  @Test void changedSourceAppointmentIsEndedAndItsHistoryIsRetained() {
    var old = new AppointmentEntity(); old.setId("old"); old.setEmployee("e"); old.setPosition("old-position"); old.setType("PRIMARY");
    var oldPosition = new PositionEntity(); oldPosition.setId("old-position"); oldPosition.setParent("old-department");
    var nextPosition = new PositionEntity(); nextPosition.setId("next-position"); nextPosition.setParent("new-department");
    when(appointments.findById("old")).thenReturn(Optional.of(old)); when(appointments.findCurrentAppointments("e")).thenReturn(List.of(old));
    when(positions.findById("old-position")).thenReturn(Optional.of(oldPosition));
    when(positions.getOrCreateDefaultPosition("new-department")).thenReturn(nextPosition);
    when(appointments.endAppointment("old")).thenReturn(true); when(facade.createAppointment(any())).thenReturn("new");
    var member = new EnterpriseDirectoryMember("m", "name", null, null, List.of("dep"), "dep", true);
    assertThat(organization.appointments("e", member, Map.of("dep", "new-department"), List.of("old"))).containsExactly("old", "new");
    verify(appointments).endAppointment("old"); verify(facade, never()).removeAppointment(any());
  }

  @Test void failedAppointmentTerminationBlocksTheCommand() {
    var owned = new AppointmentEntity(); owned.setId("owned"); when(appointments.findById("owned")).thenReturn(Optional.of(owned));
    when(appointments.endAppointment("owned")).thenReturn(false);
    assertThatThrownBy(() -> organization.endAppointments(List.of("owned"))).hasMessageContaining("保留关联等待重试");
  }
}
