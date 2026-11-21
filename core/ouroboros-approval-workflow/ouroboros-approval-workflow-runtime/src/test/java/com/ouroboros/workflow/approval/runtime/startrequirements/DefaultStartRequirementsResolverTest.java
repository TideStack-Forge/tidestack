package com.ouroboros.workflow.approval.runtime.startrequirements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.BuiltInFlowDefinitionService;
import com.ouroboros.workflow.approval.definition.candidates.UserSelectCandidate;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;
import com.ouroboros.workflow.approval.definition.startrequirements.CandidateRange;
import com.ouroboros.workflow.approval.definition.startrequirements.Requirement;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirements;

import io.vavr.control.Try;

/**
 * M22-T2 内置发起需求解析测试：requirements 字段映射、SINGLE/MULTIPLE 数量区间、无节点 none、条件表单路径。
 */
class DefaultStartRequirementsResolverTest {

  private BuiltInFlowDefinitionService flowDefinitionService;
  private DefaultStartRequirementsResolver resolver;

  @BeforeEach
  void setUp() {
    flowDefinitionService = mock(BuiltInFlowDefinitionService.class);
    resolver = new DefaultStartRequirementsResolver(flowDefinitionService);
  }

  @Test
  void singleCandidateWithRangeAndOverrideIsMapped() {
    UserSelectCandidate candidate = new UserSelectCandidate();
    candidate.setMode(UserSelectCandidate.Mode.SINGLE);
    candidate.setCandidateRange(CandidateRange.ROLE);
    candidate.setAllowOverride(true);
    ApprovalNode node = node("approve-1", "经理审批", candidate);
    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a"))
        .thenReturn(Collections.singletonList(node));

    Try<StartRequirements> result = resolver.getStartRequirements("flow-a", null);

    assertThat(result.isSuccess()).isTrue();
    StartRequirements requirements = result.get();
    assertThat(requirements.ccEnabled()).isTrue();
    assertThat(requirements.requirements()).singleElement().satisfies(req -> {
      assertThat(req.nodeId()).isEqualTo("approve-1");
      assertThat(req.label()).isEqualTo("经理审批");
      assertThat(req.mode()).isEqualTo("single");
      assertThat(req.min()).isEqualTo(1);
      assertThat(req.max()).isEqualTo(1);
      assertThat(req.candidateRange()).isEqualTo(CandidateRange.ROLE);
      assertThat(req.allowOverride()).isTrue();
    });
  }

  @Test
  void multipleCandidateHasNoUpperBound() {
    UserSelectCandidate candidate = new UserSelectCandidate();
    candidate.setMode(UserSelectCandidate.Mode.MULTIPLE);
    ApprovalNode node = node("approve-2", "会签", candidate);
    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a"))
        .thenReturn(Collections.singletonList(node));

    StartRequirements requirements = resolver.getStartRequirements("flow-a", null).get();

    assertThat(requirements.requirements()).singleElement().satisfies(req -> {
      assertThat(req.mode()).isEqualTo("multiple");
      assertThat(req.max()).isNull();
      assertThat(req.candidateRange()).isNull();
      assertThat(req.allowOverride()).isFalse();
    });
  }

  @Test
  void noSelectNodesReturnsEmptyRequirements() {
    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a"))
        .thenReturn(Collections.emptyList());

    StartRequirements requirements = resolver.getStartRequirements("flow-a", null).get();

    assertThat(requirements.requirements()).isEmpty();
    assertThat(requirements.ccEnabled()).isTrue();
  }

  @Test
  void conditionContextPathFallsBackToUnconditionalNodesWhenEmpty() {
    Map<String, Object> formData = new LinkedHashMap<>();
    formData.put("amount", 1000);
    ApprovalNode node = node("approve-1", "经理审批", singleCandidate());
    when(flowDefinitionService.getUserSelectCandidateApprovalNodes(eq("flow-a"), eq(formData)))
        .thenReturn(Collections.emptyList());
    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a"))
        .thenReturn(Collections.singletonList(node));

    StartRequirements requirements = resolver.getStartRequirements("flow-a", formData).get();

    assertThat(requirements.requirements()).singleElement()
        .satisfies(req -> assertThat(req.nodeId()).isEqualTo("approve-1"));
  }

  private UserSelectCandidate singleCandidate() {
    UserSelectCandidate candidate = new UserSelectCandidate();
    candidate.setMode(UserSelectCandidate.Mode.SINGLE);
    return candidate;
  }

  private ApprovalNode node(String id, String name, UserSelectCandidate candidate) {
    SingleApprovalNode node = new SingleApprovalNode();
    node.setId(id);
    node.setName(name);
    node.setCandidates(Collections.singletonList(candidate));
    return node;
  }
}
