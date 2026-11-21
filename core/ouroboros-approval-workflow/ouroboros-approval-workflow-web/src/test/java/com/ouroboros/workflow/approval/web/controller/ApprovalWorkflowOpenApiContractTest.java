package com.ouroboros.workflow.approval.web.controller;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ParameterLocation;
import com.ouroboros.workflow.approval.web.vo.StartFlowInstanceData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApprovalWorkflowOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractRequestBodyParametersAndResponsesForDefinitionUpdate() throws Exception {
    Method method = ApprovalWorkflowDefinitionController.class.getMethod(
        "update", String.class, String.class);

    SimpleWebAPIModel model = extract(
        "PUT",
        "/api/approval-workflow/definition/{id}",
        mapping("/api/approval-workflow/definition/{id}", RequestMethod.PUT),
        ApprovalWorkflowDefinitionController.class,
        method);

    assertEquals("更新流程定义", model.getSummary());
    assertEquals("根据流程定义 ID 更新流程定义内容。部署需显式调用部署接口。", model.getDescription());
    assertEquals(Collections.singletonList("approval-workflow"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "id").getIn());
    assertEquals("流程定义 ID", parameter(model, "id").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "id").getRequired());
    assertFalse(model.getParameters().stream().anyMatch(parameter -> "deploy".equals(parameter.getName())));
    assertNotNull(model.getRequestBody());
    assertEquals("流程定义 JSON", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("流程定义更新成功", model.getResponses().get("200").getDescription());
    assertEquals("流程定义内容不合法", model.getResponses().get("400").getDescription());
    assertEquals("流程定义不存在", model.getResponses().get("404").getDescription());
  }

  @Test
  void shouldExtractDefinitionDeployAsExplicitPostOperation() throws Exception {
    Method method = ApprovalWorkflowDefinitionController.class.getMethod("deploy", String.class);

    SimpleWebAPIModel model = extract(
        "POST",
        "/api/approval-workflow/definition/{id}/deploy",
        mapping("/api/approval-workflow/definition/{id}/deploy", RequestMethod.POST),
        ApprovalWorkflowDefinitionController.class,
        method);

    assertEquals("部署流程定义", model.getSummary());
    assertEquals("将指定流程定义部署为可用流程。", model.getDescription());
    assertEquals(Collections.singletonList("approval-workflow"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "id").getIn());
    assertEquals("流程定义 ID", parameter(model, "id").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "id").getRequired());
    assertEquals("流程定义部署成功", model.getResponses().get("200").getDescription());
  }

  @Test
  void shouldExtractDefinitionResetAsExplicitPostOperation() throws Exception {
    Method method = ApprovalWorkflowDefinitionController.class.getMethod("reset", String.class);

    SimpleWebAPIModel model = extract(
        "POST",
        "/api/approval-workflow/definition/{id}/reset",
        mapping("/api/approval-workflow/definition/{id}/reset", RequestMethod.POST),
        ApprovalWorkflowDefinitionController.class,
        method);

    assertEquals("重置流程定义", model.getSummary());
    assertEquals("下线当前流程并恢复为初始流程配置，保留历史部署和流程实例。重置后需显式部署生成新的引擎版本。", model.getDescription());
    assertEquals(Collections.singletonList("approval-workflow"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "id").getIn());
    assertEquals("流程定义 ID", parameter(model, "id").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "id").getRequired());
    assertEquals("流程定义已重置", model.getResponses().get("200").getDescription());
    assertEquals("流程定义不允许重置或找不到可恢复的初始流程配置", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractRequestBodyParametersAndResponsesForInstanceStart() throws Exception {
    Method method = ApprovalWorkflowInstanceController.class.getMethod(
        "start", String.class, StartFlowInstanceData.class);

    SimpleWebAPIModel model = extract(
        "POST",
        "/api/approval-workflow/instance/{flowKey}/start",
        mapping("/api/approval-workflow/instance/{flowKey}/start", RequestMethod.POST),
        ApprovalWorkflowInstanceController.class,
        method);

    assertEquals("发起流程实例", model.getSummary());
    assertEquals(Collections.singletonList("approval-workflow"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "flowKey").getIn());
    assertEquals("流程定义 ID", parameter(model, "flowKey").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "flowKey").getRequired());
    assertNotNull(model.getRequestBody());
    assertEquals("流程发起数据", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("流程实例发起成功", model.getResponses().get("200").getDescription());
    assertEquals("流程发起参数不合法", model.getResponses().get("400").getDescription());
  }

  @Test
  void shouldExtractStartRequirementsContract() throws Exception {
    Method method = ApprovalWorkflowInstanceController.class.getMethod(
        "getStartRequirements", String.class, StartFlowInstanceData.class);

    SimpleWebAPIModel model = extract(
        "POST",
        "/api/approval-workflow/instance/{flowKey}/start-requirements",
        mapping("/api/approval-workflow/instance/{flowKey}/start-requirements", RequestMethod.POST),
        ApprovalWorkflowInstanceController.class,
        method);

    assertEquals("查询流程发起前置要求", model.getSummary());
    assertEquals(Collections.singletonList("approval-workflow"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "flowKey").getIn());
    assertEquals("流程定义 ID", parameter(model, "flowKey").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "flowKey").getRequired());
    assertNotNull(model.getRequestBody());
    assertEquals("流程发起上下文", model.getRequestBody().getDescription());
    assertEquals("成功返回流程发起前置要求", model.getResponses().get("200").getDescription());
  }

  @Test
  void shouldExtractRequestBodyAndResponsesForBaseRecordAddAndPatchUpdate() throws Exception {
    Method add = ApprovalWorkflowCategoryController.class.getMethod("add", java.util.Map.class);
    SimpleWebAPIModel addModel = extract(
        "POST",
        "/api/approval-workflow/category",
        mapping("/api/approval-workflow/category", RequestMethod.POST),
        ApprovalWorkflowCategoryController.class,
        add);

    assertEquals("创建审批工作流基础记录", addModel.getSummary());
    assertEquals(Collections.singletonList("approval-workflow"), addModel.getTags());
    assertNotNull(addModel.getRequestBody());
    assertEquals("新增记录参数", addModel.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, addModel.getRequestBody().getRequired());
    assertEquals("记录创建成功", addModel.getResponses().get("201").getDescription());

    Method patchUpdate = ApprovalWorkflowCategoryController.class.getMethod("patchUpdate", Object.class, java.util.Map.class);
    SimpleWebAPIModel patchModel = extract(
        "PATCH",
        "/api/approval-workflow/category/{id}",
        mapping("/api/approval-workflow/category/{id}", RequestMethod.PATCH),
        ApprovalWorkflowCategoryController.class,
        patchUpdate);

    assertEquals("部分更新审批工作流基础记录", patchModel.getSummary());
    assertEquals(Collections.singletonList("approval-workflow"), patchModel.getTags());
    assertEquals(ParameterLocation.PATH, parameter(patchModel, "id").getIn());
    assertEquals("记录 ID", parameter(patchModel, "id").getDescription());
    assertEquals(Boolean.TRUE, parameter(patchModel, "id").getRequired());
    assertNotNull(patchModel.getRequestBody());
    assertEquals("局部更新字段", patchModel.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, patchModel.getRequestBody().getRequired());
    assertEquals("记录部分更新成功", patchModel.getResponses().get("200").getDescription());
    assertEquals("记录不存在", patchModel.getResponses().get("404").getDescription());
  }

  @Test
  void shouldExtractTaskTargetQueryResponsesForTaskTargetEndpoints() throws Exception {
    assertTaskTargetQueryContract(
        "getTransferTargets",
        "/api/approval-workflow/task/{taskId}/transfer-targets",
        "查询转办可选目标用户",
        "成功返回转办目标用户列表");
    assertTaskTargetQueryContract(
        "getAddSignTargets",
        "/api/approval-workflow/task/{taskId}/add-sign-targets",
        "查询加签可选目标用户",
        "成功返回加签目标用户列表");
    assertTaskTargetQueryContract(
        "getFreeRejectTargets",
        "/api/approval-workflow/task/{taskId}/free-reject-targets",
        "查询自由驳回可选目标节点",
        "成功返回自由驳回目标节点列表");
    assertTaskTargetQueryContract(
        "getFreeApprovalTargets",
        "/api/approval-workflow/task/{taskId}/free-approval-targets",
        "查询自由审批可选协商用户",
        "成功返回自由审批协商用户列表");
    assertTaskTargetQueryContract(
        "getRejectTargets",
        "/api/approval-workflow/task/{taskId}/reject-targets",
        "查询拒绝可选目标节点",
        "成功返回拒绝目标节点列表");
    assertTaskTargetQueryContract(
        "getRecoveryTargets",
        "/api/approval-workflow/task/{taskId}/recovery-targets",
        "查询恢复可选目标节点",
        "成功返回恢复目标节点列表");
  }

  @Test
  void shouldExtractDurationStatisticsQueryContract() throws Exception {
    Method method = ApprovalWorkflowStatisticsController.class.getMethod(
        "duration", String.class, String.class, String.class);

    SimpleWebAPIModel model = extract(
        "GET",
        "/api/approval-workflow/statistics/duration",
        mapping("/api/approval-workflow/statistics/duration", RequestMethod.GET),
        ApprovalWorkflowStatisticsController.class,
        method);

    assertEquals("统计审批流时长", model.getSummary());
    assertEquals(Collections.singletonList("approval-workflow"), model.getTags());
    assertEquals(ParameterLocation.QUERY, parameter(model, "flowKey").getIn());
    assertEquals("流程定义 ID", parameter(model, "flowKey").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "flowKey").getRequired());
    assertEquals(ParameterLocation.QUERY, parameter(model, "from").getIn());
    assertEquals(Boolean.TRUE, parameter(model, "from").getRequired());
    assertEquals(ParameterLocation.QUERY, parameter(model, "to").getIn());
    assertEquals(Boolean.TRUE, parameter(model, "to").getRequired());
    assertEquals("成功返回审批流时长统计结果", model.getResponses().get("200").getDescription());
  }

  private void assertTaskTargetQueryContract(String methodName, String path, String summary, String response200Description) throws Exception {
    Method method = ApprovalWorkflowTaskController.class.getMethod(methodName, String.class);
    SimpleWebAPIModel model = extract(
        "GET",
        path,
        mapping(path, RequestMethod.GET),
        ApprovalWorkflowTaskController.class,
        method);

    assertEquals(summary, model.getSummary());
    assertEquals(Collections.singletonList("approval-workflow"), model.getTags());
    assertEquals(ParameterLocation.PATH, parameter(model, "taskId").getIn());
    assertEquals("任务 ID", parameter(model, "taskId").getDescription());
    assertEquals(Boolean.TRUE, parameter(model, "taskId").getRequired());
    assertEquals(response200Description, model.getResponses().get("200").getDescription());
    assertEquals("任务不存在", model.getResponses().get("404").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Class<?> controllerClass, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder()
            .api(httpMethod, path)
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        controllerClass,
        method
    ).build();
  }

  private RequestMappingInfo mapping(String path, RequestMethod method) {
    return RequestMappingInfo.paths(path)
        .methods(method)
        .consumes("application/json")
        .produces("application/json")
        .build();
  }

  private com.ouroboros.webapi.openapi.ParameterModel parameter(SimpleWebAPIModel model, String name) {
    return model.getParameters().stream()
        .filter(parameter -> name.equals(parameter.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing parameter " + name));
  }
}
