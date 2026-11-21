package com.ouroboros.workflow.approval.external.runtime.startrequirements;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.definition.startrequirements.CandidateRange;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirements;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;

import io.vavr.control.Try;

/**
 * M22-T4 外部发起需求解析测试：startOptionsJson 声明解析、allowOverride=false 剔除、无声明空、非法 JSON 失败。
 */
class ExternalStartRequirementsParserTest {

  private ExternalStartRequirementsParser parser;

  @BeforeEach
  void setUp() {
    parser = new ExternalStartRequirementsParser();
  }

  @Test
  void declaredStartOptionsAreParsedIntoRequirements() {
    WorkflowEngineMapping mapping = mapping("m-1",
        "{\"requirements\":[{\"nodeId\":\"approve-1\",\"label\":\"部门审批\",\"mode\":\"single\","
            + "\"min\":1,\"max\":1,\"candidateRange\":\"DEPARTMENT\",\"allowOverride\":true}],\"ccEnabled\":true}");

    Try<StartRequirements> result = parser.parse(mapping, Map.of());

    assertThat(result.isSuccess()).isTrue();
    StartRequirements requirements = result.get();
    assertThat(requirements.ccEnabled()).isTrue();
    assertThat(requirements.requirements()).singleElement().satisfies(req -> {
      assertThat(req.nodeId()).isEqualTo("approve-1");
      assertThat(req.label()).isEqualTo("部门审批");
      assertThat(req.mode()).isEqualTo("single");
      assertThat(req.min()).isEqualTo(1);
      assertThat(req.max()).isEqualTo(1);
      assertThat(req.candidateRange()).isEqualTo(CandidateRange.DEPARTMENT);
      assertThat(req.allowOverride()).isTrue();
    });
  }

  @Test
  void nonOverridableRequirementsAreFilteredOut() {
    WorkflowEngineMapping mapping = mapping("m-1",
        "{\"requirements\":[{\"nodeId\":\"fixed-1\",\"label\":\"固定审批\",\"mode\":\"single\",\"allowOverride\":false},"
            + "{\"nodeId\":\"pick-1\",\"label\":\"自选\",\"mode\":\"multiple\",\"allowOverride\":true}],\"ccEnabled\":false}");

    Try<StartRequirements> result = parser.parse(mapping, Map.of());

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().requirements()).singleElement()
        .satisfies(req -> assertThat(req.nodeId()).isEqualTo("pick-1"));
  }

  @Test
  void missingStartOptionsReturnsEmptyRequirements() {
    WorkflowEngineMapping mapping = mapping("m-1", null);

    Try<StartRequirements> result = parser.parse(mapping, Map.of());

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().requirements()).isEmpty();
    assertThat(result.get().ccEnabled()).isFalse();
  }

  @Test
  void malformedStartOptionsFails() {
    WorkflowEngineMapping mapping = mapping("m-1", "{not-json");

    Try<StartRequirements> result = parser.parse(mapping, Map.of());

    assertThat(result.isFailure()).isTrue();
    assertThat(result.getCause()).isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(ex.getMessage()).contains("startOptionsJson 解析失败"));
  }

  private WorkflowEngineMapping mapping(String id, String startOptionsJson) {
    WorkflowEngineMapping mapping = new WorkflowEngineMapping();
    mapping.setId(id);
    mapping.setStartOptionsJson(startOptionsJson);
    return mapping;
  }
}
