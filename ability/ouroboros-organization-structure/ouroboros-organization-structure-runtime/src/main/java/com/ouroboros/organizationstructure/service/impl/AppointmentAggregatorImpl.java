package com.ouroboros.organizationstructure.service.impl;

import com.ouroboros.organizationstructure.Appointment;
import com.ouroboros.organizationstructure.AppointmentAggregator;
import com.ouroboros.organizationstructure.AppointmentProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 任职聚合器实现 - 聚合多个 AppointmentProvider
 */
@Component
public class AppointmentAggregatorImpl implements AppointmentAggregator {

  private final List<AppointmentProvider> providers;

  public AppointmentAggregatorImpl(List<AppointmentProvider> providers) {
    this.providers = providers;
  }

  @Override
  public Optional<Appointment> getById(String id) {
    return providers.stream()
        .map(provider -> provider.getById(id))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  @Override
  public List<Appointment> getByIds(Collection<String> ids) {
    return ids.stream()
        .map(this::getById)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  @Override
  public List<Appointment> getByEmployee(String employeeId) {
    return providers.stream()
        .flatMap(provider -> provider.getByEmployee(employeeId).stream())
        .collect(Collectors.toList());
  }

  @Override
  public List<Appointment> getCurrentByEmployee(String employeeId) {
    return getByEmployee(employeeId).stream()
        .filter(appointment -> appointment.getEndDate() == null || 
                appointment.getEndDate().isAfter(LocalDate.now()))
        .collect(Collectors.toList());
  }

  @Override
  public Optional<Appointment> getPrimaryByEmployee(String employeeId) {
    return getCurrentByEmployee(employeeId).stream()
        .filter(Appointment::isPrimary)
        .findFirst();
  }

  @Override
  public List<Appointment> getByPosition(String positionId) {
    return providers.stream()
        .flatMap(provider -> provider.getByPosition(positionId).stream())
        .collect(Collectors.toList());
  }

  @Override
  public List<Appointment> getCurrentByPosition(String positionId) {
    return getByPosition(positionId).stream()
        .filter(appointment -> appointment.getEndDate() == null || 
                appointment.getEndDate().isAfter(LocalDate.now()))
        .collect(Collectors.toList());
  }

  @Override
  public List<Appointment> getAll() {
    return providers.stream()
        .flatMap(provider -> provider.getAll().stream())
        .collect(Collectors.toList());
  }
}
