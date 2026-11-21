package com.ouroboros.workflow.approval.dev.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.workflow.approval.dev.candidate.ApprovalWorkflowDesignTimeCandidateRegistry;
import com.ouroboros.workflow.approval.dev.candidate.FormFieldDesignTimeCandidateProvider;
import com.ouroboros.workflow.approval.dev.candidate.StarterDesignTimeCandidateProvider;
import com.ouroboros.workflow.approval.dev.candidate.UserSelectDesignTimeCandidateProvider;

class ApprovalWorkflowDesignTimeCandidateControllerTest {

  @Test
  void designTimeCandidateApiShouldExposeStaticCandidateTypesAndKeepUnknownTypeEmpty() throws Exception {
    ApprovalWorkflowDesignTimeCandidateRegistry registry = new ApprovalWorkflowDesignTimeCandidateRegistry(Arrays.asList(
        new StarterDesignTimeCandidateProvider(),
        new FormFieldDesignTimeCandidateProvider(),
        new UserSelectDesignTimeCandidateProvider()
    ));
    ApprovalWorkflowDesignTimeCandidateController controller = new ApprovalWorkflowDesignTimeCandidateController(registry);

    var mvc = MockMvcBuilders.standaloneSetup(controller).build();

    mvc.perform(get("/api/dev/demo-app/approval-workflow/candidate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(3))
        .andExpect(jsonPath("$.data.items[0].name").value("Starter"))
        .andExpect(jsonPath("$.data.items[0].valueKey").value("isChecked"))
        .andExpect(jsonPath("$.data.items[1].name").value("FormField"))
        .andExpect(jsonPath("$.data.items[1].label").value("表单字段"))
        .andExpect(jsonPath("$.data.items[1].configMode").value("SingleSelect"))
        .andExpect(jsonPath("$.data.items[1].valueKey").value("field"))
        .andExpect(jsonPath("$.data.items[1].schema.type").value("select"))
        .andExpect(jsonPath("$.data.items[1].schema.name").value("field"))
        .andExpect(jsonPath("$.data.items[1].schema.source").value("$candidateFieldSource.fields"))
        .andExpect(jsonPath("$.data.items[2].name").value("UserSelect"))
        .andExpect(jsonPath("$.data.items[2].items[0].value").value("single"))
        .andExpect(jsonPath("$.data.items[2].items[1].value").value("multiple"));

    mvc.perform(get("/api/dev/demo-app/approval-workflow/candidate/Unknown/items"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(0));

    mvc.perform(get("/api/dev/demo-app/approval-workflow/candidate/Unknown/category"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(0));

    mvc.perform(get("/api/dev/demo-app/approval-workflow/candidate/User/items"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(0));
  }
}
