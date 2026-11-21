package com.ouroboros.organizationstructure.dto.response;

import com.ouroboros.organizationstructure.service.EmployeeAppointmentInfo;

public class EmployeeAppointmentInfoDTO {

  private AppointmentDTO appointment;
  private PositionDTO position;
  private OrganizationNodeDTO department;
  private String source;

  public static EmployeeAppointmentInfoDTO from(EmployeeAppointmentInfo info) {
    EmployeeAppointmentInfoDTO dto = new EmployeeAppointmentInfoDTO();

    if (info.getAppointment() != null) {
      dto.setAppointment(AppointmentDTO.from(info.getAppointment()));
      dto.setSource(dto.getAppointment().getSource());
    }

    if (info.getPosition() != null) {
      dto.setPosition(PositionDTO.from(info.getPosition()));
    }

    if (info.getDepartment() != null) {
      dto.setDepartment(OrganizationNodeDTO.from(info.getDepartment()));
    }

    return dto;
  }

  public AppointmentDTO getAppointment() {
    return appointment;
  }

  public void setAppointment(AppointmentDTO appointment) {
    this.appointment = appointment;
  }

  public PositionDTO getPosition() {
    return position;
  }

  public void setPosition(PositionDTO position) {
    this.position = position;
  }

  public OrganizationNodeDTO getDepartment() {
    return department;
  }

  public void setDepartment(OrganizationNodeDTO department) {
    this.department = department;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }
}
