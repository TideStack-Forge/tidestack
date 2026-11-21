package com.ouroboros.workflow.approval.dev.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.businessmodel.dev.service.BusinessModelApprovalFieldSourceService;
import com.ouroboros.fieldtype.*;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.file.VCSFile;

class ApprovalWorkflowDesignTimeFormControllerTest {
  @Test
  void readsOnlySelectedApplicationDraftAndPassesItsFieldCatalogContext() throws Exception {
    var center = mock(VCSManagerCenter.class);
    var catalog = spy(new DefaultFieldTypeCatalog(List.of(new JarFieldTypeDefinitionProvider(new FieldTypeDefinitionReader())), new DefaultFieldTypeCompiler()));
    for (String app : List.of("alpha", "beta")) {
      var vcs = mock(VCSManager.class);
      when(center.getVcsManager(app)).thenReturn(vcs);
      when(vcs.walkDirectory("approval-form", ".approval-form.json")).thenAnswer(call -> Stream.of(file(app)));
    }
    var businessModels = mock(BusinessModelApprovalFieldSourceService.class);
    var controller = new ApprovalWorkflowDesignTimeFormController(center, catalog, businessModels);
    var mvc = MockMvcBuilders.standaloneSetup(controller).build();
    mvc.perform(get("/api/dev/alpha/approval-workflow/base-info/form-types"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[1].label").value("自定义表单"));
    mvc.perform(get("/api/dev/alpha/approval-workflow/base-info/form-types").param("value", "externalForm"))
        .andExpect(jsonPath("$.data.items[2].value").value("externalForm"))
        .andExpect(jsonPath("$.data.items[2].label").value("externalForm（当前来源不可用）"));
    mvc.perform(get("/api/dev/alpha/approval-workflow/base-info/form/custom").param("keyword", "alpha").param("perPage", "1"))
        .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].title").value("alpha采购"));
    mvc.perform(get("/api/dev/beta/approval-workflow/base-info/form/custom").param("value", "purchase"))
        .andExpect(jsonPath("$.data.items[0].title").value("beta采购"));
    mvc.perform(get("/api/dev/alpha/approval-workflow/base-info/workflow-field").param("formType", "custom").param("formKey", "purchase"))
        .andExpect(jsonPath("$.data.fields[0].name").value("amount"))
        .andExpect(jsonPath("$.data.fields[0].valueType").value("Decimal"))
        .andExpect(jsonPath("$.data.boundForm.formTitle").value("alpha采购"))
        .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print());
    verify(catalog, atLeastOnce()).compile(argThat(context -> "alpha".equals(context.getAppName())), eq("定点数字"), anyMap());
    verifyNoInteractions(businessModels);
    mvc.perform(get("/api/dev/alpha/approval-workflow/base-info/form/custom/missing/workflow-field"))
        .andExpect(jsonPath("$.code").value(-1));
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> controller.types("missing", null));
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> controller.forms("alpha", "default", null, null, 1, null));
  }

  @Test
  void delegatesBusinessModelsAndUnboundDraftsToTheExistingSourceService() {
    var center = mock(VCSManagerCenter.class);
    when(center.getVcsManager("alpha")).thenReturn(mock(VCSManager.class));
    var businessModels = mock(BusinessModelApprovalFieldSourceService.class);
    when(businessModels.forms(null, null)).thenReturn(List.of(java.util.Map.of("name", "order", "title", "订单")));
    var controller = new ApprovalWorkflowDesignTimeFormController(center, mock(FieldTypeCatalog.class), businessModels);
    controller.forms("alpha", "business-model", null, null, 1, 10);
    controller.fields("alpha", "business-model", "order");
    controller.boundFields("alpha", null, null, "approval");
    verify(businessModels).source("alpha", "business-model", "order", null);
    verify(businessModels).source("alpha", null, null, "approval");
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> controller.forms("alpha", "business-model", null, null, 0, 1));
  }

  private VCSFile file(String app) {
    var file = mock(VCSFile.class);
    when(file.getPath()).thenReturn("approval-form/purchase.approval-form.json");
    when(file.getContent()).thenReturn("""
        {"formatVersion":1,"name":"purchase","title":"%s采购","fields":[
         {"name":"amount","label":"采购金额","type":"定点数字","params":{"size":12,"precision":2}}]}
        """.formatted(app));
    return file;
  }
}
