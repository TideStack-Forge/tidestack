package com.ouroboros.workflow.approval.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.FlowFormProvider;
import com.ouroboros.workflow.approval.model.ExpressionVariable;
import com.ouroboros.workflow.approval.model.FlowForm;
import com.ouroboros.workflow.approval.model.FlowFormField;

import io.vavr.Tuple;
import io.vavr.Tuple2;

class ApprovalWorkflowBaseInfoControllerTest {

  @Test
  void workflowFieldSourceCombinesConditionAndPermissionUsages() {
    ApprovalWorkflowBaseInfoController controller = new ApprovalWorkflowBaseInfoController(
        Collections.singletonList(new StubFlowFormProvider())
    );

    var result = controller.getWorkflowFields("default", "purchase");

    assertEquals(0, result.getCode());
    assertEquals("form-fields", result.getData().get("sourceKind"));
    assertTrue(result.getData().get("revisionToken").toString()
        .startsWith("form-fields:default:purchase:sha256:"));
    assertEquals("采购（purchase）", result.getData().get("boundFormSummary"));
    Map<?, ?> boundForm = (Map<?, ?>) result.getData().get("boundForm");
    assertEquals("default", boundForm.get("formType"));
    assertEquals("purchase", boundForm.get("formKey"));

    List<?> fields = (List<?>) result.getData().get("fields");
    Map<?, ?> amount = fields.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .filter(item -> "amount".equals(item.get("value")))
        .findFirst()
        .orElseThrow(AssertionError::new);
    assertEquals("amount", amount.get("name"));
    assertEquals("金额", amount.get("label"));
    assertEquals("number", amount.get("type"));
    assertEquals("Decimal", amount.get("valueType"));
    assertEquals(Collections.singletonList("greater"), amount.get("operators"));
    assertEquals(true, amount.get("workflowCandidateField"));
    assertTrue(((List<?>) amount.get("usages")).contains("condition"));
    assertTrue(((List<?>) amount.get("usages")).contains("permission"));

    Map<?, ?> reviewer = fields.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .filter(item -> "reviewer".equals(item.get("value")))
        .findFirst()
        .orElseThrow(AssertionError::new);
    assertEquals("员工", reviewer.get("type"));
    assertEquals(Collections.singletonList("permission"), reviewer.get("usages"));
  }

  @Test
  void workflowFieldsDoNotUseInputTypeAsMissingValueType() {
    var provider = new StubFlowFormProvider() {
      @Override
      protected FlowForm form() {
        var form = super.form();
        form.getExpressionVariables().get(0).getExtraProps().remove("valueType");
        return form;
      }
    };
    var fields = (List<?>) new ApprovalWorkflowBaseInfoController(List.of(provider))
        .getWorkflowFields("default", "purchase").getData().get("fields");

    assertTrue(!((Map<?, ?>) fields.get(0)).containsKey("valueType"));
  }

  @Test
  void workflowFieldRevisionTokenChangesWhenFieldContentChangesWithoutChangingFieldCount() {
    var original = new ApprovalWorkflowBaseInfoController(
        Collections.singletonList(new StubFlowFormProvider()))
        .getWorkflowFields("default", "purchase")
        .getData()
        .get("revisionToken");
    var changed = new ApprovalWorkflowBaseInfoController(
        Collections.singletonList(new LabelChangedFlowFormProvider()))
        .getWorkflowFields("default", "purchase")
        .getData()
        .get("revisionToken");

    assertTrue(original.toString().startsWith("form-fields:default:purchase:sha256:"));
    assertTrue(changed.toString().startsWith("form-fields:default:purchase:sha256:"));
    assertTrue(!original.equals(changed));
  }

  @Test
  void workflowFieldRevisionTracksDisplayOrder() {
    var form = new StubFlowFormProvider().form();
    var assembler = new com.ouroboros.workflow.approval.form.FlowFormSourceAssembler();
    var before = assembler.build("default", "purchase", form).get("revisionToken");
    Collections.reverse(form.getFields());
    assertTrue(!before.equals(assembler.build("default", "purchase", form).get("revisionToken")));
  }

  @Test
  void sourceTypesRetainUnavailableBindingsWithoutInventingProviders() throws Exception {
    var controller = new ApprovalWorkflowBaseInfoController(List.of(new StubFlowFormProvider()));
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/approval-workflow/base-info/form-types").param("value", "externalForm"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.items[1].value").value("externalForm"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.items[1].label").value("externalForm（当前来源不可用）"));
  }

  private static class StubFlowFormProvider implements FlowFormProvider {

    @Override
    public String getType() {
      return "default";
    }

    @Override
    public List<FlowForm> search(String keyword) {
      return Collections.singletonList(form());
    }

    @Override
    public Tuple2<Long, List<FlowForm>> search(String keyword, Integer page, Integer pageSize) {
      return Tuple.of(1L, Collections.singletonList(form()));
    }

    @Override
    public Optional<FlowForm> get(String key) {
      return "purchase".equals(key) ? Optional.of(form()) : Optional.empty();
    }

    protected FlowForm form() {
      FlowFormField field = new FlowFormField();
      field.setName("amount");
      field.setLabel("金额");
      field.setType("number");
      field.setExtraProp("workflowCandidateField", true);

      FlowFormField reviewer = new FlowFormField();
      reviewer.setName("reviewer");
      reviewer.setLabel("复核人");
      reviewer.setType("员工");

      FlowForm form = new FlowForm();
      form.setName("purchase");
      form.setTitle("采购");
      form.setFields(Arrays.asList(field, reviewer));
      form.setExpressionVariables(Collections.singletonList(
          new ExpressionVariable()
              .setName("amount")
              .setLabel("金额")
              .setType("input-number")
              .setExtraProps(new java.util.LinkedHashMap<>(Map.of(
                  "operators", Collections.singletonList("greater"), "valueType", "Decimal")))
      ));
      return form;
    }
  }

  private static class LabelChangedFlowFormProvider extends StubFlowFormProvider {
    @Override
    protected FlowForm form() {
      FlowForm form = super.form();
      form.getFields().get(0).setLabel("金额合计");
      return form;
    }
  }
}
