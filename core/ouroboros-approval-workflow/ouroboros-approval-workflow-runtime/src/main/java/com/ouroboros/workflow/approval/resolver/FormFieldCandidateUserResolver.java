package com.ouroboros.workflow.approval.resolver;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.CandidateUserResolver;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.candidates.FormFieldCandidate;

@Component
public class FormFieldCandidateUserResolver implements CandidateUserResolver {
  private final ApprovalWorkflowCandidateFormDataAccessorRegistry accessorRegistry;

  public FormFieldCandidateUserResolver() {
    this(new ApprovalWorkflowCandidateFormDataAccessorRegistry());
  }

  FormFieldCandidateUserResolver(ApprovalWorkflowCandidateFormDataAccessorRegistry accessorRegistry) {
    this.accessorRegistry = accessorRegistry;
  }

  @Override
  public Boolean support(Candidate candidate) {
    return candidate instanceof FormFieldCandidate;
  }

  @Override
  public List<String> resolve(Candidate candidate, String processInstanceId, String taskInstanceId, String taskName) {
    FormFieldCandidate formFieldCandidate = (FormFieldCandidate) candidate;
    Object rawValue = accessorRegistry.getFieldValue(processInstanceId, taskInstanceId, taskName, formFieldCandidate.getField());
    return normalizeUsers(rawValue).stream()
        .distinct()
        .collect(Collectors.toList());
  }

  @Override
  public List<String> resolve(Candidate candidate) {
    return Collections.emptyList();
  }

  private List<String> normalizeUsers(Object value) {
    if (value == null) {
      return Collections.emptyList();
    }
    if (value instanceof CharSequence) {
      String userId = StringUtils.trimToNull(value.toString());
      return userId == null ? Collections.emptyList() : Collections.singletonList(userId);
    }
    if (value instanceof Map) {
      return normalizeUserMap((Map<?, ?>) value);
    }
    if (value instanceof Collection) {
      List<String> result = new ArrayList<>();
      for (Object item : (Collection<?>) value) {
        result.addAll(normalizeUsers(item));
      }
      return result;
    }
    if (value.getClass().isArray()) {
      List<String> result = new ArrayList<>();
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        result.addAll(normalizeUsers(Array.get(value, i)));
      }
      return result;
    }
    return Collections.emptyList();
  }

  private List<String> normalizeUserMap(Map<?, ?> value) {
    Object id = firstPresent(value, "id", "value", "userId");
    return normalizeUsers(id);
  }

  private Object firstPresent(Map<?, ?> value, String... keys) {
    for (String key : keys) {
      if (value.containsKey(key)) {
        return value.get(key);
      }
    }
    return null;
  }
}
