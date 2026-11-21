package com.ouroboros.workflow.approval.activiti6.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.model.FlowDurationStatistic;
import com.ouroboros.workflow.approval.model.FlowInstanceDetail;
import com.ouroboros.workflow.approval.model.FlowInstanceStatistic;

@Service
public class FlowInstanceQueryService {
  private static final String INSTANCE_STATUS_RUNNING = "running";
  private static final String INSTANCE_STATUS_SUSPENDED = "suspended";
  private static final String INSTANCE_STATUS_FINISHED = "finished";
  private static final int RUNTIME_STATE_SCAN_BATCH_SIZE = 100;

  final RuntimeService runtimeService;
  final HistoryService historyService;
  final RepositoryService repositoryService;

  @Autowired
  public FlowInstanceQueryService(RuntimeService runtimeService, HistoryService historyService, RepositoryService repositoryService) {
    this.runtimeService = runtimeService;
    this.historyService = historyService;
    this.repositoryService = repositoryService;
  }

  public List<FlowInstanceStatistic> getStatistic(String keyword, String status, Integer offset, Integer limit) {
    var query = repositoryService.createProcessDefinitionQuery()
        .latestVersion()
        .processDefinitionCategoryLike(Constants.OUROBOROS_NAMESPACE + "/%");

    if (StringUtils.isNotEmpty(keyword)) {
      query.processDefinitionNameLike("%" + keyword + "%");
    }

    if (StringUtils.isNotEmpty(status)) {
      if ("deployed".equalsIgnoreCase(status)) {
        query.active();
      } else if ("offline".equalsIgnoreCase(status)) {
        query.suspended();
      }
    }
    var defineList = query.orderByProcessDefinitionKey()
        .asc()
        .listPage(offset, limit);
    return defineList.stream()
        .map(define -> {
          var subQuery = historyService.createHistoricProcessInstanceQuery().processDefinitionKey(define.getKey());
          var total = subQuery.count();
          var running = countRuntimeInstances(define.getKey(), false);
          var suspended = countRuntimeInstances(define.getKey(), true);
          var finished = total - running - suspended;
          var duration = getDurationStatistic(define.getKey());
          var statistic = new FlowInstanceStatistic();
          statistic.setCategory(define.getCategory().replace(Constants.OUROBOROS_NAMESPACE + "/", ""));
          statistic.setFlowKey(define.getKey());
          statistic.setDisplayName(define.getName());
          statistic.setDescription(define.getDescription());
          statistic.setTotal(total);
          statistic.setRunning(running);
          statistic.setSuspended(suspended);
          statistic.setFinished(finished);
          statistic.setAvgDurationMillis(duration.avgDurationMillis);
          statistic.setNodeCount(duration.nodeCount);
          statistic.setAvgNodeDurationMillis(duration.avgNodeDurationMillis);
          statistic.setHandledCount(duration.handledCount);
          statistic.setAvgHandleMillis(duration.avgHandleMillis);
          statistic.setStatus(define.isSuspended() ? "offline" : "deployed");
          return statistic;
        })
        .collect(Collectors.toList());
  }

  private DurationStatistic getDurationStatistic(String flowKey) {
    var finishedInstances = historyService.createHistoricProcessInstanceQuery()
        .processDefinitionKey(flowKey)
        .finished()
        .list();
    var tasks = historyService.createHistoricTaskInstanceQuery()
        .processDefinitionKey(flowKey)
        .finished()
        .list();

    long instanceCount = finishedInstances.stream()
        .filter(instance -> instance.getStartTime() != null && instance.getEndTime() != null)
        .count();
    long instanceDuration = finishedInstances.stream()
        .filter(instance -> instance.getStartTime() != null && instance.getEndTime() != null)
        .mapToLong(instance -> instance.getEndTime().getTime() - instance.getStartTime().getTime())
        .sum();

    long nodeCount = tasks.stream()
        .filter(task -> task.getStartTime() != null && task.getEndTime() != null)
        .count();
    long nodeDuration = tasks.stream()
        .filter(task -> task.getStartTime() != null && task.getEndTime() != null)
        .mapToLong(task -> task.getEndTime().getTime() - task.getStartTime().getTime())
        .sum();

    long handledCount = tasks.stream()
        .filter(task -> StringUtils.isNotBlank(task.getAssignee()))
        .filter(task -> task.getStartTime() != null && task.getEndTime() != null)
        .count();
    long handleDuration = tasks.stream()
        .filter(task -> StringUtils.isNotBlank(task.getAssignee()))
        .filter(task -> task.getStartTime() != null && task.getEndTime() != null)
        .mapToLong(task -> task.getEndTime().getTime() - task.getStartTime().getTime())
        .sum();

    var statistic = new DurationStatistic();
    statistic.avgDurationMillis = average(instanceDuration, instanceCount);
    statistic.nodeCount = nodeCount;
    statistic.avgNodeDurationMillis = average(nodeDuration, nodeCount);
    statistic.handledCount = handledCount;
    statistic.avgHandleMillis = average(handleDuration, handledCount);
    return statistic;
  }

  private long average(long total, long count) {
    if (count == 0) {
      return 0L;
    }
    return total / count;
  }

  private static class DurationStatistic {
    long avgDurationMillis;
    long nodeCount;
    long avgNodeDurationMillis;
    long handledCount;
    long avgHandleMillis;
  }

  public Long getStatisticCount(String keyword, String status) {
    var query = repositoryService.createProcessDefinitionQuery()
        .latestVersion()
        .processDefinitionCategoryLike(Constants.OUROBOROS_NAMESPACE + "/%");

    if (StringUtils.isNotEmpty(keyword)) {
      query.processDefinitionNameLike("%" + keyword + "%");
    }

    if (StringUtils.isNotEmpty(status)) {
      if ("deployed".equalsIgnoreCase(status)) {
        query.active();
      } else if ("offline".equalsIgnoreCase(status)) {
        query.suspended();
      }
    }
    return query.count();
  }

  public FlowDurationStatistic getDurationStatistic(String flowKey, Date from, Date to) {
    if (!processDefinitionExists(flowKey)) {
      throw new BusinessException("flow_not_found");
    }

    var finishedInstances = historyService.createHistoricProcessInstanceQuery()
        .processDefinitionKey(flowKey)
        .finished()
        .list()
        .stream()
        .filter(instance -> isWithinRange(instance.getStartTime(), instance.getEndTime(), from, to))
        .collect(Collectors.toList());

    var instanceIds = finishedInstances.stream()
        .map(HistoricProcessInstance::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    var tasks = instanceIds.isEmpty()
        ? Collections.<HistoricTaskInstance>emptyList()
        : historyService.createHistoricTaskInstanceQuery()
            .processDefinitionKey(flowKey)
            .finished()
            .list()
            .stream()
            .filter(task -> instanceIds.contains(task.getProcessInstanceId()))
            .filter(task -> isWithinRange(task.getStartTime(), task.getEndTime(), from, to))
            .collect(Collectors.toList());

    var statistic = new FlowDurationStatistic();
    statistic.setFlowKey(flowKey);
    statistic.setInstanceCount((long) finishedInstances.size());
    statistic.setAvgDurationMillis(averageDuration(finishedInstances));
    statistic.setNodes(buildNodeDurationStatistics(tasks));
    statistic.setApprovers(buildApproverDurationStatistics(tasks));
    return statistic;
  }

  public List<FlowInstanceDetail> getInstances(String flowKey, String status, Integer offset, Integer limit) {
    if (isRuntimeStatus(status)) {
      return getRuntimeStateInstances(flowKey, status, offset, limit);
    }

    var historicInstances = createHistoricProcessInstanceQuery(flowKey, status)
        .listPage(offset, limit);
    var runtimeStatuses = resolveRuntimeStatuses(historicInstances, status);
    return historicInstances
        .stream()
        .map(instance -> mapInstanceDetail(instance, flowKey, resolveHistoricStatus(instance, status, runtimeStatuses)))
        .collect(Collectors.toList());
  }

  public Long getInstancesCount(String flowKey, String status) {
    if (isRuntimeStatus(status)) {
      return countRuntimeStateInstances(flowKey, status);
    }

    return createHistoricProcessInstanceQuery(flowKey, status).count();
  }

  private long countRuntimeInstances(String flowKey, boolean suspended) {
    var runtimeQuery = runtimeService.createProcessInstanceQuery()
        .processDefinitionKey(flowKey);
    return suspended ? runtimeQuery.suspended().count() : runtimeQuery.active().count();
  }

  private boolean processDefinitionExists(String flowKey) {
    return repositoryService.createProcessDefinitionQuery()
        .processDefinitionKey(flowKey)
        .latestVersion()
        .singleResult() != null;
  }

  private boolean isWithinRange(Date startTime, Date endTime, Date from, Date to) {
    if (startTime == null || endTime == null) {
      return false;
    }
    if (from != null && startTime.before(from)) {
      return false;
    }
    return to == null || !endTime.after(to);
  }

  private long averageDuration(List<HistoricProcessInstance> instances) {
    long total = instances.stream()
        .mapToLong(instance -> instance.getEndTime().getTime() - instance.getStartTime().getTime())
        .sum();
    return average(total, instances.size());
  }

  private List<FlowDurationStatistic.NodeDurationStatistic> buildNodeDurationStatistics(List<HistoricTaskInstance> tasks) {
    Map<String, DurationAggregate> durationByNode = new LinkedHashMap<String, DurationAggregate>();
    Map<String, String> nameByNode = new HashMap<String, String>();
    for (var task : tasks) {
      var nodeId = task.getTaskDefinitionKey();
      if (StringUtils.isBlank(nodeId)) {
        continue;
      }
      durationByNode.computeIfAbsent(nodeId, key -> new DurationAggregate())
          .add(task.getEndTime().getTime() - task.getStartTime().getTime());
      nameByNode.putIfAbsent(nodeId, task.getName());
    }

    var result = new ArrayList<FlowDurationStatistic.NodeDurationStatistic>();
    durationByNode.forEach((nodeId, duration) -> {
      var nodeStatistic = new FlowDurationStatistic.NodeDurationStatistic();
      nodeStatistic.setNodeId(nodeId);
      nodeStatistic.setNodeName(nameByNode.get(nodeId));
      nodeStatistic.setTaskCount(duration.count);
      nodeStatistic.setAvgDurationMillis(average(duration.totalMillis, duration.count));
      result.add(nodeStatistic);
    });
    return result;
  }

  private List<FlowDurationStatistic.ApproverDurationStatistic> buildApproverDurationStatistics(List<HistoricTaskInstance> tasks) {
    Map<String, DurationAggregate> durationByApprover = new LinkedHashMap<String, DurationAggregate>();
    for (var task : tasks) {
      var userId = task.getAssignee();
      if (StringUtils.isBlank(userId)) {
        continue;
      }
      durationByApprover.computeIfAbsent(userId, key -> new DurationAggregate())
          .add(task.getEndTime().getTime() - task.getStartTime().getTime());
    }

    var result = new ArrayList<FlowDurationStatistic.ApproverDurationStatistic>();
    durationByApprover.forEach((userId, duration) -> {
      var approverStatistic = new FlowDurationStatistic.ApproverDurationStatistic();
      approverStatistic.setUserId(userId);
      approverStatistic.setUserName(userId);
      approverStatistic.setHandledCount(duration.count);
      approverStatistic.setAvgHandleMillis(average(duration.totalMillis, duration.count));
      result.add(approverStatistic);
    });
    return result;
  }

  private static class DurationAggregate {
    long count;
    long totalMillis;

    void add(long durationMillis) {
      count++;
      totalMillis += durationMillis;
    }
  }

  private HistoricProcessInstanceQuery createHistoricProcessInstanceQuery(String flowKey, String status) {
    var query = historyService.createHistoricProcessInstanceQuery()
        .processDefinitionKey(flowKey);

    if (StringUtils.isNotEmpty(status)) {
      if (INSTANCE_STATUS_RUNNING.equalsIgnoreCase(status)) {
        query = query.unfinished();
      } else if (INSTANCE_STATUS_FINISHED.equalsIgnoreCase(status)) {
        query = query.finished();
      }
    }
    return query.orderByProcessInstanceStartTime().desc();
  }

  private List<FlowInstanceDetail> getRuntimeStateInstances(String flowKey, String status, Integer offset, Integer limit) {
    int remainingSkip = offset == null ? 0 : Math.max(0, offset);
    if (limit != null && limit <= 0) {
      return Collections.emptyList();
    }

    long remaining = limit == null ? Long.MAX_VALUE : limit.longValue();
    int batchSize = limit == null ? RUNTIME_STATE_SCAN_BATCH_SIZE : Math.max(RUNTIME_STATE_SCAN_BATCH_SIZE, limit);
    int historyOffset = 0;
    String normalizedStatus = normalizeInstanceStatus(status);
    var historicQuery = createHistoricProcessInstanceQuery(flowKey, INSTANCE_STATUS_RUNNING);
    var details = new ArrayList<FlowInstanceDetail>();

    while (remaining > 0) {
      var historicInstances = historicQuery.listPage(historyOffset, batchSize);
      if (historicInstances.isEmpty()) {
        break;
      }

      var runtimeInstanceIds = listRuntimeInstanceIds(flowKey, status, historicInstances);
      for (var instance : historicInstances) {
        if (!runtimeInstanceIds.contains(instance.getId())) {
          continue;
        }
        if (remainingSkip > 0) {
          remainingSkip--;
          continue;
        }
        details.add(mapInstanceDetail(instance, flowKey, normalizedStatus));
        remaining--;
        if (remaining == 0) {
          return details;
        }
      }

      historyOffset += historicInstances.size();
      if (historicInstances.size() < batchSize) {
        break;
      }
    }

    return details;
  }

  private Long countRuntimeStateInstances(String flowKey, String status) {
    var runtimeQuery = runtimeService.createProcessInstanceQuery()
        .processDefinitionKey(flowKey);
    return isSuspendedStatus(status)
        ? runtimeQuery.suspended().count()
        : runtimeQuery.active().count();
  }

  private Set<String> listRuntimeInstanceIds(String flowKey, String status, List<HistoricProcessInstance> historicInstances) {
    if (historicInstances.isEmpty()) {
      return Collections.emptySet();
    }

    var runtimeQuery = runtimeService.createProcessInstanceQuery()
        .processDefinitionKey(flowKey);

    runtimeQuery = isSuspendedStatus(status)
        ? runtimeQuery.suspended()
        : runtimeQuery.active();

    Set<String> instanceIds = historicInstances.stream()
        .map(HistoricProcessInstance::getId)
        .collect(Collectors.toSet());
    var runtimeInstances = runtimeQuery.processInstanceIds(instanceIds).list();
    return runtimeInstances.stream()
        .map(ProcessInstance::getId)
        .collect(Collectors.toSet());
  }

  private FlowInstanceDetail mapInstanceDetail(HistoricProcessInstance instance, String flowKey, String status) {
    var instanceId = instance.getId();
    var latestTask = historyService.createHistoricTaskInstanceQuery()
        .processInstanceId(instanceId)
        .orderByHistoricTaskInstanceStartTime()
        .desc()
        .listPage(0, 1)
        .stream()
        .findFirst()
        .orElse(null);
    var starter = Optional.ofNullable(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instanceId)
            .variableName(Constants.Variable.PROCESS_STARTER)
            .singleResult())
        .map(HistoricVariableInstance::getValue)
        .map(String::valueOf)
        .orElse(null);
    var outcome = Optional.ofNullable(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(instanceId)
            .variableName(Constants.Variable.PROCESS_OUTCOME)
            .singleResult())
        .map(HistoricVariableInstance::getValue)
        .map(String::valueOf)
        .orElse(null);

    var detail = new FlowInstanceDetail();
    detail.setInstanceId(instanceId);
    detail.setFlowKey(flowKey);
    detail.setStarter(starter);
    detail.setStartTime(Optional.ofNullable(instance.getStartTime()).map(Dates::toLocalDateTime).orElse(null));
    detail.setEndTime(Optional.ofNullable(instance.getEndTime()).map(Dates::toLocalDateTime).orElse(null));
    detail.setStatus(status);
    detail.setOutcome(outcome);
    detail.setTaskId(Optional.ofNullable(latestTask).map(HistoricTaskInstance::getId).orElse(null));
    detail.setTaskName(Optional.ofNullable(latestTask).map(HistoricTaskInstance::getName).orElse(null));
    return detail;
  }

  private boolean isRuntimeStatus(String status) {
    return INSTANCE_STATUS_RUNNING.equalsIgnoreCase(status)
        || INSTANCE_STATUS_SUSPENDED.equalsIgnoreCase(status);
  }

  private boolean isSuspendedStatus(String status) {
    return INSTANCE_STATUS_SUSPENDED.equalsIgnoreCase(status);
  }

  private Map<String, String> resolveRuntimeStatuses(List<HistoricProcessInstance> historicInstances, String requestedStatus) {
    if (StringUtils.isNotBlank(requestedStatus) || historicInstances.isEmpty()) {
      return Collections.emptyMap();
    }

    Set<String> unfinishedIds = historicInstances.stream()
        .filter(instance -> instance.getEndTime() == null)
        .map(HistoricProcessInstance::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    if (unfinishedIds.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, String> statuses = new HashMap<String, String>();
    runtimeService.createProcessInstanceQuery()
        .processInstanceIds(unfinishedIds)
        .active()
        .list()
        .forEach(instance -> statuses.put(instance.getId(), INSTANCE_STATUS_RUNNING));
    runtimeService.createProcessInstanceQuery()
        .processInstanceIds(unfinishedIds)
        .suspended()
        .list()
        .forEach(instance -> statuses.put(instance.getId(), INSTANCE_STATUS_SUSPENDED));
    return statuses;
  }

  private String resolveHistoricStatus(HistoricProcessInstance instance, String requestedStatus, Map<String, String> runtimeStatuses) {
    if (INSTANCE_STATUS_FINISHED.equalsIgnoreCase(requestedStatus)) {
      return INSTANCE_STATUS_FINISHED;
    }
    if (INSTANCE_STATUS_RUNNING.equalsIgnoreCase(requestedStatus)) {
      return INSTANCE_STATUS_RUNNING;
    }
    if (INSTANCE_STATUS_SUSPENDED.equalsIgnoreCase(requestedStatus)) {
      return INSTANCE_STATUS_SUSPENDED;
    }
    var runtimeStatus = runtimeStatuses.get(instance.getId());
    if (runtimeStatus != null) {
      return runtimeStatus;
    }
    return instance.getEndTime() == null ? INSTANCE_STATUS_RUNNING : INSTANCE_STATUS_FINISHED;
  }

  private String normalizeInstanceStatus(String status) {
    return isSuspendedStatus(status) ? INSTANCE_STATUS_SUSPENDED : INSTANCE_STATUS_RUNNING;
  }
}
