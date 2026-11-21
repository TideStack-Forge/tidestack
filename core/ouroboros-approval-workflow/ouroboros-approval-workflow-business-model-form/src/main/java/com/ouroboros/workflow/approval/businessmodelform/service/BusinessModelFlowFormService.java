package com.ouroboros.workflow.approval.businessmodelform.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.FlowFormService;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;

@Service
public class BusinessModelFlowFormService implements FlowFormService {
  private static final String TYPE = "business-model";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public Try<SchemaNode> getInitialFormSchema(String formKey, List<NodeFormFieldPermission> permissions) {
    return Try.success(emptyContainer());
  }

  @Override
  public Try<String> submitInitialForm(String instanceId, String formKey, String userId,
                                       List<NodeFormFieldPermission> permissions, Map<String, Object> formData) {
    return Try.success(extractBusinessRecordId(formData).orElse(null));
  }

  @Override
  public Try<SchemaNode> getTaskFormSchema(String instanceId, String taskId, String formKey, String formId,
                                           List<NodeFormFieldPermission> permissions, List<String> actions) {
    return Try.success(emptyTaskForm(actions));
  }

  @Override
  public Try<Void> submitTaskForm(String instanceId, String taskId, String formKey, String formId, String userId,
                                  List<NodeFormFieldPermission> permissions, Map<String, Object> formData) {
    return Try.success(null);
  }

  private SchemaNode emptyContainer() {
    SchemaNode container = new SchemaNode();
    container.setType("container");
    container.setProp("body", Collections.emptyList());
    return container;
  }

  private SchemaNode emptyTaskForm(List<String> actions) {
    SchemaNode form = new SchemaNode();
    form.setType("form");
    form.setProp("id", "form");
    form.setProp("title", "");
    form.setProp("body", Collections.emptyList());
    form.setProp("actions", buildActionNames(actions));
    return form;
  }

  private List<String> buildActionNames(List<String> actions) {
    List<String> result = new ArrayList<>();
    result.add("approve");
    Optional.ofNullable(actions)
        .orElseGet(Collections::emptyList)
        .stream()
        .filter(action -> action != null && !action.trim().isEmpty())
        .filter(action -> result.stream().noneMatch(existing -> existing.equalsIgnoreCase(action)))
        .forEach(result::add);
    return result;
  }

  private Optional<String> extractBusinessRecordId(Map<String, Object> formData) {
    if (formData == null) {
      return Optional.empty();
    }
    Object id = formData.get("id");
    if (id == null) {
      return Optional.empty();
    }
    String value = String.valueOf(id).trim();
    return value.isEmpty() ? Optional.empty() : Optional.of(value);
  }
}
