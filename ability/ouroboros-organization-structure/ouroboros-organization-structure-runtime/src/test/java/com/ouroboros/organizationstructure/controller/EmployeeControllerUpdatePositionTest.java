package com.ouroboros.organizationstructure.controller;

import org.junit.jupiter.api.Test;

import com.ouroboros.organizationstructure.dto.request.UpdateEmployeePositionRequest;
import com.ouroboros.organizationstructure.service.OrganizationFacadeService;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.service.UserService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmployeeControllerUpdatePositionTest {

  @Test
  void updateEmployeePositionShouldDelegateToFacadeTransfer() {
    OrganizationFacadeService manageFacade = mock(OrganizationFacadeService.class);
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    UserService userService = mock(UserService.class);
    UserCenter userCenter = mock(UserCenter.class);

    EmployeeController controller = new EmployeeController(manageFacade, queryFacade, userService, userCenter);

    UpdateEmployeePositionRequest request = new UpdateEmployeePositionRequest();
    request.setPositionId("position-1");

    controller.updateEmployeePosition("employee-1", request);

    verify(manageFacade).transferToPosition("employee-1", "position-1");
  }
}
