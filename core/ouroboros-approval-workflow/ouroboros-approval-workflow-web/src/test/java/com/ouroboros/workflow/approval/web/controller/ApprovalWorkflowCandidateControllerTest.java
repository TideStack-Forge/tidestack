package com.ouroboros.workflow.approval.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.CandidateProvider;

class ApprovalWorkflowCandidateControllerTest {

  @Test
  void searchItemsShouldApplyAppNameToDevAppHolderContext() throws Exception {
    ApprovalWorkflowCandidateController controller = new ApprovalWorkflowCandidateController(
        Collections.<CandidateProvider>singletonList(new CandidateProvider() {
          @Override
          public String getName() {
            return "Role";
          }

          @Override
          public String getLabel() {
            return "角色";
          }

          @Override
          public ConfigMode getConfigMode() {
            return ConfigMode.MultiSelect;
          }

          @Override
          public String getValueKey() {
            return "roles";
          }

          @Override
          public java.util.List<CandidateItem> getItems() {
            return search(null, null);
          }

          @Override
          public java.util.List<CandidateItem> search(String category, String keyword) {
            CandidateItem item = new CandidateItem();
            item.setValue(DevAppHolder.getAppName());
            item.setLabel(DevAppHolder.getAppName());
            return Collections.singletonList(item);
          }

          @Override
          public java.util.List<CandidateItem> search(java.util.List<String> valueList) {
            return search(null, null);
          }
        })
    );

    var mvc = MockMvcBuilders.standaloneSetup(controller).build();

    mvc.perform(get("/api/approval-workflow/candidate/Role/items").param("appName", "sales-app"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].value").value("sales-app"));

    assertEquals(null, DevAppHolder.getAppName());
  }

  @Test
  void getCandidateTypesShouldExposeProviderSchema() throws Exception {
    ApprovalWorkflowCandidateController controller = new ApprovalWorkflowCandidateController(
        Collections.<CandidateProvider>singletonList(new CandidateProvider() {
          @Override
          public String getName() {
            return "Starter";
          }

          @Override
          public String getLabel() {
            return "流程发起人";
          }

          @Override
          public SchemaNode getSchema() {
            SchemaNode schema = new SchemaNode();
            schema.setType("hidden");
            schema.setName("isChecked");
            schema.setProp("value", true);
            return schema;
          }

          @Override
          public ConfigMode getConfigMode() {
            return ConfigMode.Check;
          }

          @Override
          public String getValueKey() {
            return "isChecked";
          }

          @Override
          public java.util.List<CandidateItem> getItems() {
            return Collections.emptyList();
          }

          @Override
          public java.util.List<CandidateItem> search(String category, String keyword) {
            return Collections.emptyList();
          }

          @Override
          public java.util.List<CandidateItem> search(java.util.List<String> valueList) {
            return Collections.emptyList();
          }
        })
    );

    var mvc = MockMvcBuilders.standaloneSetup(controller).build();

    mvc.perform(get("/api/approval-workflow/candidate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].name").value("Starter"))
        .andExpect(jsonPath("$.data.items[0].schema.type").value("hidden"))
        .andExpect(jsonPath("$.data.items[0].schema.name").value("isChecked"))
        .andExpect(jsonPath("$.data.items[0].schema.value").value(true));
  }
}
