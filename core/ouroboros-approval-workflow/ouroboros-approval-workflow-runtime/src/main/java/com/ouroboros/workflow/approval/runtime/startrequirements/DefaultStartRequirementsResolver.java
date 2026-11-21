package com.ouroboros.workflow.approval.runtime.startrequirements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.ApprovalWorkflowErrorCodes;
import com.ouroboros.workflow.approval.BuiltInFlowDefinitionService;
import com.ouroboros.workflow.approval.definition.candidates.UserSelectCandidate;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.startrequirements.CandidateRange;
import com.ouroboros.workflow.approval.definition.startrequirements.BuiltInStartRequirementsResolver;
import com.ouroboros.workflow.approval.definition.startrequirements.Requirement;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirements;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirementsResolver;

import io.vavr.control.Try;

/**
 * 内置引擎发起需求解析（Phase 4 M22，引擎无关化）。
 *
 * <p>从流程定义的自选节点（{@code UserSelectCandidate}）生成统一契约
 * {@link StartRequirements}：min=1、max 由 mode 派生（SINGLE=1、MULTIPLE 不限），
 * candidateRange/allowOverride 取定义声明（Phase 4 M22 新增字段，旧定义缺省 null/false）。
 * ccEnabled=true（内置发起接口支持 cc；按流程定义级判定待接入，javadoc 注明）。
 */
public class DefaultStartRequirementsResolver implements BuiltInStartRequirementsResolver {

  private final BuiltInFlowDefinitionService flowDefinitionService;

  public DefaultStartRequirementsResolver(BuiltInFlowDefinitionService flowDefinitionService) {
    this.flowDefinitionService = flowDefinitionService;
  }

  @Override
  public Try<StartRequirements> getStartRequirements(String flowKey, Map<String, Object> formData) {
    try {
      List<ApprovalNode> nodes = getUserSelectNodes(flowKey, formData);
      if (ObjectUtils.isEmpty(nodes)) {
        return Try.success(StartRequirements.none(true));
      }
      List<Requirement> requirements = new ArrayList<>();
      for (ApprovalNode node : nodes) {
        UserSelectCandidate candidate = UserSelectCandidate.resolve(node.getCandidates());
        requirements.add(new Requirement(
            node.getId(),
            node.getName(),
            candidate == null ? UserSelectCandidate.Mode.SINGLE.toString() : candidate.getMode().toString(),
            1,
            candidate != null && candidate.getMode() == UserSelectCandidate.Mode.SINGLE ? 1 : null,
            candidate == null ? null : candidate.getCandidateRange(),
            candidate != null && candidate.isAllowOverride()));
      }
      return Try.success(new StartRequirements(requirements, true));
    } catch (RuntimeException ex) {
      return Try.failure(new BusinessException(
          "[" + ApprovalWorkflowErrorCodes.WORKFLOW_START_REQUIREMENTS_RESOLVE_FAILED
              + "] 发起需求解析失败: " + ex.getMessage(),
          ex));
    }
  }

  private List<ApprovalNode> getUserSelectNodes(String flowKey, Map<String, Object> formData) {
    List<ApprovalNode> nodes = ObjectUtils.isEmpty(formData)
        ? flowDefinitionService.getUserSelectCandidateApprovalNodes(flowKey)
        : flowDefinitionService.getUserSelectCandidateApprovalNodes(flowKey, formData);
    if (ObjectUtils.isNotEmpty(formData) && ObjectUtils.isEmpty(nodes)) {
      return flowDefinitionService.getUserSelectCandidateApprovalNodes(flowKey);
    }
    return nodes;
  }
}
