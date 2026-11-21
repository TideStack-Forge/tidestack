package com.ouroboros.workflow.approval.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.ApprovalWorkflowCandidateFormDataAccessor;
import com.ouroboros.workflow.approval.definition.candidates.FormFieldCandidate;

class FormFieldCandidateUserResolverTest {
  @Test
  void resolveShouldReturnStringFieldValue() {
    FormFieldCandidate candidate = candidate("managerId");
    FormFieldCandidateUserResolver resolver = resolverReturning("managerId", "user-a");

    List<String> users = resolver.resolve(candidate, "instance-1", "task-1", "经理审批");

    assertEquals(Collections.singletonList("user-a"), users);
  }

  @Test
  void resolveShouldFlattenSupportedCollectionArrayAndMapValues() {
    FormFieldCandidate candidate = candidate("approvers");
    Map<String, Object> userMap = new HashMap<>();
    userMap.put("userId", "user-b");
    Map<String, Object> valueMap = new HashMap<>();
    valueMap.put("value", "user-c");
    Object rawValue = Arrays.asList("user-a", userMap, new Object[] {valueMap, "user-a"});
    FormFieldCandidateUserResolver resolver = resolverReturning("approvers", rawValue);

    List<String> users = resolver.resolve(candidate, "instance-1", "task-1", "经理审批");

    assertEquals(Arrays.asList("user-a", "user-b", "user-c"), users);
  }

  @Test
  void resolveShouldReturnEmptyListForMissingBlankOrUnsupportedValues() {
    assertEquals(Collections.emptyList(), resolverReturning("managerId", null)
        .resolve(candidate("managerId"), "instance-1", "task-1", "经理审批"));
    assertEquals(Collections.emptyList(), resolverReturning("managerId", " ")
        .resolve(candidate("managerId"), "instance-1", "task-1", "经理审批"));
    assertEquals(Collections.emptyList(), resolverReturning("managerId", 42)
        .resolve(candidate("managerId"), "instance-1", "task-1", "经理审批"));
  }

  @Test
  void resolveWithoutContextShouldNotFallbackToOtherCandidateTypes() {
    FormFieldCandidateUserResolver resolver = resolverReturning("managerId", "user-a");

    assertEquals(Collections.emptyList(), resolver.resolve(candidate("managerId")));
  }

  private FormFieldCandidate candidate(String field) {
    FormFieldCandidate candidate = new FormFieldCandidate();
    candidate.setField(field);
    return candidate;
  }

  private FormFieldCandidateUserResolver resolverReturning(String fieldName, Object value) {
    ApprovalWorkflowCandidateFormDataAccessor accessor = (processInstanceId, taskInstanceId, taskName, requestedFieldName) ->
        fieldName.equals(requestedFieldName) ? value : null;
    return new FormFieldCandidateUserResolver(new ApprovalWorkflowCandidateFormDataAccessorRegistry(Collections.singletonList(accessor)));
  }
}
