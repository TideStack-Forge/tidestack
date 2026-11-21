package com.ouroboros.organizationstructure.controller;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.organizationstructure.dto.response.OrganizationNodeDTO;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationTreeControllerHttpStatusTest {

  @Test
  void getTreeShouldReturnHttp404WhenRootNodeIsMissing() throws Exception {
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    when(queryFacade.buildTree("missing", false, null, null))
        .thenThrow(new BusinessException(404, "组织节点不存在"));

    OrganizationTreeController controller = new OrganizationTreeController(queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/tree").param("rootId", "missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("组织节点不存在"));
  }

  @Test
  void getTreeShouldReturnHttp200AndDelegateQueryParameters() throws Exception {
    OrganizationQueryFacade queryFacade = mock(OrganizationQueryFacade.class);
    OrganizationNodeDTO dto = new OrganizationNodeDTO();
    dto.setId("company-1");
    dto.setName("company-1");
    dto.setType("company");
    when(queryFacade.buildTree("company-1", true, Collections.singletonList("company"), "company"))
        .thenReturn(Collections.singletonList(dto));

    OrganizationTreeController controller = new OrganizationTreeController(queryFacade);

    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/organization/tree")
            .param("rootId", "company-1")
            .param("includePositions", "true")
            .param("types", "company")
            .param("type", "company"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].id").value("company-1"));

    verify(queryFacade).buildTree("company-1", true, Collections.singletonList("company"), "company");
  }

  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) {
      return WebApiResult.error(exception.getErrorCode(), exception.getMessage());
    }
  }
}
