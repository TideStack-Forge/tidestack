package com.ouroboros.workflow.approval.external.runtime.startrequirements;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.definition.startrequirements.Requirement;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirements;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMapping;

import io.vavr.control.Try;

/**
 * 外部引擎发起需求解析（Phase 4 M22-T4）。
 *
 * <p>数据来源（设计 L659）：映射的 {@code startOptionsJson} 声明（平台管理员声明，JSON 结构
 * {@code {"requirements":[{"nodeId","label","mode","min","max","candidateRange","allowOverride"}],"ccEnabled":true}}），
 * 解析后按 L661 语义剔除 {@code allowOverride=false} 的节点（外部不允许覆盖时不返回该项，前端不渲染选择器）。
 * 无声明时：外部定义自选节点解析待 M25 真实 schema 联调复核，当前返回空 requirements（禁止猜测）。
 */
@Component
public class ExternalStartRequirementsParser {

  private static final Logger logger = LoggerFactory.getLogger(ExternalStartRequirementsParser.class);

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * 解析映射的发起需求；无声明返回空 requirements（ccEnabled=false）。
   */
  public Try<StartRequirements> parse(WorkflowEngineMapping mapping, Map<String, Object> formData) {
    String startOptionsJson = mapping.getStartOptionsJson();
    if (startOptionsJson == null || startOptionsJson.isBlank()) {
      logger.debug("外部发起需求：无 startOptions 声明且定义解析未接入（M25 复核），返回空 requirements: mappingId={}",
          mapping.getId());
      return Try.success(StartRequirements.none(false));
    }
    try {
      StartRequirements declared = objectMapper.readValue(startOptionsJson, StartRequirements.class);
      return Try.success(filterNonOverridable(declared));
    } catch (Exception ex) {
      return Try.failure(new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
              + "] startOptionsJson 解析失败: " + mapping.getId(), ex));
    }
  }

  private StartRequirements filterNonOverridable(StartRequirements requirements) {
    List<Requirement> filtered = requirements.requirements().stream()
        .filter(Requirement::allowOverride)
        .toList();
    return new StartRequirements(filtered, requirements.ccEnabled());
  }
}
