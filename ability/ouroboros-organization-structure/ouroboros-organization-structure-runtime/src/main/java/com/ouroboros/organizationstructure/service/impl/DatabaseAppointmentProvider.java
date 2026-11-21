package com.ouroboros.organizationstructure.service.impl;

import com.ouroboros.organizationstructure.Appointment;
import com.ouroboros.organizationstructure.AppointmentProvider;
import com.ouroboros.organizationstructure.service.AppointmentManageService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 基于数据库的任职提供者实现
 */
@Component
public class DatabaseAppointmentProvider implements AppointmentProvider {

    private final AppointmentManageService appointmentManageService;
    private final OrganizationModelResolver modelResolver;

    public DatabaseAppointmentProvider(
            AppointmentManageService appointmentManageService,
            OrganizationModelResolver modelResolver) {
        this.appointmentManageService = appointmentManageService;
        this.modelResolver = modelResolver;
    }

    @Override
    public String getProviderName() {
        return "database";
    }

    @Override
    public Optional<Appointment> getById(String id) {
        return appointmentManageService.findById(id)
            .map(modelResolver::toAppointmentModel);
    }

    @Override
    public List<Appointment> getByEmployee(String employeeId) {
        return appointmentManageService.findByEmployee(employeeId).stream()
            .map(modelResolver::toAppointmentModel)
            .collect(Collectors.toList());
    }

    @Override
    public List<Appointment> getByPosition(String positionId) {
        return appointmentManageService.findByPosition(positionId).stream()
            .map(modelResolver::toAppointmentModel)
            .collect(Collectors.toList());
    }

    @Override
    public List<Appointment> getAll() {
        return appointmentManageService.findAll().stream()
            .map(modelResolver::toAppointmentModel)
            .collect(Collectors.toList());
    }
}
