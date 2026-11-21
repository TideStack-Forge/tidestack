package com.ouroboros.workflow.approval.businessmodelform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.businessmodelform.service.BusinessModelFlowFormService;

class BusinessModelFlowFormServiceTest {
  private final BusinessModelFlowFormService service = new BusinessModelFlowFormService();

  @Test
  void getTypeReturnsBusinessModel() {
    assertEquals("business-model", service.getType());
  }

  @Test
  void submitInitialFormReturnsExistingBusinessRecordId() {
    Map<String, Object> formData = new LinkedHashMap<>();
    formData.put("id", 2090694074058145792L);
    formData.put("amount", 1200);

    var result = service.submitInitialForm("instance-1", "awf-expense-request", "awf.submitter", Collections.emptyList(), formData);

    assertTrue(result.isSuccess());
    assertEquals("2090694074058145792", result.get());
  }

  @Test
  void submitInitialFormDoesNotRequireSyntheticFormRecordWhenBusinessIdIsAbsent() {
    var result = service.submitInitialForm("instance-1", "awf-expense-request", "awf.submitter", Collections.emptyList(), Collections.emptyMap());

    assertTrue(result.isSuccess());
    assertNull(result.get());
  }

  @Test
  void submitTaskFormDoesNotTouchBusinessDataAfterGeneratedLogicFlowAlreadyUpdatedIt() {
    Map<String, Object> formData = new LinkedHashMap<>();
    formData.put("amount", 1300);

    var result = service.submitTaskForm("instance-1", "task-1", "awf-expense-request", "2090694074058145792", "awf.approver1", Collections.emptyList(), formData);

    assertTrue(result.isSuccess());
  }

  @Test
  void formSchemaMethodsReturnEmptyContainersForNonCanonicalPublicEntry() {
    var initial = service.getInitialFormSchema("awf-expense-request", Collections.emptyList());
    var task = service.getTaskFormSchema("instance-1", "task-1", "awf-expense-request", "record-1", Collections.emptyList(), Arrays.asList("reject", "rollback"));

    assertTrue(initial.isSuccess());
    assertEquals("container", initial.get().getType());
    assertTrue(task.isSuccess());
    assertEquals("form", task.get().getType());
    assertEquals("form", task.get().getProp("id"));
    assertEquals(Collections.emptyList(), task.get().getProp("body"));
    assertEquals(Arrays.asList("approve", "reject", "rollback"), task.get().getProp("actions"));
  }

  @Test
  void taskFormSchemaAlwaysIncludesApproveOnceAndPreservesConfiguredActions() {
    var task = service.getTaskFormSchema(
        "instance-1",
        "task-1",
        "awf-expense-request",
        "record-1",
        Collections.emptyList(),
        Arrays.asList("approve", "freeApprove"));

    assertTrue(task.isSuccess());
    assertEquals(List.of("approve", "freeApprove"), task.get().getProp("actions"));
  }
}
