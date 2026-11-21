package com.ouroboros.modelmanage.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.template.JarTemplateEngine;
import com.ouroboros.util.Json;

class DevFragmentSchemaContractTest {

  private final JarTemplateEngine templateEngine =
      new JarTemplateEngine("META-INF/ouroboros/dev/fragment-schema");

  @Test
  void vcsButtonsFragmentKeepsDefaultEditActionsEnabledContract() {
    String rendered = templateEngine.generate("vcs-buttons", baseParams());

    assertThat(Json.tryToMap(rendered).isSuccess()).isTrue();
    assertThat(rendered).contains("\"disabledOn\": \"${lockState == 'LOCKED'}\"");
    assertThat(rendered).contains("\"disabledOn\": \"${false}\"");
    assertThat(rendered).doesNotContain("mode === 'preview'");
  }

  @Test
  void vcsButtonsFragmentSupportsHostProvidedEditDisabledExpression() {
    Map<String, String> params = baseParams();
    params.put("editDisabledOn", "mode === 'preview'");

    String rendered = templateEngine.generate("vcs-buttons", params);

    assertThat(Json.tryToMap(rendered).isSuccess()).isTrue();
    assertThat(rendered).contains("\"disabledOn\": \"${lockState == 'LOCKED' || (mode === 'preview')}\"");
    assertThat(rendered).contains("\"disabledOn\": \"${mode === 'preview'}\"");
  }

  private static Map<String, String> baseParams() {
    Map<String, String> params = new HashMap<>();
    params.put("apiPath", "approval-workflow-definition/${id}");
    params.put("statusId", "approvalWorkflowStatus");
    params.put("formId", "edit-workflow-model-form");
    params.put("actionType", "static");
    return params;
  }
}
