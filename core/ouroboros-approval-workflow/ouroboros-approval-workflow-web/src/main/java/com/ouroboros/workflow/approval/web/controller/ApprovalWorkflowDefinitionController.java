package com.ouroboros.workflow.approval.web.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.vavr.control.Try;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.workflow.approval.CurrentUserSupplier;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionDocument;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;
import com.ouroboros.workflow.approval.manage.lifecycle.WorkflowModelDefinitionProjector;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;
import com.ouroboros.workflow.approval.model.FlowDefinitionVersion;
import com.ouroboros.workflow.approval.web.service.ApprovalWorkflowDiagramResultAssembler;
import com.ouroboros.workflow.approval.web.vo.ApprovalWorkflowDiagramResult;
import com.ouroboros.workflow.approval.web.vo.WorkflowDefinitionVersionDetailResult;
import com.ouroboros.workflow.approval.web.vo.WorkflowDefinitionVersionResult;
import com.ouroboros.workflow.approval.web.vo.WorkflowModelResult;

@RestController
@RequestMapping("/api/approval-workflow/definition")
public class ApprovalWorkflowDefinitionController {
  final WorkflowModelDefinitionProjector workflowModelProjector = new WorkflowModelDefinitionProjector();
  final ApprovalWorkflowDiagramResultAssembler approvalWorkflowDiagramResultAssembler =
      new ApprovalWorkflowDiagramResultAssembler();

  @Autowired
  WorkflowModelService workflowModelService;

  @Autowired
  FlowDefinitionService flowDefinitionService;

  @Operation(
      summary = "创建流程定义",
      description = "创建新的审批流程定义草稿。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "流程定义创建成功"),
      @ApiResponse(responseCode = "400", description = "流程定义内容不合法")
  })
  @PostMapping
  public WebApiResult<Void> create(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "流程定义 JSON", required = true)
      @RequestBody String processJson) {
    Try<Void> result = decodeMetadata(processJson)
        .flatMap(metadata -> workflowModelProjector.createInstanceDraft(metadata, getCurrentUserId()))
        .mapTry(workflowModelService::add)
        .<Void>map(ignored -> null);
    return WebApiResult.of(result);
  }

  @Operation(
      summary = "删除流程定义",
      description = "删除指定的流程定义草稿。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "流程定义删除成功"),
      @ApiResponse(responseCode = "404", description = "流程定义不存在")
  })
  @DeleteMapping("/{id}")
  public WebApiResult<Void> delete(
      @Parameter(description = "流程定义 ID", required = true) @PathVariable String id) {
    Try<Void> result = Try.of(() -> {
      WorkflowModel model = workflowModelService.getByPrimaryKey(id);
      if (model == null) {
        throw new BusinessException("流程不存在");
      }
      if (!"draft".equalsIgnoreCase(model.getStatus())) {
        throw new BusinessException("只能删除草稿状态的流程");
      }
      workflowModelService.delete(id);
      return (Void) null;
    });
    return WebApiResult.of(result);
  }

  @Operation(
      summary = "更新流程定义",
      description = "根据流程定义 ID 更新流程定义内容。部署需显式调用部署接口。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "流程定义更新成功"),
      @ApiResponse(responseCode = "400", description = "流程定义内容不合法"),
      @ApiResponse(responseCode = "404", description = "流程定义不存在")
  })
  @PutMapping("/{id}")
  public WebApiResult<Void> update(
      @Parameter(description = "流程定义 ID", required = true) @PathVariable String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "流程定义 JSON", required = true)
      @RequestBody String processJson) {
    Try<Void> result = Try.of(() -> {
      ApprovalWorkflowDefinitionMetadata metadata = decodeMetadata(processJson)
          .map(value -> ensureDefinitionNameMatchesPath(id, value))
          .get();
      WorkflowModel existing = workflowModelService.getByPrimaryKey(id);
      WorkflowModel workflowModel = workflowModelProjector.updateInstanceDefinition(id, existing, metadata, getCurrentUserId()).get();
      if (existing == null) {
        workflowModelService.add(workflowModel);
      } else {
        workflowModelService.update(id, workflowModel);
      }
      return (Void) null;
    });
    return WebApiResult.of(result);
  }

  @Operation(
      summary = "获取流程定义详情",
      description = "根据流程定义 ID 获取流程定义内容。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程定义详情")
  })
  @GetMapping("/{id}")
  public WebApiResult<Object> get(
      @Parameter(description = "流程定义 ID", required = true) @PathVariable String id,
      @Parameter(description = "返回内容格式版本") @RequestParam(required = false) Integer formatVersion) {
    Try<WorkflowModel> workflowModelTry = Try.of(() -> workflowModelService.getByPrimaryKey(id));
    if (workflowModelTry.isFailure()) {
      return WebApiResult.of(workflowModelTry.map(model -> (Object) model));
    }
    WorkflowModel workflowModel = workflowModelTry.get();
    if (workflowModel == null) {
      return WebApiResult.of(Optional.empty());
    }
    return WebApiResult.of(encodeForRead(workflowModel.getJson(), formatVersion));
  }

  @Operation(
      summary = "分页查询流程定义",
      description = "按关键字和状态分页查询流程定义列表。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程定义分页结果")
  })
  @GetMapping("")
  public PagedWebApiResult<WorkflowModelResult> search(
      @Parameter(description = "流程名称或关键字") @RequestParam(required = false) String keyword,
      @Parameter(description = "流程状态") @RequestParam(required = false) String status,
      @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
      @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") Integer perPage) {
    Try<PagedWebApiResult<WorkflowModel>> result = Try.of(() -> workflowModelService.searchByPage(keyword, "all".equalsIgnoreCase(status) ? null : status, page, perPage));
    if (result.isFailure()) {
      return PagedWebApiResult.fail(result.getCause().getMessage());
    }
    PagedWebApiResult<WorkflowModel> pagedResult = result.get();
    if (pagedResult.getCode() != 0) {
      return PagedWebApiResult.fail(pagedResult.getCode(), pagedResult.getMsg());
    }
    Try<List<WorkflowModelResult>> itemsResult = toWorkflowModelResults(pagedResult.getData().getItems());
    if (itemsResult.isFailure()) {
      return PagedWebApiResult.fail(itemsResult.getCause().getMessage());
    }
    return PagedWebApiResult.ok(pagedResult.getData().getTotal(), itemsResult.get());
  }

  @Operation(
      summary = "下线流程定义",
      description = "将已部署的流程定义下线。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "流程定义下线成功"),
      @ApiResponse(responseCode = "400", description = "流程状态不允许下线")
  })
  @PostMapping("/{id}/offline")
  public WebApiResult<Void> offline(
      @Parameter(description = "流程定义 ID", required = true) @PathVariable String id) {
    Try<Void> result = Try.of(() -> {
      WorkflowModel model = workflowModelService.getByPrimaryKey(id);
      if (model == null) {
        throw new BusinessException("流程不存在");
      }
      if (!"deployed".equalsIgnoreCase(model.getStatus())) {
        throw new BusinessException("只能下线已部署的流程");
      }
      workflowModelService.offline(id);
      return (Void) null;
    });
    return WebApiResult.of(result);
  }

  @Operation(
      summary = "重置流程定义",
      description = "下线当前流程并恢复为初始流程配置，保留历史部署和流程实例。重置后需显式部署生成新的引擎版本。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "流程定义已重置"),
      @ApiResponse(responseCode = "400", description = "流程定义不允许重置或找不到可恢复的初始流程配置")
  })
  @PostMapping("/{id}/reset")
  public WebApiResult<Void> reset(
      @Parameter(description = "流程定义 ID", required = true) @PathVariable String id) {
    Try<Void> result = Try.run(() -> workflowModelService.reset(id));
    return WebApiResult.of(result);
  }

  @Operation(
      summary = "部署流程定义",
      description = "将指定流程定义部署为可用流程。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "流程定义部署成功")
  })
  @PostMapping("/{id}/deploy")
  public WebApiResult<Void> deploy(
      @Parameter(description = "流程定义 ID", required = true) @PathVariable String id) {
    Try<Void> result = Try.run(() -> workflowModelService.deploy(id));
    return WebApiResult.of(result);
  }

  @Operation(
      summary = "获取流程定义版本历史",
      description = "按流程 key 查询所有已部署版本。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程定义版本历史")
  })
  @GetMapping("/{flowKey}/versions")
  public WebApiResult<List<WorkflowDefinitionVersionResult>> listVersions(
      @Parameter(description = "流程定义 key", required = true) @PathVariable String flowKey) {
    Try<List<WorkflowDefinitionVersionResult>> result = flowDefinitionService.listFlowDefinitionVersions(flowKey)
        .map(versions -> toVersionResults(flowKey, versions));
    return WebApiResult.of(result);
  }

  @Operation(
      summary = "获取流程定义指定版本",
      description = "按流程 key 与版本号查询流程定义版本详情。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程定义版本详情")
  })
  @GetMapping("/{flowKey}/versions/{version}")
  public WebApiResult<WorkflowDefinitionVersionDetailResult> getVersion(
      @Parameter(description = "流程定义 key", required = true) @PathVariable String flowKey,
      @Parameter(description = "流程定义版本号", required = true) @PathVariable Integer version) {
    Try<WorkflowDefinitionVersionDetailResult> result = flowDefinitionService.getFlowDefinitionVersion(flowKey, version)
        .flatMap(value -> toVersionDetailResult(flowKey, value));
    return WebApiResult.of(result);
  }

  @Operation(
      summary = "获取流程定义流程图数据",
      description = "按流程 key 与版本号返回流程图定义数据。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回流程定义流程图数据")
  })
  @GetMapping("/{flowKey}/versions/{version}/diagram")
  public WebApiResult<ApprovalWorkflowDiagramResult> getDefinitionDiagram(
      @Parameter(description = "流程定义 key", required = true) @PathVariable String flowKey,
      @Parameter(description = "流程定义版本号", required = true) @PathVariable Integer version) {
    return WebApiResult.of(flowDefinitionService.getFlowDefinitionV2(flowKey, version)
        .map(definition -> approvalWorkflowDiagramResultAssembler.assemble(definition, null)));
  }

  private String getCurrentUserId() {
    return CurrentUserSupplier.getCurrentUserId()
        .orElse("anonymousUser");
  }

  private ApprovalWorkflowDefinitionMetadata ensureDefinitionNameMatchesPath(
      String pathId,
      ApprovalWorkflowDefinitionMetadata definition) {
    if (definition == null || definition.getName() == null) {
      throw new BusinessException("流程定义 name 不能为空");
    }
    if (!pathId.equals(definition.getName())) {
      throw new BusinessException("流程定义 name 与请求路径不一致: " + pathId + " != " + definition.getName());
    }
    return definition;
  }

  private Try<ApprovalWorkflowDefinitionMetadata> decodeMetadata(String rawJson) {
    return ApprovalWorkflowDefinitionDocument.decode(rawJson)
        .map(ApprovalWorkflowDefinitionDocument::metadata);
  }

  private Try<List<WorkflowModelResult>> toWorkflowModelResults(List<WorkflowModel> models) {
    List<WorkflowModelResult> results = new ArrayList<>();
    for (WorkflowModel model : models) {
      Try<WorkflowModelResult> result = toWorkflowModelResult(model);
      if (result.isFailure()) {
        return Try.failure(result.getCause());
      }
      results.add(result.get());
    }
    return Try.success(results);
  }

  private Try<WorkflowModelResult> toWorkflowModelResult(WorkflowModel model) {
    return decodeMetadata(model.getJson())
        .map(metadata -> new WorkflowModelResult(model, metadata))
        .recover(cause -> new WorkflowModelResult(model, null));
  }

  private Try<Object> encodeForRead(String rawJson, Integer formatVersion) {
    return ApprovalWorkflowDefinitionDocument.decode(rawJson)
        .flatMap(ApprovalWorkflowDefinitionDocument::toStaticJsonNode)
        .map(value -> (Object) value);
  }

  private List<WorkflowDefinitionVersionResult> toVersionResults(String flowKey, List<FlowDefinitionVersion> versions) {
    Integer latestVersion = versions.stream()
        .map(FlowDefinitionVersion::getVersion)
        .filter(java.util.Objects::nonNull)
        .max(Integer::compareTo)
        .orElse(null);
    return versions.stream()
        .map(version -> {
          WorkflowDefinitionVersionResult result = new WorkflowDefinitionVersionResult(version);
          result.setFlowKey(flowKey);
          result.setStatus(isActiveVersion(version.getVersion(), latestVersion) ? "active" : "inactive");
          return result;
        })
        .collect(java.util.stream.Collectors.toList());
  }

  private Try<WorkflowDefinitionVersionDetailResult> toVersionDetailResult(String flowKey, FlowDefinitionVersion version) {
    return ApprovalWorkflowDefinitionDocument.decode(version.getDefinition())
        .flatMap(document -> document.toStaticJsonNode()
            .map(definition -> new VersionDetailMetadata(document.metadata(), definition)))
        .map(definition -> {
          WorkflowDefinitionVersionDetailResult result = new WorkflowDefinitionVersionDetailResult();
          result.setFlowKey(flowKey);
          result.setVersion(version.getVersion());
          result.setName(definition.metadata().getTitle());
          result.setCategory("");
          result.setDescription(definition.metadata().getDescription());
          result.setDeploymentTime(version.getDeploymentTime());
          result.setStatus(isActiveVersion(flowKey, version.getVersion()) ? "active" : "inactive");
          result.setProcessDefinitionId(version.getProcessDefinitionId());
          result.setDeploymentId(version.getDeploymentId());
          result.setDeployedBy(version.getDeployedBy());
          result.setDefinition(definition.definition());
          return result;
        });
  }

  private boolean isActiveVersion(String flowKey, Integer version) {
    return flowDefinitionService.listFlowDefinitionVersions(flowKey)
        .map(versions -> versions.stream()
            .map(FlowDefinitionVersion::getVersion)
            .filter(java.util.Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(null))
        .map(latestVersion -> isActiveVersion(version, latestVersion))
        .getOrElse(false);
  }

  private boolean isActiveVersion(Integer version, Integer latestVersion) {
    return version != null && version.equals(latestVersion);
  }

  record VersionDetailMetadata(ApprovalWorkflowDefinitionMetadata metadata, JsonNode definition) {
  }

}
