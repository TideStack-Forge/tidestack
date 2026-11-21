package com.ouroboros.organizationstructure.dto.request;

import com.ouroboros.organizationstructure.AppointmentType;
import com.ouroboros.organizationstructure.entity.AppointmentEntity;
import java.time.LocalDate;

public class CreateAppointmentRequest {
  private String employeeId;
  private String positionId;
  private AppointmentType type;
  private LocalDate startDate;
  
  private LocalDate endDate;

  public AppointmentEntity toEntity() {
    AppointmentEntity entity = new AppointmentEntity();
    entity.setEmployee(employeeId);
    entity.setPosition(positionId);
    entity.setType(type != null ? type.name() : null);
    entity.setStartDate(startDate);
    entity.setEndDate(endDate);
    return entity;
  }

  public String getEmployeeId() { return employeeId; }
  public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
  public String getPositionId() { return positionId; }
  public void setPositionId(String positionId) { this.positionId = positionId; }
  public AppointmentType getType() { return type; }
  public void setType(AppointmentType type) { this.type = type; }
  public LocalDate getStartDate() { return startDate; }
  public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
  public LocalDate getEndDate() { return endDate; }
  public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
