package com.ouroboros.workflow.approval.resolver;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.util.SPI;
import com.ouroboros.workflow.approval.ApprovalWorkflowCandidateFormDataAccessor;

public class ApprovalWorkflowCandidateFormDataAccessorRegistry {
  private final List<ApprovalWorkflowCandidateFormDataAccessor> accessors;

  public ApprovalWorkflowCandidateFormDataAccessorRegistry() {
    this(SPI.getCachedReversedServiceStream(ApprovalWorkflowCandidateFormDataAccessor.class)
        .collect(Collectors.toList()));
  }

  ApprovalWorkflowCandidateFormDataAccessorRegistry(List<ApprovalWorkflowCandidateFormDataAccessor> accessors) {
    this.accessors = accessors;
  }

  public Object getFieldValue(String processInstanceId, String taskInstanceId, String taskName, String fieldName) {
    if (StringUtils.isBlank(fieldName) || accessors == null || accessors.isEmpty()) {
      return null;
    }
    return accessors.stream()
        .map(accessor -> accessor.getFieldValue(processInstanceId, taskInstanceId, taskName, fieldName))
        .filter(value -> value != null)
        .findFirst()
        .orElse(null);
  }
}
