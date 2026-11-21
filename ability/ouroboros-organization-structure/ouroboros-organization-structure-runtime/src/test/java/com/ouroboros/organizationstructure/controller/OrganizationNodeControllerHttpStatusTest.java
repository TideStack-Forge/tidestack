package com.ouroboros.organizationstructure.controller;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;
import com.ouroboros.organizationstructure.model.CompanyModel;
import com.ouroboros.organizationstructure.model.DepartmentModel;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationNodeControllerHttpStatusTest {

  @Test
  void getByIdShouldReturnHttp404WhenNodeIsMissing() throws Exception {
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getNodeById("missing"))
        .thenReturn(java.util.Optional.empty());

    OrganizationNodeController controller = new OrganizationNodeController(queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/nodes/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("组织节点不存在"));
  }

  @Test
  void getByIdShouldReturnHttp200AndDelegateToFacade() throws Exception {
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getNodeById("department-1"))
        .thenReturn(java.util.Optional.of(department("department-1", "company-1")));

    OrganizationNodeController controller = new OrganizationNodeController(queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/nodes/department-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("department-1"));

    verify(queryFacade).getNodeById("department-1");
  }

  @Test
  void getChildrenShouldReturnHttp404WhenNodeIsMissing() throws Exception {
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getNodeChildrenDTO("missing"))
        .thenThrow(new BusinessException(404, "组织节点不存在"));

    OrganizationNodeController controller = new OrganizationNodeController(queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/nodes/missing/children"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("组织节点不存在"));
  }

  @Test
  void getPathShouldReturnHttp404WhenNodeIsMissing() throws Exception {
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getNodePath("missing"))
        .thenThrow(new BusinessException(404, "组织节点不存在"));

    OrganizationNodeController controller = new OrganizationNodeController(queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/nodes/missing/path"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("组织节点不存在"));
  }

  @Test
  void getPathShouldReturnHttp200AndDelegateToFacade() throws Exception {
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.getNodePath("department-1"))
        .thenReturn(Arrays.asList(company("company-1", null), department("department-1", "company-1")));

    OrganizationNodeController controller = new OrganizationNodeController(queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/nodes/department-1/path"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].id").value("company-1"))
        .andExpect(jsonPath("$.data.items[1].id").value("department-1"));

    verify(queryFacade).getNodePath("department-1");
  }

  private OrganizationNode company(String id, String parent) {
    OrganizationNodeEntity entity = new OrganizationNodeEntity();
    entity.setId(id);
    entity.setName(id);
    entity.setType("company");
    entity.setParent(parent);
    return new CompanyModel(entity, leaderId -> null, parentId -> null);
  }

  private OrganizationNode department(String id, String parent) {
    OrganizationNodeEntity entity = new OrganizationNodeEntity();
    entity.setId(id);
    entity.setName(id);
    entity.setType("department");
    entity.setParent(parent);
    return new DepartmentModel(entity, leaderId -> null, parentId -> null, companyId -> null);
  }

  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) {
      return WebApiResult.error(exception.getErrorCode(), exception.getMessage());
    }
  }
}
