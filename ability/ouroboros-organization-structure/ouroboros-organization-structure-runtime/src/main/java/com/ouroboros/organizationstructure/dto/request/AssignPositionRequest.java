package com.ouroboros.organizationstructure.dto.request;

import com.ouroboros.organizationstructure.AppointmentType;

/**
 * 分配岗位请求
 */
public class AssignPositionRequest {
    private String positionId;
    private AppointmentType appointmentType;

    public String getPositionId() {
        return positionId;
    }

    public void setPositionId(String positionId) {
        this.positionId = positionId;
    }

    public AppointmentType getAppointmentType() {
        return appointmentType;
    }

    public void setAppointmentType(AppointmentType appointmentType) {
        this.appointmentType = appointmentType;
    }
}
