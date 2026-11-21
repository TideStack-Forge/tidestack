package com.ouroboros.organizationstructure.service;

import com.ouroboros.organizationstructure.entity.*;

/** Installed organization facets protect the fields and appointments that they own. */
public interface OrganizationMutationPolicy {
  default void node(String id, OrganizationNodeEntity patch, boolean removing) { }
  default void employee(String id, EmployeeEntity patch, boolean removing) { }
  default void appointment(AppointmentEntity appointment, boolean removing) { }
}
