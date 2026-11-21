package com.ouroboros.businessmodel.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.action.BusinessActionContext;
import com.ouroboros.businessmodel.action.BusinessActionInput;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.ApprovalBusinessBindingValidator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowBusinessActionValidatorTest {
  @Test
  void validatesNormalizedInputAndNeverTrustsPayloadIdentity() {
    var models = mock(BusinessModelProvider.class);
    when(models.getBusinessModel("sales.Order")).thenReturn(Optional.of(model(true, "approval")));
    var bindings = mock(ApprovalBusinessBindingValidator.class);
    var validator = new WorkflowBusinessActionValidator(models, () -> bindings);
    var input = BusinessActionInput.of(Map.of("record", Map.of("id", 42), "instanceId", "instance", "taskId", "task"),
        Map.of("businessRecordType", "forged", "businessRecordId", "other"));
    validator.validate(context("submitTask"), input);
    validator.validate(context("initTaskForm"), input);
    verify(bindings, times(2)).validate("approval", "sales.Order", "42", "instance", "task");
    validator.validate(context("startProcess"), BusinessActionInput.paramsOnly(Map.of("record", Map.of("id", 42))));
    verify(bindings).validate("approval", "sales.Order", "42", null, null);
    doThrow(new BusinessException("foreign instance")).when(bindings).validate("approval", "sales.Order", "42", "instance", "task");
    assertThrows(BusinessException.class, () -> validator.validate(context("submitTask"), input));
  }

  @Test
  void preservesOrdinaryActionsAndDiagnosesMissingFormalWorkflowBindingsAndParameters() {
    var models = mock(BusinessModelProvider.class);
    var bindings = mock(ApprovalBusinessBindingValidator.class);
    var validator = new WorkflowBusinessActionValidator(models, () -> bindings);
    validator.validate(context("approve"), BusinessActionInput.empty());
    when(models.getBusinessModel("sales.Order")).thenReturn(Optional.of(model(false, "approval")));
    validator.validate(context("submitTask"), BusinessActionInput.empty());
    verifyNoInteractions(bindings);
    when(models.getBusinessModel("sales.Order")).thenReturn(Optional.of(model(true, "")));
    assertThrows(BusinessException.class, () -> validator.validate(context("submitTask"), BusinessActionInput.empty()));
    when(models.getBusinessModel("sales.Order")).thenReturn(Optional.of(model(true, "approval")));
    assertThrows(BusinessException.class, () -> validator.validate(context("submitTask"), BusinessActionInput.empty()));
    assertThrows(BusinessException.class, () -> validator.validate(context("submitTask"),
        BusinessActionInput.paramsOnly(Map.of("record", Map.of("id", "42"), "instanceId", "instance"))));
  }

  private BusinessActionContext context(String action) {
    return new BusinessActionContext("sales.Order", action, "sales.Order.workflow", "execution", null, Map.of(), null, null, false);
  }

  private DefaultBusinessModel model(boolean enabled, String flow) {
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("sales");
    meta.setName("Order");
    var id = new BusinessModelFieldMeta();
    id.setName("id");
    id.setType("单行文本");
    id.setIsPrimaryKey(true);
    meta.setFields(List.of(id));
    meta.setExtraProp("workflow", Map.of("isEnabled", enabled, "bindings", List.of(Map.of("processId", flow))));
    return new DefaultBusinessModel(meta, mock(BusinessModelProvider.class));
  }
}
