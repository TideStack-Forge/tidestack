package com.ouroboros.logicflow.manage.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Change;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.StoredOverride;
import com.ouroboros.logicflow.model.FlowModelBuildReport;
import com.ouroboros.logicflow.model.FlowModelMeta;

public final class RuntimeFlowManagement {
  private RuntimeFlowManagement() { }

  public record Command(
      @Schema(description = "规范 UUID；同操作者同请求可幂等重放", requiredMode = Schema.RequiredMode.REQUIRED) String operationId,
      @Schema(description = "详情返回的条件编辑令牌", requiredMode = Schema.RequiredMode.REQUIRED) String editToken,
      @Schema(description = "完整正式模型；名称、启用和公开 IO 合同固定", requiredMode = Schema.RequiredMode.REQUIRED) FlowModelMeta definition) {
    @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("未知命令字段: " + name); }
  }
  public record VersionedCommand(
      @Schema(description = "规范 UUID", requiredMode = Schema.RequiredMode.REQUIRED) String operationId,
      @Schema(description = "详情返回的条件编辑令牌", requiredMode = Schema.RequiredMode.REQUIRED) String editToken) {
    @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("未知命令字段: " + name); }
  }
  public record RestoreCommand(
      @Schema(description = "本次恢复的新 UUID", requiredMode = Schema.RequiredMode.REQUIRED) String operationId,
      @Schema(description = "当前详情的条件编辑令牌", requiredMode = Schema.RequiredMode.REQUIRED) String editToken,
      @Schema(description = "同模型历史操作 UUID，读取其 before", requiredMode = Schema.RequiredMode.REQUIRED) String fromChangeId) {
    @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("未知命令字段: " + name); }
  }
  public record ValidationCommand(
      @Schema(description = "当前详情的条件编辑令牌", requiredMode = Schema.RequiredMode.REQUIRED) String editToken,
      @Schema(description = "待检查的完整正式模型；检查不执行", requiredMode = Schema.RequiredMode.REQUIRED) FlowModelMeta definition) {
    @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("未知命令字段: " + name); }
  }
  public record Access(String actor, boolean edit, boolean reload) { }
  public record View(String name, String description, String defaultSource, FlowModelMeta defaultDefinition,
      StoredOverride override, FlowModelMeta publishedDefinition, boolean exactMetadata, String runtimeRevision,
      String editToken, String catalogVersion, String appliedCatalogVersion, String defaultHash, String publicationId,
      List<String> readOnlyReasons, List<String> availableActions, boolean defaultChanged, String instanceId,
      Map<String, Object> definition, String operationId, List<Source> sources,
      Boolean valid, List<String> diagnostics) { }
  public record InstanceView(com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Instance instance,
      String state, boolean recent, boolean current, boolean defaultDiffers) { }
  public record Operations(View model, String state, long observedAt, List<InstanceView> instances, List<Change> changes) { }
  public record Source(String id, int priority, boolean runtimeOverride, boolean declared, boolean complete,
      boolean enabled, boolean selectedDefault, String version, List<String> diagnostics) { }
  public record Summary(String name, String description, String defaultSource, boolean overridden,
      boolean defaultChanged, boolean editable, String state, String actor, String updatedAt, Boolean valid) { }
  public record Page(List<Summary> items, int total, String instanceId, String catalogVersion) { }
  public record Validation(boolean valid, FlowModelBuildReport report, Set<String> impact,
      String candidateHash, String basePublicationId, List<Difference> differences) { }
  public record Difference(String path, String kind) { }
  public record Operation(Change change, String currentCatalogVersion, boolean superseded,
      List<com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Instance> instances) { }
  public record ApplyRequest(
      @Schema(description = "当前覆盖集合 epoch:revision", requiredMode = Schema.RequiredMode.REQUIRED) String expectedCatalogVersion) {
    @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("未知命令字段: " + name); }
  }
  public record ApplyReceipt(String instanceId, String catalogVersion, String status) { }
}
