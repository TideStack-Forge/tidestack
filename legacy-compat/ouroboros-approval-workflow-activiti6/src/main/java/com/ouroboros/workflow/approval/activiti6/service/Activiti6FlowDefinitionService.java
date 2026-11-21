package com.ouroboros.workflow.approval.activiti6.service;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.readFlowDefinition;
import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.readStructuredFlowDefinition;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.activiti.engine.RepositoryService;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.impl.persistence.entity.TaskEntityImpl;
import org.activiti.spring.SpringProcessEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.Converter;
import com.ouroboros.workflow.approval.BuiltInFlowDefinitionService;
import com.ouroboros.workflow.approval.definition.FlowDefinition;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.condition.Condition;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.candidates.UserSelectCandidate;
import com.ouroboros.workflow.approval.definition.codec.FlowDefinitionV2Normalizer;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;
import com.ouroboros.workflow.approval.model.FlowDefinitionVersion;
import com.ouroboros.workflow.approval.util.StructuredFlowRuntimeNavigator;
import com.ouroboros.workflow.approval.activiti6.util.BpmnUtil;

@Service
public class Activiti6FlowDefinitionService implements BuiltInFlowDefinitionService {
  final static Logger logger = LoggerFactory.getLogger(Activiti6FlowDefinitionService.class);
  final StructuredFlowRuntimeNavigator runtimeNavigator = new StructuredFlowRuntimeNavigator();
  final FlowDefinitionV2Normalizer flowDefinitionV2Normalizer = new FlowDefinitionV2Normalizer();

  @Autowired
  RepositoryService repositoryService;
  @Autowired
  SpringProcessEngineConfiguration processEngineConfiguration;

  @Override
  public Try<FlowDefinition> getFlowDefinition(String flowKey) {
    var definitionTry = Try.of(() -> repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey(flowKey)
        .latestVersion()
        .singleResult());
    return definitionTry
        .mapTry(definition -> {
          if (definition == null) {
            throw new PlatformException("流程定义不存在");
          }
          var model = repositoryService.getBpmnModel(definition.getId());
          return readFlowDefinition(model);
        });
  }

  @Override
  public List<FlowNode> getFlowNodes(String flowKey) {
    var definitionTry = getStructuredFlowDefinition(flowKey);
    if (definitionTry.isFailure()) {
      logger.error("获取流程定义失败", definitionTry.getCause());
      return Collections.emptyList();
    }
    return runtimeNavigator.getFlowNodes(definitionTry.get());
  }

  @Override
  public Try<JsonNode> getFlowDefinitionV2(String flowKey, Integer version) {
    var definitionTry = Try.of(() -> repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey(flowKey)
        .processDefinitionVersion(version)
        .singleResult());
    return definitionTry
        .mapTry(definition -> {
          if (definition == null) {
            throw new PlatformException("流程定义不存在");
          }
          var model = repositoryService.getBpmnModel(definition.getId());
          return BpmnUtil.readFlowDefinitionRawJson(model);
        })
        .flatMap(flowDefinitionV2Normalizer::normalizeToJsonNode)
        .mapTry(definition -> {
          if (definition == null) {
            throw new PlatformException("流程定义不存在");
          }
          return definition;
        });
  }

  // 获取所有用户选择审批候选人的审批节点
  @Override
  public List<ApprovalNode> getUserSelectCandidateApprovalNodes(String flowKey) {
    var definitionTry = getStructuredFlowDefinition(flowKey);
    if (definitionTry.isFailure()) {
      logger.error("获取流程定义(flowKey:{})失败", flowKey, definitionTry.getCause());
      return Collections.emptyList();
    }
    return runtimeNavigator.getUserSelectCandidateApprovalNodes(definitionTry.get());
  }

  // 根据条件获取所有用户选择审批候选人的审批节点
  @Override
  public List<ApprovalNode> getUserSelectCandidateApprovalNodes(String flowKey, Map<String, Object> conditionContext) {
    var definitionTry = getStructuredFlowDefinition(flowKey);
    if (definitionTry.isFailure()) {
      logger.error("获取流程定义(flowKey:{})失败", flowKey, definitionTry.getCause());
      return Collections.emptyList();
    }
    return runtimeNavigator.getUserSelectCandidateApprovalNodes(definitionTry.get(), getBranchEdgeFilter(conditionContext));
  }

  @Override
  public Try<List<FlowDefinitionVersion>> listFlowDefinitionVersions(String flowKey) {
    return Try.of(() -> repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(flowKey)
            .orderByProcessDefinitionVersion()
            .desc()
            .list())
        .map(definitions -> definitions.stream()
            .map(definition -> {
              var deployment = repositoryService.createDeploymentQuery()
                  .deploymentId(definition.getDeploymentId())
                  .singleResult();
              FlowDefinitionVersion version = new FlowDefinitionVersion();
              version.setVersion(definition.getVersion());
              version.setProcessDefinitionId(definition.getId());
              version.setDeploymentId(definition.getDeploymentId());
              version.setDeploymentTime(toLocalDateTime(Optional.ofNullable(deployment)
                  .map(org.activiti.engine.repository.Deployment::getDeploymentTime)
                  .orElse(null)));
              version.setDeployedBy(Optional.ofNullable(deployment)
                  .map(org.activiti.engine.repository.Deployment::getTenantId)
                  .orElse(""));
              return version;
            })
            .collect(Collectors.toList()));
  }

  @Override
  public Try<FlowDefinitionVersion> getFlowDefinitionVersion(String flowKey, Integer version) {
    return Try.of(() -> repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(flowKey)
            .processDefinitionVersion(version)
            .singleResult())
        .mapTry(definition -> {
          if (definition == null) {
            throw new PlatformException("流程定义版本不存在");
          }
          var deployment = repositoryService.createDeploymentQuery()
              .deploymentId(definition.getDeploymentId())
              .singleResult();
          FlowDefinitionVersion result = new FlowDefinitionVersion();
          result.setVersion(definition.getVersion());
          result.setProcessDefinitionId(definition.getId());
          result.setDeploymentId(definition.getDeploymentId());
          result.setDeploymentTime(toLocalDateTime(Optional.ofNullable(deployment)
              .map(org.activiti.engine.repository.Deployment::getDeploymentTime)
              .orElse(null)));
          result.setDeployedBy(Optional.ofNullable(deployment)
              .map(org.activiti.engine.repository.Deployment::getTenantId)
              .orElse(""));
          result.setDefinition(BpmnUtil.readFlowDefinitionRawJson(repositoryService.getBpmnModel(definition.getId())));
          return result;
        });
  }

  // 传入条件上下文，返回根据分支条件进行过滤的过滤器
  private Predicate<FlowEdgeV2> getBranchEdgeFilter(Map<String, Object> conditionContext) {
    var variableScope = new TaskEntityImpl();
    variableScope.setExecutionVariables(conditionContext);

    // 根据分支条件计算是否走这条分支
    return edge -> {

      // 把结果包裹在Try中，避免抛出异常，遇到异常时记录错误信息
      var result = Try.of(() -> {
            var expression = createExpression(getConditionExpression(edge.getCondition()));
            return expression.getValue(variableScope);
          })
          .map(Converter::toBoolean)
          .onFailure(e -> logger.error("表达式解析失败", e));

      // 如果有异常，则返回false
      return result.getOrElse(false);
    };
  }

  private String getConditionExpression(Condition condition) {
    return Optional.ofNullable(condition)
        .map(Condition::toExpression)
        .orElse("true");
  }

  private Try<StructuredFlowDefinition> getStructuredFlowDefinition(String flowKey) {
    var definitionTry = Try.of(() -> repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey(flowKey)
        .latestVersion()
        .singleResult());
    return definitionTry
        .mapTry(definition -> {
          if (definition == null) {
            throw new PlatformException("流程定义不存在");
          }
          var model = repositoryService.getBpmnModel(definition.getId());
          return readStructuredFlowDefinition(model);
        })
        .mapTry(definition -> {
          if (definition == null) {
            throw new PlatformException("流程定义不存在");
          }
          return definition;
        });
  }

  // 用流程引擎的表达式引擎创建表达式
  private Expression createExpression(String expr) {
    var expressionManager = processEngineConfiguration.getExpressionManager();
    return expressionManager.createExpression(expr);
  }

  private LocalDateTime toLocalDateTime(java.util.Date value) {
    if (value == null) {
      return null;
    }
    return LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
  }

}
