package com.ouroboros.organizationstructure.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.ouroboros.organizationstructure.*;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.security.rbac.User;

/**
 * 员工业务模型 - 实现Employee接口，封装业务行为
 *
 * @author songmingxu
 */
public class EmployeeModel implements Employee {
  @Override
  public String source() { return "LOCAL"; }


  private final EmployeeEntity entity;
  private final Supplier<User> userSupplier;
  private final AppointmentAggregator appointmentAggregator;
  private User user;
  private List<Appointment> appointments;
  private Department department;
  private Company company;

  public EmployeeModel(EmployeeEntity entity,
                       Supplier<User> userSupplier,
                       AppointmentAggregator appointmentAggregator) {
    this.entity = entity;
    this.userSupplier = userSupplier;
    this.appointmentAggregator = appointmentAggregator;
  }

  // Employee接口实现
  @Override
  public String getId() {
    return entity.getId();
  }

  @Override
  public String getName() {
    return entity.getName();
  }

  @Override
  public String getGender() {
    return entity.getGender();
  }

  @Override
  public String getMobile() {
    return entity.getMobile();
  }

  @Override
  public String getEmail() {
    return entity.getEmail();
  }

  @Override
  public String getBirthday() {
    return entity.getBirthday();
  }

  @Override
  public String getUser() {
    var user = getUserDetail();
    return user != null ? user.getUserIdentity() : null;
  }

  @Override
  public User getUserDetail() {
    if (user == null && userSupplier != null) {
      user = userSupplier.get();
    }
    return user;
  }

  @Override
  public List<Appointment> getAppointments() {
    if (appointments == null && appointmentAggregator != null) {
      appointments = appointmentAggregator.getCurrentByEmployee(getId());
    }
    return appointments != null ? appointments : Collections.emptyList();
  }

  @Override
  public Department getDepartmentDetail() {
    if (department == null) {
      var primary = getPrimaryAppointment();
      if (primary == null) {
        return null;
      }
      OrganizationNode node = primary.getPositionDetail();
      while (node != null && !(node instanceof Department)) {
        node = node.getParentDetail();
      }
      department = node != null ? (Department) node : null;
    }
    return department;
  }

  @Override
  public Company getCompanyDetail() {
    if (company == null) {
      var primary = getPrimaryAppointment();
      if (primary == null) {
        return null;
      }
      OrganizationNode node = primary.getPositionDetail();
      while (node != null && !(node instanceof Company)) {
        node = node.getParentDetail();
      }
      company = node != null ? (Company) node : null;
    }
    return company;
  }

  // 审计字段访问
  public String getCreatedBy() {
    return entity.getCreatedBy();
  }

  public LocalDateTime getCreatedAt() {
    return entity.getCreatedAt();
  }

  public String getUpdatedBy() {
    return entity.getUpdatedBy();
  }

  public LocalDateTime getUpdatedAt() {
    return entity.getUpdatedAt();
  }

  public String getDeletedBy() {
    return entity.getDeletedBy();
  }

  public LocalDateTime getDeletedAt() {
    return entity.getDeletedAt();
  }

  public boolean isDeleted() {
    return entity.isDeleted();
  }
}
