package com.ouroboros.workflow.approval.external.dingtalk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ouroboros.workflow.approval.external.ExternalFormSchema;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinition;
import com.ouroboros.workflow.approval.external.ExternalTaskSnapshot;

import io.vavr.control.Try;

/**
 * 钉钉 OpenAPI 客户端测试 fake：内存实现，记录调用参数。
 */
class FakeDingtalkOpenApiClient implements DingtalkOpenApiClient {

  boolean failToken = false;
  final List<StartCall> startCalls = new ArrayList<>();
  final List<TaskActionCall> actionCalls = new ArrayList<>();
  String nextInstanceId = "inst-1";

  record StartCall(String originatorUserId, String processCode,
                   List<Map<String, String>> formComponentValues) {
  }

  record TaskActionCall(String processInstanceId, String taskId, String action, String comment) {
  }

  @Override
  public Try<String> accessToken() {
    return failToken ? Try.failure(new IllegalStateException("token 失败"))
        : Try.success("test-token");
  }

  @Override
  public Try<String> startProcessInstance(
      String originatorUserId, String processCode,
      List<Map<String, String>> formComponentValues) {
    startCalls.add(new StartCall(originatorUserId, processCode, formComponentValues));
    return Try.success(nextInstanceId);
  }

  @Override
  public Try<ExternalFormSchema> getTemplateSchema(String processCode) {
    return Try.success(new ExternalFormSchema(List.of(), Map.of()));
  }

  @Override
  public Try<ExternalProcessDefinition> validateTemplate(String processCode) {
    return Try.success(new ExternalProcessDefinition(processCode, null, "模板", "hash"));
  }

  @Override
  public Try<ExternalInstanceSnapshot> getInstanceDetail(String processInstanceId) {
    return Try.success(new ExternalInstanceSnapshot(processInstanceId, "RUNNING", null, null, null, Map.of()));
  }

  @Override
  public Try<List<ExternalTaskSnapshot>> listTasks(String processInstanceId) {
    return Try.success(List.of());
  }

  @Override
  public Try<Void> executeTaskAction(
      String processInstanceId, String taskId, String action, String comment) {
    actionCalls.add(new TaskActionCall(processInstanceId, taskId, action, comment));
    return Try.success(null);
  }

  final List<CentreTaskCall> centreTaskCalls = new ArrayList<>();
  int nextMirrorTaskId = 1;

  record CentreTaskCall(String op, String processInstanceId, String activityId,
                        List<Map<String, Object>> tasks, List<String> activityIds) {
  }



}
