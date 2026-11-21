package com.ouroboros.organizationstructure.service;

import com.ouroboros.organizationstructure.Appointment;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.Position;

/**
 * 员工任职信息（包含关联的岗位和部门）
 */
public class EmployeeAppointmentInfo {
  private Appointment appointment;
  private Position position;
  private OrganizationNode department;

  public EmployeeAppointmentInfo() {
  }

  public EmployeeAppointmentInfo(Appointment appointment, Position position, OrganizationNode department) {
    this.appointment = appointment;
    this.position = position;
    this.department = department;
  }

  public Appointment getAppointment() {
    return appointment;
  }

  public void setAppointment(Appointment appointment) {
    this.appointment = appointment;
  }

  public Position getPosition() {
    return position;
  }

  public void setPosition(Position position) {
    this.position = position;
  }

  public OrganizationNode getDepartment() {
    return department;
  }

  public void setDepartment(OrganizationNode department) {
    this.department = department;
  }
}
