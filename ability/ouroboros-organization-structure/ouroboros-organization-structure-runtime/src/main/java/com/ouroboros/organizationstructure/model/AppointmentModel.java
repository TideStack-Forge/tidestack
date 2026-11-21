package com.ouroboros.organizationstructure.model;

import java.time.LocalDate;
import java.util.function.Supplier;

import com.ouroboros.organizationstructure.Appointment;
import com.ouroboros.organizationstructure.AppointmentType;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.Position;
import com.ouroboros.organizationstructure.entity.AppointmentEntity;

/**
 * 任职业务模型 - 实现Appointment接口，封装业务行为
 *
 * @author songmingxu
 */
public class AppointmentModel implements Appointment {
  @Override
  public String source() { return "LOCAL"; }


  private final AppointmentEntity entity;
  private final Supplier<Employee> employeeSupplier;
  private final Supplier<Position> positionSupplier;
  
  private Employee employee;
  private Position position;

  public AppointmentModel(AppointmentEntity entity,
                          Supplier<Employee> employeeSupplier,
                          Supplier<Position> positionSupplier) {
    this.entity = entity;
    this.employeeSupplier = employeeSupplier;
    this.positionSupplier = positionSupplier;
  }

  @Override
  public String getId() {
    return entity.getId();
  }

  @Override
  public String getEmployee() {
    return entity.getEmployee();
  }

  @Override
  public Employee getEmployeeDetail() {
    if (employee == null && employeeSupplier != null) {
      employee = employeeSupplier.get();
    }
    return employee;
  }

  @Override
  public String getPosition() {
    return entity.getPosition();
  }

  @Override
  public Position getPositionDetail() {
    if (position == null && positionSupplier != null) {
      position = positionSupplier.get();
    }
    return position;
  }

  @Override
  public AppointmentType getType() {
    String type = entity.getType();
    return type != null ? AppointmentType.valueOf(type) : null;
  }

  @Override
  public LocalDate getStartDate() {
    return entity.getStartDate();
  }

  @Override
  public LocalDate getEndDate() {
    return entity.getEndDate();
  }
}
