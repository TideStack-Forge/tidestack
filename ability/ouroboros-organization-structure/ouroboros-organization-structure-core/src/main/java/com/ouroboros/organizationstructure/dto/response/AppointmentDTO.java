package com.ouroboros.organizationstructure.dto.response;

import com.ouroboros.organizationstructure.Appointment;
import com.ouroboros.organizationstructure.AppointmentType;
import java.time.LocalDate;

public class AppointmentDTO {
  private String id;
  private String employee;
  private String position;
  private String employeeName;    // 新增：员工姓名显示
  private String positionName;    // 新增：岗位名称显示
  private AppointmentType type;
  private LocalDate startDate;
  private LocalDate endDate;
  private String source;

  public static AppointmentDTO from(Appointment appointment) {
    AppointmentDTO dto = new AppointmentDTO();
    dto.setId(appointment.getId());
    dto.setEmployee(appointment.getEmployee());
    dto.setPosition(appointment.getPosition());
    dto.setType(appointment.getType());
    dto.setStartDate(appointment.getStartDate());
    dto.setEndDate(appointment.getEndDate());
    dto.setSource(appointment.source());
    // employeeName和positionName需要通过关联查询获取，暂时为空
    return dto;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getEmployee() { return employee; }
  public void setEmployee(String employee) { this.employee = employee; }
  public String getPosition() { return position; }
  public void setPosition(String position) { this.position = position; }
  public String getEmployeeName() { return employeeName; }
  public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
  public String getPositionName() { return positionName; }
  public void setPositionName(String positionName) { this.positionName = positionName; }
  public AppointmentType getType() { return type; }
  public void setType(AppointmentType type) { this.type = type; }
  public LocalDate getStartDate() { return startDate; }
  public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
  public LocalDate getEndDate() { return endDate; }
  public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
}
