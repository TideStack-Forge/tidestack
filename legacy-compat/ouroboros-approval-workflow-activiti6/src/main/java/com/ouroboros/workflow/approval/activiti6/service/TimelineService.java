package com.ouroboros.workflow.approval.activiti6.service;

import static com.ouroboros.workflow.approval.activiti6.util.BpmnUtil.readFlowDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.activiti.bpmn.model.Process;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.task.Comment;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;

import com.ouroboros.util.Converter;
import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.activiti6.transpiler.Constants;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.candidates.UserCandidate;
import com.ouroboros.workflow.approval.definition.candidates.UserSelectCandidate;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.definition.nodes.MultipleApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.model.TaskState;

@Service
public class TimelineService {
  final TaskService taskService;
  final RuntimeService runtimeService;
  final HistoryService historyService;
  final RepositoryService repositoryService;

  @Autowired
  public TimelineService(TaskService taskService,
                         RuntimeService runtimeService,
                         HistoryService historyService,
                         RepositoryService repositoryService) {
    this.taskService = taskService;
    this.runtimeService = runtimeService;
    this.historyService = historyService;
    this.repositoryService = repositoryService;
  }

  public List<FlowTimeline> getTimeLine(String instanceId) {
    var contextOptional = prepareTimelineContext(instanceId);
    if (!contextOptional.isPresent()) {
      return Collections.emptyList();
    }

    var context = contextOptional.get();
    var variables = loadHistoricVariables(instanceId);
    var userSelectedAssignees = parseUserSelectedAssignees(variables);

    var createdResult = buildCreatedTimelineNodes(instanceId, context.suspended, context.flowNodeMap, context.logs, variables);
    appendUncreatedTasksForMultipleSessions(instanceId, createdResult.multipleSessions, createdResult.multipleSessionAssignees);
    var unCreatedList = buildUncreatedNodeTimelines(context.flowNodeList, createdResult.taskDefinitionIds, userSelectedAssignees);
    normalizeTerminalMultipleSessionTasks(createdResult.multipleSessions);

    return mergeTimeline(createdResult.createdTaskNodes, unCreatedList);
  }

  private Optional<TimelineBuildContext> prepareTimelineContext(String instanceId) {
    var instanceTry = getHistoricInstance(instanceId);
    if (instanceTry.isFailure()) {
      return Optional.empty();
    }
    var instance = instanceTry.get();
    var suspended = isRuntimeInstanceSuspended(instanceId);
    var process = getProcess(instance);
    var flowDefinition = readFlowDefinition(process);
    if (flowDefinition == null || (ObjectUtils.isEmpty(flowDefinition.getNodes()) && ObjectUtils.isEmpty(flowDefinition.getTreeGraphNodes()))) {
      return Optional.empty();
    }

    var flowNodeList = ObjectUtils.isNotEmpty(flowDefinition.getNodes())
        ? flowDefinition.getNodes().stream()
            .filter(node -> node instanceof StartNode || node instanceof ApprovalNode || node instanceof HandleNode)
            .collect(Collectors.toCollection(ArrayList::new))
        : flowDefinition.getTreeGraphNodes().stream()
            .map(com.ouroboros.workflow.approval.util.TreeGraphNode::getFlowNode)
            .filter(node -> node instanceof StartNode || node instanceof ApprovalNode || node instanceof HandleNode)
            .collect(Collectors.toCollection(ArrayList::new));

    Map<String, FlowNode> flowNodeMap = flowNodeList.stream()
        .collect(LinkedHashMap::new, (map, info) -> map.put(info.getId(), info), LinkedHashMap::putAll);

    var logs = historyService.createHistoricTaskInstanceQuery()
        .processInstanceId(instanceId)
        .orderByHistoricTaskInstanceStartTime()
        .asc()
        .list()
        .stream()
        .filter(task -> flowNodeMap.containsKey(task.getTaskDefinitionKey()))
        .collect(Collectors.toList());
    return Optional.of(new TimelineBuildContext(suspended, flowNodeList, flowNodeMap, logs));
  }

  private List<HistoricVariableInstance> loadHistoricVariables(String instanceId) {
    return historyService.createHistoricVariableInstanceQuery()
        .processInstanceId(instanceId)
        .list();
  }

  private Map<String, List<String>> parseUserSelectedAssignees(List<HistoricVariableInstance> variables) {
    return variables.stream()
        .filter(v -> v.getVariableName().equals(Constants.Variable.USER_SELECTED_ASSIGNEES))
        .findFirst()
        .map(HistoricVariableInstance::getValue)
        .map(v -> {
          if (v instanceof Map<?, ?>) {
            return (Map<String, List<String>>) v;
          }
          return Collections.<String, List<String>>emptyMap();
        })
        .orElseGet(Collections::emptyMap);
  }

  private CreatedTimelineBuildResult buildCreatedTimelineNodes(String instanceId,
                                                               boolean suspended,
                                                               Map<String, FlowNode> flowNodeMap,
                                                               List<HistoricTaskInstance> logs,
                                                               List<HistoricVariableInstance> variables) {
    var taskDefinitionIds = logs.stream()
        .map(HistoricTaskInstance::getTaskDefinitionKey)
        .collect(Collectors.toSet());
    var multipleSessions = new HashMap<String, FlowTimeline>();
    var multipleSessionAssignees = new HashMap<String, List<String>>();

    var createdTaskNodes = logs.stream()
        .map(task -> {
          var flowNode = flowNodeMap.get(task.getTaskDefinitionKey());
          var taskInstance = buildTaskInstance(task, suspended);
          if (!(flowNode instanceof MultipleApprovalNode)) {
            return buildSingleTaskTimeline(flowNode, taskInstance);
          }
          return buildOrAppendMultipleApprovalTimeline(
              instanceId,
              task,
              flowNode,
              taskInstance,
              variables,
              multipleSessions,
              multipleSessionAssignees,
              suspended);
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    return new CreatedTimelineBuildResult(createdTaskNodes, taskDefinitionIds, multipleSessions, multipleSessionAssignees);
  }

  private FlowTimeline buildSingleTaskTimeline(FlowNode flowNode, FlowTimeline.TaskInstance taskInstance) {
    var timeline = new FlowTimeline();
    timeline.setFlowNode(flowNode);
    timeline.setTaskInstances(Collections.singletonList(taskInstance));
    timeline.setState(taskInstance.getState());
    return timeline;
  }

  private FlowTimeline buildOrAppendMultipleApprovalTimeline(String instanceId,
                                                              HistoricTaskInstance task,
                                                              FlowNode flowNode,
                                                              FlowTimeline.TaskInstance taskInstance,
                                                              List<HistoricVariableInstance> variables,
                                                              Map<String, FlowTimeline> multipleSessions,
                                                              Map<String, List<String>> multipleSessionAssignees,
                                                              boolean suspended) {
    var session = getMultipleApprovalSession(task, variables);
    var sessionTimeline = multipleSessions.getOrDefault(session, new FlowTimeline());
    if (ObjectUtils.isNotEmpty(sessionTimeline.getTaskInstances())) {
      sessionTimeline.getTaskInstances().add(taskInstance);
      return null;
    }

    sessionTimeline.setTaskInstances(new ArrayList<>());
    sessionTimeline.setFlowNode(flowNode);
    sessionTimeline.setState(getMultipleTaskStatus(session, variables, suspended));
    sessionTimeline.getTaskInstances().add(taskInstance);
    multipleSessions.put(session, sessionTimeline);

    List<String> approvalAssignees = variables
        .stream()
        .filter(v -> v.getVariableName().equals(Constants.Variable.MULTIPLE_APPROVAL_SESSION + session + Constants.Variable.MULTIPLE_APPROVAL_ASSIGNEE_LIST))
        .map(HistoricVariableInstance::getValue)
        .map(v -> {
          if (v instanceof List<?>) {
            return (List<String>) v;
          }
          return Collections.<String>emptyList();
        })
        .findFirst()
        .orElseGet(Collections::emptyList);
    multipleSessionAssignees.put(session, approvalAssignees);
    return sessionTimeline;
  }

  private void appendUncreatedTasksForMultipleSessions(String instanceId,
                                                       Map<String, FlowTimeline> multipleSessions,
                                                       Map<String, List<String>> multipleSessionAssignees) {
    multipleSessions.forEach((session, timeLine) -> {
      var taskInstances = timeLine.getTaskInstances();
      var assignees = multipleSessionAssignees.get(session);
      var unCreatedTaskInstances = assignees.stream()
          .filter(assignee -> taskInstances.stream().noneMatch(task -> assignee.equals(task.getAssignee())))
          .map(assignee -> {
            var taskInstance = new FlowTimeline.TaskInstance();
            taskInstance.setInstanceId(instanceId);
            taskInstance.setAssignee(assignee);
            taskInstance.setState(TaskState.UNCREATED);
            return taskInstance;
          })
          .collect(Collectors.toList());
      taskInstances.addAll(unCreatedTaskInstances);
    });
  }

  private List<FlowTimeline> buildUncreatedNodeTimelines(List<FlowNode> flowNodeList,
                                                         Set<String> createdTaskDefinitionIds,
                                                         Map<String, List<String>> userSelectedAssignees) {
    return flowNodeList.stream()
        .filter(x -> !createdTaskDefinitionIds.contains(x.getId()))
        .map(this::mapUnCreatedNodeToTimeline)
        .peek(timeline -> enrichUserSelectCandidates(timeline, userSelectedAssignees))
        .collect(Collectors.toList());
  }

  private void enrichUserSelectCandidates(FlowTimeline timeline, Map<String, List<String>> userSelectedAssignees) {
    var node = timeline.getFlowNode();
    if (node.getCandidates().stream().noneMatch(UserSelectCandidate.class::isInstance)) {
      return;
    }

    var candidates = userSelectedAssignees.getOrDefault(timeline.getFlowNode().getId(), Collections.emptyList());
    var userCandidate = new UserCandidate();
    userCandidate.setUsers(candidates);
    var merged = Stream.concat(Stream.of(userCandidate), node.getCandidates().stream().filter(x -> !(x instanceof UserSelectCandidate)))
        .collect(Collectors.toList());
    node.setCandidates(merged);
  }

  private void normalizeTerminalMultipleSessionTasks(Map<String, FlowTimeline> multipleSessions) {
    multipleSessions.forEach((session, timeLine) -> {
      var timeLineState = timeLine.getState();
      if (timeLineState != TaskState.APPROVED && timeLineState != TaskState.REJECTED) {
        return;
      }
      timeLine.getTaskInstances().stream()
          .filter(x -> x.getState() == TaskState.UNCREATED || x.getState() == TaskState.CREATED)
          .forEach(x -> x.setState(TaskState.CANCELLED));
    });
  }

  private List<FlowTimeline> mergeTimeline(List<FlowTimeline> createdTaskNodes, List<FlowTimeline> unCreatedList) {
    var timeLine = new ArrayList<FlowTimeline>();
    timeLine.addAll(createdTaskNodes);
    timeLine.addAll(unCreatedList);
    return timeLine;
  }

  private static final class TimelineBuildContext {
    private final boolean suspended;
    private final List<FlowNode> flowNodeList;
    private final Map<String, FlowNode> flowNodeMap;
    private final List<HistoricTaskInstance> logs;

    private TimelineBuildContext(boolean suspended,
                                 List<FlowNode> flowNodeList,
                                 Map<String, FlowNode> flowNodeMap,
                                 List<HistoricTaskInstance> logs) {
      this.suspended = suspended;
      this.flowNodeList = flowNodeList;
      this.flowNodeMap = flowNodeMap;
      this.logs = logs;
    }
  }

  private static final class CreatedTimelineBuildResult {
    private final List<FlowTimeline> createdTaskNodes;
    private final Set<String> taskDefinitionIds;
    private final Map<String, FlowTimeline> multipleSessions;
    private final Map<String, List<String>> multipleSessionAssignees;

    private CreatedTimelineBuildResult(List<FlowTimeline> createdTaskNodes,
                                       Set<String> taskDefinitionIds,
                                       Map<String, FlowTimeline> multipleSessions,
                                       Map<String, List<String>> multipleSessionAssignees) {
      this.createdTaskNodes = createdTaskNodes;
      this.taskDefinitionIds = taskDefinitionIds;
      this.multipleSessions = multipleSessions;
      this.multipleSessionAssignees = multipleSessionAssignees;
    }
  }

  private FlowTimeline.TaskInstance buildTaskInstance(HistoricTaskInstance task, boolean suspended) {
    var taskInstance = new FlowTimeline.TaskInstance();
    var assignee = task.getAssignee();
    var state = getTaskState(task, suspended);
    taskInstance.setTaskId(task.getId());
    taskInstance.setState(state);
    taskInstance.setAssignee(assignee);
    taskInstance.setInstanceId(task.getProcessInstanceId());
    if (task.getStartTime() != null) {
      taskInstance.setStartTime(Dates.toLocalDateTime(task.getStartTime()));
    }
    if (task.getEndTime() != null) {
      taskInstance.setEndTime(Dates.toLocalDateTime(task.getEndTime()));
    }

    var comment = taskInstance.getState() == TaskState.UNCREATED
        ? null
        : taskService.getProcessInstanceComments(task.getProcessInstanceId())
        .stream()
        .filter(c -> task.getId().equals(c.getTaskId()))
        .max(Comparator.comparing(Comment::getTime, Comparator.nullsFirst(Date::compareTo)))
        .map(Comment::getFullMessage)
        .orElse(null);
    taskInstance.setComment(comment);

    // 候选人信息
    if (Arrays.asList(TaskState.CREATED, TaskState.PROCESSING, TaskState.SUSPENDED).contains(state)) {
      // 一般来说，历史任务中的候选人信息在任务的全生命周期中都能取到
      var candidateUsers = Try.of(() -> historyService.getHistoricIdentityLinksForTask(task.getId()).stream().map(l -> Tuple.of(l.getType(), l.getUserId())))
          .orElse(() -> Try.of(() -> taskService.getIdentityLinksForTask(task.getId()).stream().map(l -> Tuple.of(l.getType(), l.getUserId()))))
          .getOrElse(Stream::empty)
          .filter(x -> "candidate".equalsIgnoreCase(x._1))
          .map(Tuple2::_2)
          .collect(Collectors.toList());
      if (assignee != null) {
        candidateUsers.add(assignee);
      }
      taskInstance.setCandidateUsers(candidateUsers);
    }

    return taskInstance;
  }

  private FlowTimeline mapUnCreatedNodeToTimeline(FlowNode node) {
    var timeLineItem = new FlowTimeline();
    timeLineItem.setFlowNode(node);
    timeLineItem.setTaskInstances(Collections.emptyList());
    timeLineItem.setState(TaskState.UNCREATED);

    return timeLineItem;
  }

  private TaskState getTaskState(HistoricTaskInstance task, boolean suspended) {
    if (task.getEndTime() == null) {
      if (suspended) {
        return TaskState.SUSPENDED;
      }
      return StringUtils.isNotBlank(task.getAssignee()) ? TaskState.PROCESSING : TaskState.CREATED;
    }
    var approved = Optional.ofNullable(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(task.getProcessInstanceId())
            .taskId(task.getId())
            .variableName(Constants.Variable.TASK_APPROVED)
            .singleResult())
        .map(HistoricVariableInstance::getValue)
        .map(Converter::toBoolean)
        .orElse(null);
    if (approved == null) {
      return isCompleteAction(task) ? TaskState.HANDLED : TaskState.CREATED;
    }
    return approved ? TaskState.APPROVED : TaskState.REJECTED;
  }

  private boolean isCompleteAction(HistoricTaskInstance task) {
    return Optional.ofNullable(historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(task.getProcessInstanceId())
            .taskId(task.getId())
            .variableName(Constants.Variable.LOCAL_APPROVAL_ACTION)
            .singleResult())
        .map(HistoricVariableInstance::getValue)
        .map(String::valueOf)
        .filter(Constants.Value.COMPLETE::equalsIgnoreCase)
        .isPresent();
  }

  private String getMultipleApprovalSession(HistoricTaskInstance task, List<HistoricVariableInstance> variables) {
    return variables.stream()
        .filter(variable -> StringUtils.equals(task.getId(), variable.getTaskId()))
        .filter(variable -> StringUtils.equals(Constants.Variable.MULTIPLE_APPROVAL_SESSION, variable.getVariableName()))
        .findFirst()
        .map(HistoricVariableInstance::getValue)
        .map(String::valueOf)
        .orElse("NO_SESSION_FOUND");
  }

  private TaskState getMultipleTaskStatus(String session, List<HistoricVariableInstance> variables, boolean suspended) {
    var sessionVariables = variables.stream()
        .filter(variable -> StringUtils.startsWith(variable.getVariableName(), Constants.Variable.MULTIPLE_APPROVAL_SESSION + session))
        .collect(Collectors.toList());
    var completed = sessionVariables.stream()
        .filter(x -> x.getVariableName().endsWith(Constants.Variable.MULTIPLE_APPROVAL_COMPLETED_SUFFIX))
        .findFirst()
        .map(HistoricVariableInstance::getValue)
        .map(Converter::toBoolean)
        .orElse(false);
    var approved = sessionVariables.stream()
        .filter(x -> x.getVariableName().endsWith(Constants.Variable.APPROVED_SUFFIX))
        .findFirst()
        .map(HistoricVariableInstance::getValue)
        .map(Converter::toBoolean)
        .orElse(false);
    if (!completed) {
      if (suspended) {
        return TaskState.SUSPENDED;
      }
      return TaskState.PROCESSING;
    }
    return approved ? TaskState.APPROVED : TaskState.REJECTED;
  }

  private boolean isRuntimeInstanceSuspended(String instanceId) {
    return runtimeService != null
        && runtimeService.createProcessInstanceQuery()
        .processInstanceId(instanceId)
        .suspended()
        .count() > 0;
  }

  private Try<HistoricProcessInstance> getHistoricInstance(String instanceId) {
    return Try.of(() -> historyService.createHistoricProcessInstanceQuery()
        .processInstanceId(instanceId)
        .singleResult());
  }

  private Process getProcess(HistoricProcessInstance instance) {
    return repositoryService.getBpmnModel(instance.getProcessDefinitionId()).getMainProcess();
  }
}
