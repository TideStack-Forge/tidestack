package com.ouroboros.workflow.approval.web.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.uischema.schema.FormNode;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.definition.Candidate;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.candidates.UserSelectCandidate;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.web.vo.ApprovalWorkflowStartRequirementsResult;
import com.ouroboros.workflow.approval.web.vo.StartFlowInstanceData;

public class ApprovalWorkflowStartRequirementsService {

  public ApprovalWorkflowStartRequirementsResult getStartRequirements(
      FlowDefinitionService flowDefinitionService,
      String flowKey,
      StartFlowInstanceData data) {
    Map<String, Object> formData = data == null || data.getFormData() == null
        ? Collections.emptyMap()
        : data.getFormData();
    List<ApprovalNode> nodes = getUserSelectNodes(flowDefinitionService, flowKey, formData);
    if (ObjectUtils.isEmpty(nodes)) {
      return ApprovalWorkflowStartRequirementsResult.none();
    }
    return ApprovalWorkflowStartRequirementsResult.inputRequired(buildStartRequirementsForm(nodes));
  }

  public SchemaNode buildStartRequirementsForm(
      com.ouroboros.workflow.approval.definition.startrequirements.StartRequirements requirements) {
    if (requirements.requirements() == null || requirements.requirements().isEmpty()) {
      return null;
    }
    List<SchemaNode> body = new ArrayList<>();
    requirements.requirements().forEach(requirement -> {
      SchemaNode picker = new SchemaNode();
      picker.setType("user-picker");
      picker.setName("taskAssignee." + requirement.nodeId());
      picker.setProp("label", requirement.label());
      picker.setProp("multiple", true);
      picker.setProp("minLength", requirement.min() == null ? 0 : requirement.min());
      picker.setProp("maxLength", requirement.max() == null
          ? ("single".equalsIgnoreCase(requirement.mode()) ? 1 : 99) : requirement.max());
      picker.setProp("allowCompanyLevelSelect", true);
      body.add(picker);
    });
    FormNode form = new FormNode();
    form.setMode("normal");
    form.setWrapWithPanel(false);
    form.setProp("body", body);
    return form;
  }

  public SchemaNode buildTaskAssigneePicker(FlowNode node, Object label) {
    UserSelectCandidate.Mode mode = resolveUserSelectMode(node.getCandidates());

    SchemaNode picker = new SchemaNode();
    picker.setType("user-picker");
    picker.setName("taskAssignee." + node.getId());
    picker.setProp("label", label);
    picker.setProp("multiple", true);
    picker.setProp("minLength", 1);
    picker.setProp("maxLength", mode == UserSelectCandidate.Mode.SINGLE ? 1 : 99);
    picker.setProp("allowCompanyLevelSelect", true);
    return picker;
  }

  private List<ApprovalNode> getUserSelectNodes(
      FlowDefinitionService flowDefinitionService,
      String flowKey,
      Map<String, Object> formData) {
    List<ApprovalNode> nodes = ObjectUtils.isEmpty(formData)
        ? flowDefinitionService.getUserSelectCandidateApprovalNodes(flowKey)
        : flowDefinitionService.getUserSelectCandidateApprovalNodes(flowKey, formData);
    if (ObjectUtils.isNotEmpty(formData) && ObjectUtils.isEmpty(nodes)) {
      return flowDefinitionService.getUserSelectCandidateApprovalNodes(flowKey);
    }
    return nodes;
  }

  private SchemaNode buildStartRequirementsForm(List<ApprovalNode> nodes) {
    List<SchemaNode> body = new ArrayList<>();
    nodes.forEach(node -> body.add(buildTaskAssigneePicker(node, node.getName())));

    FormNode form = new FormNode();
    form.setMode("normal");
    form.setWrapWithPanel(false);
    form.setProp("body", body);
    return form;
  }

  private UserSelectCandidate.Mode resolveUserSelectMode(List<Candidate> candidates) {
    if (candidates == null) {
      return UserSelectCandidate.Mode.SINGLE;
    }
    return candidates.stream()
        .filter(UserSelectCandidate.class::isInstance)
        .map(UserSelectCandidate.class::cast)
        .map(UserSelectCandidate::getMode)
        .filter(ObjectUtils::isNotEmpty)
        .findFirst()
        .orElse(UserSelectCandidate.Mode.SINGLE);
  }
}
