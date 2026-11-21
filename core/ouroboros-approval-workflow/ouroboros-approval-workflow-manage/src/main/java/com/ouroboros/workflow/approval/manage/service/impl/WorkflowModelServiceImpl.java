package com.ouroboros.workflow.approval.manage.service.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.workflow.approval.FlowManageService;
import com.ouroboros.workflow.approval.FlowDeploymentResult;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionDocument;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;
import com.ouroboros.workflow.approval.manage.lifecycle.WorkflowModelDefinitionProjector;
import com.ouroboros.workflow.approval.manage.service.ApprovalWorkflowDesignTimeDefinitionProvider;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;

import io.vavr.control.Try;

@Service
public class WorkflowModelServiceImpl extends SimpleModelServiceImpl<String, WorkflowModel> implements WorkflowModelService {
  final WorkflowModelDefinitionProjector workflowModelProjector = new WorkflowModelDefinitionProjector();

  @Autowired
  FlowManageService flowManageService;

  @Autowired(required = false)
  List<ApprovalWorkflowDesignTimeDefinitionProvider> designTimeDefinitionProviders = List.of();

  @Autowired(required = false)
  List<com.ouroboros.workflow.approval.FlowFormProvider> flowFormProviders = List.of();

  @Override
  public WorkflowModel add(WorkflowModel model) throws OuroborosException {
    applyRuntimeDefaults(model);
    return insertRuntimeModel(model);
  }

  @Override
  public Boolean update(String id, WorkflowModel model) throws OuroborosException {
    WorkflowModel existing = getByPrimaryKey(id);
    applyRuntimeAdoption(existing, model);
    applyRuntimeDefaults(model);
    return updateRuntimeModel(id, model);
  }

  @Override
  public Boolean partialUpdate(String id, Map<String, Object> map) throws OuroborosException {
    WorkflowModel existing = getByPrimaryKey(id);
    if (isMetadataLayerModel(existing) && !Boolean.TRUE.equals(existing.getAdopted()) && containsDefinitionEdit(map)) {
      map.put("adopted", true);
    }
    Object definitionJson = map == null ? null : map.get("json");
    if (definitionJson instanceof String) {
      map.put("version", definitionVersion((String) definitionJson));
      // 已部署/已下线的定义被改动后必须回到 updated，否则 deploy() 会因为 status=deployed 直接返回，
      // 已接管并已部署的记录再也无法重新部署。
      if (isRuntimeDefinitionStatus(existing)) {
        map.put("status", "updated");
      }
    }
    return partialUpdateRuntimeModel(id, map);
  }

  private boolean isRuntimeDefinitionStatus(WorkflowModel existing) {
    if (existing == null || existing.getStatus() == null) {
      return false;
    }
    return "deployed".equalsIgnoreCase(existing.getStatus())
        || "offline".equalsIgnoreCase(existing.getStatus());
  }

  @Override
  @Transactional
  public int refreshDefinitionLayers(List<ApprovalWorkflowDefinitionMetadata> definitions) throws OuroborosException {
    List<WorkflowModel> layeredDefinitions = toLayeredWorkflowModels(definitions == null ? List.of() : definitions);
    validateLayerSnapshot(layeredDefinitions);
    Set<String> incomingIds = layeredDefinitions.stream()
        .map(WorkflowModel::getId)
        .collect(Collectors.toSet());
    int changed = 0;
    for (WorkflowModel definition : layeredDefinitions) {
      if (refreshDefinitionLayer(definition)) {
        changed++;
      }
    }
    for (WorkflowModel existing : queryLayerModels()) {
      if (!incomingIds.contains(existing.getId()) && retireMissingLayerDefinition(existing)) {
        changed++;
      }
    }
    return changed;
  }

  boolean refreshDefinitionLayer(WorkflowModel definition) throws OuroborosException {
    validateLayerModel(definition);
    WorkflowModel existing = getByPrimaryKey(definition.getId());
    if (existing == null) {
      insertLayerModel(definition);
      return true;
    }
    if (!isMetadataLayerModel(existing)) {
      throw new BusinessException("静态审批流定义与实例流程 ID 冲突: " + definition.getId());
    }
    if (Boolean.TRUE.equals(existing.getAdopted())) {
      return false;
    }
    int versionCompare = compareVersion(existing, definition);
    if (versionCompare < 0) {
      return false;
    }
    if (isSameLayerContent(existing, definition)) {
      return false;
    }
    if (versionCompare == 0) {
      throw new BusinessException("静态审批流定义同版本内容冲突: " + definition.getId());
    }
    definition.setStatus(nextLayerStatus(existing, definition));
    // 指针一旦被清空就再也无法下线旧部署（offlineProcessDefinition 在 processDefinitionId 为空时静默 no-op），
    // 而旧部署在引擎侧仍是 active、仍可被发起，因此先挂起再丢指针。
    offlineProcessDefinition(existing);
    definition.setProcessDefinitionId("");
    updateLayerModel(definition.getId(), definition);
    return true;
  }

  @Override
  public PagedWebApiResult<WorkflowModel> searchByPage(String keyword, String status, int page, int pageSize) throws OuroborosException {
    TypedDataModel<String, WorkflowModel> m = this.getDataModel();
    Map<String, Object> where = new HashMap<String, Object>() {{
      if (ObjectUtils.isNotEmpty(keyword)) {
        put("or", Arrays.asList(
            new HashMap<String, Object>() {{
              put("name", new HashMap<String, Object>() {{
                put("like", "%" + keyword + "%");
              }});
            }},
            new HashMap<String, Object>() {{
              put("id", new HashMap<String, Object>() {{
                put("like", "%" + keyword + "%");
              }});
            }}
        ));
      }
      if (ObjectUtils.isNotEmpty(status)) {
        put("status", new HashMap<String, Object>() {{
          put("=", status);
        }});
      }
    }};

    Try<Long> countResult = m.count(where);
    if (countResult.isFailure()) {
      throw OuroborosExceptionMapper.toOuroborosException(countResult.getCause(), (cause) -> new InternalException("查询总数失败", cause));
    }

    Try<java.util.List<WorkflowModel>> queryResult = m.query(null, where, "saveAt desc", (page - 1) * pageSize, pageSize);
    if (queryResult.isFailure()) {
      throw OuroborosExceptionMapper.toOuroborosException(queryResult.getCause(), cause -> new InternalException("查询数据失败", cause));
    }

    return PagedWebApiResult.ok(countResult.get(), queryResult.get());
  }

  @Override
  public PagedWebApiResult<WorkflowModel> searchAvailableByPage(String keyword, String category, int page, int pageSize) throws OuroborosException {
    TypedDataModel<String, WorkflowModel> m = this.getDataModel();
    Map<String, Object> where = new HashMap<String, Object>() {{
      put("status", new HashMap<String, Object>() {{
        put("=", "deployed");
      }});
      if (ObjectUtils.isNotEmpty(keyword)) {
        put("name", new HashMap<String, Object>() {{
          put("like", "%" + keyword + "%");
        }});
      }
      if (ObjectUtils.isNotEmpty(category)) {
        put("category", new HashMap<String, Object>() {{
          put("=", category);
        }});
      }
    }};

    Try<Long> countResult = m.count(where);
    if (countResult.isFailure()) {
      throw OuroborosExceptionMapper.toOuroborosException(countResult.getCause(), cause -> new InternalException("查询总数失败", cause));
    }

    Try<java.util.List<WorkflowModel>> queryResult = m.query(null, where, "saveAt desc", (page - 1) * pageSize, pageSize);
    if (queryResult.isFailure()) {
      throw OuroborosExceptionMapper.toOuroborosException(queryResult.getCause(), cause -> new InternalException("查询数据失败", cause));
    }

    return PagedWebApiResult.ok(countResult.get(), queryResult.get());
  }

  @Override
  @Transactional
  public void deploy(String id) throws OuroborosException {
    WorkflowModel workflowModel = getByPrimaryKey(id);
    ApprovalWorkflowDefinitionDocument definitionDocument = ApprovalWorkflowDefinitionDocument.decode(workflowModel.getJson())
        .getOrElseThrow(cause -> new InternalException("部署流程失败", cause));
    ApprovalWorkflowDefinitionMetadata definition = definitionDocument.metadata();
    if (!definition.isEnabledEffective()) {
      throw new BusinessException("审批流定义已禁用，不能部署: " + definition.getName());
    }
    if (Objects.equals(workflowModel.getStatus(), "deployed")) {
      return;
    }
    com.ouroboros.workflow.approval.form.CustomFormBindingValidator.validate(definition, flowFormProviders);
    Try<String> definitionJson = definitionDocument.toRuntimeJson();
    if (definitionJson.isFailure()) {
      throw new InternalException("部署流程失败", definitionJson.getCause());
    }

    Try<FlowDeploymentResult> deployResult = flowManageService.deployForRuntime(
        workflowModel.getId(), definitionJson.get());
    if (deployResult.isFailure()) {
      Throwable cause = deployResult.getCause();
      if (cause instanceof OuroborosException) {
        OuroborosException oe = (OuroborosException) cause;
        throw oe;
      }
      throw new InternalException("部署流程失败", cause);
    }
    FlowDeploymentResult result = deployResult.get();
    Map<String, Object> updateData = new HashMap<>();
    updateData.put("status", "deployed");
    updateData.put("processDefinitionId", result.processDefinitionId());
    updateData.put("executionMode", result.executionMode());
    updateData.put("integrationMode", result.integrationMode());
    updateData.put("engineAdapterId", result.adapterId());
    updateData.put("engineMappingId", result.mappingId());
    updateData.put("engineMappingVersion", result.mappingVersion());
    updateData.put("externalDefinitionRef", result.externalDefinitionRef());
    this.partialUpdate(id, updateData);
  }

  @Override
  @Transactional
  public void offline(String id) throws OuroborosException {
    WorkflowModel existing = getByPrimaryKey(id);
    if (existing == null) {
      throw new BusinessException("流程不存在");
    }
    offlineProcessDefinition(existing);
    this.partialUpdate(id, new HashMap<String, Object>() {{
      put("status", "offline");
    }});
  }

  @Override
  @Transactional
  public void reset(String id) throws OuroborosException {
    WorkflowModel existing = getByPrimaryKey(id);
    if (existing == null) {
      throw new BusinessException("流程不存在");
    }
    if (!isMetadataLayerModel(existing)) {
      throw new BusinessException("当前流程定义不允许重置: " + id);
    }
    ApprovalWorkflowDefinitionMetadata definition = findSourceDefinition(id)
        .orElseThrow(() -> new BusinessException("找不到可恢复的初始流程配置: " + id));
    WorkflowModel resetModel = workflowModelProjector.createMetadataLayerDraft(definition)
        .getOrElseThrow(cause -> new BusinessException("初始流程配置不合法: " + id, cause));
    if (!id.equals(resetModel.getId())) {
      throw new BusinessException("初始流程配置 ID 与当前流程不一致: " + id);
    }
    offlineProcessDefinition(existing);
    updateLayerModel(id, resetModel);
  }

  protected Boolean updateRuntimeModel(String id, WorkflowModel model) throws OuroborosException {
    return super.update(id, model);
  }

  protected Boolean partialUpdateRuntimeModel(String id, Map<String, Object> map) throws OuroborosException {
    return super.partialUpdate(id, map);
  }

  protected WorkflowModel insertLayerModel(WorkflowModel definition) throws OuroborosException {
    return super.add(definition);
  }

  protected WorkflowModel insertRuntimeModel(WorkflowModel model) throws OuroborosException {
    return super.add(model);
  }

  protected Boolean updateLayerModel(String id, WorkflowModel definition) throws OuroborosException {
    return super.update(id, definition);
  }

  protected List<WorkflowModel> queryLayerModels() throws OuroborosException {
    TypedDataModel<String, WorkflowModel> m = this.getDataModel();
    Map<String, Object> where = new HashMap<String, Object>() {{
      put("source", new HashMap<String, Object>() {{
        put("=", WorkflowModel.SOURCE_METADATA_LAYER);
      }});
    }};
    Try<List<WorkflowModel>> queryResult = m.query(null, where, "id asc", 0, Integer.MAX_VALUE);
    if (queryResult.isFailure()) {
      throw OuroborosExceptionMapper.toOuroborosException(
          queryResult.getCause(),
          cause -> new InternalException("查询元数据层审批流程失败", cause));
    }
    return queryResult.get();
  }

  private Optional<ApprovalWorkflowDefinitionMetadata> findSourceDefinition(String id) throws OuroborosException {
    for (ApprovalWorkflowDesignTimeDefinitionProvider provider : designTimeDefinitionProviders) {
      Optional<ApprovalWorkflowDefinitionMetadata> definition = provider.findDefinition(id);
      if (definition.isPresent()) {
        return definition;
      }
    }
    return Optional.empty();
  }

  private void offlineProcessDefinition(WorkflowModel model) throws OuroborosException {
    if (model == null || StringUtils.isBlank(model.getProcessDefinitionId())) {
      return;
    }
    Try<Void> offlineResult = flowManageService.offline(model.getProcessDefinitionId());
    if (offlineResult.isFailure()) {
      Throwable cause = offlineResult.getCause();
      if (cause instanceof OuroborosException) {
        throw (OuroborosException) cause;
      }
      throw new InternalException("下线流程失败", cause);
    }
  }

  private List<WorkflowModel> toLayeredWorkflowModels(List<ApprovalWorkflowDefinitionMetadata> definitions) {
    return definitions.stream()
        .map(definition -> workflowModelProjector.createMetadataLayerDraft(definition)
            .getOrElseThrow(cause -> new BusinessException("静态审批流定义不合法", cause)))
        .collect(Collectors.toList());
  }

  private void validateLayerSnapshot(List<WorkflowModel> definitions) {
    Set<String> ids = new HashSet<>();
    for (WorkflowModel definition : definitions) {
      validateLayerModel(definition);
      if (!ids.add(definition.getId())) {
        throw new BusinessException("静态审批流定义重复: " + definition.getId());
      }
    }
  }

  private void validateLayerModel(WorkflowModel definition) {
    if (definition == null) {
      throw new BusinessException("静态审批流定义不能为空");
    }
    if (StringUtils.isBlank(definition.getId())) {
      throw new BusinessException("静态审批流定义 ID 不能为空");
    }
    if (!WorkflowModel.SOURCE_METADATA_LAYER.equals(definition.getSource())) {
      throw new BusinessException("静态审批流定义 source 必须来自元数据层: " + definition.getId());
    }
    if (version(definition) == null || version(definition) <= 0) {
      throw new BusinessException("静态审批流定义 version 必须为正整数: " + definition.getId());
    }
    if (Boolean.TRUE.equals(definition.getAdopted())) {
      throw new BusinessException("元数据层审批流定义不能以 adopted=true 生效: " + definition.getId());
    }
  }

  private int compareVersion(WorkflowModel existing, WorkflowModel definition) {
    int existingVersion = version(existing) == null ? 0 : version(existing);
    return Integer.compare(version(definition), existingVersion);
  }

  private boolean isSameLayerContent(WorkflowModel existing, WorkflowModel definition) {
    return Objects.equals(existing.getName(), definition.getName())
        && Objects.equals(existing.getIcon(), definition.getIcon())
        && Objects.equals(existing.getCategory(), definition.getCategory())
        && Objects.equals(existing.getDescription(), definition.getDescription())
        && Objects.equals(existing.getJson(), definition.getJson());
  }

  private String nextLayerStatus(WorkflowModel existing, WorkflowModel definition) {
    return "draft".equalsIgnoreCase(existing.getStatus()) ? definition.getStatus() : "updated";
  }

  /**
   * 删除未接管的静态定义：先按既有下线机制挂起引擎部署，再标记 offline。
   *
   * <p>此前只改 DB 状态，引擎部署仍处于 active，任何人仍能按 flowKey 发起新实例；而发起侧
   * （{@code ApprovalWorkflowInstanceController}）只用 DB 状态过滤"可发起列表"，不读 WorkflowModel，
   * 因此"删除后阻止新发起"的承诺落空。保留 Flowable 历史部署/实例/审计由 {@code offline} 本身保证。
   */
  private boolean retireMissingLayerDefinition(WorkflowModel existing) throws OuroborosException {
    if (Boolean.TRUE.equals(existing.getAdopted()) || "offline".equalsIgnoreCase(existing.getStatus())) {
      return false;
    }
    offlineProcessDefinition(existing);
    Map<String, Object> updateData = new HashMap<>();
    updateData.put("status", "offline");
    return Boolean.TRUE.equals(partialUpdateRuntimeModel(existing.getId(), updateData));
  }

  private void applyRuntimeAdoption(WorkflowModel existing, WorkflowModel model) {
    if (isMetadataLayerModel(existing)) {
      model.setSource(WorkflowModel.SOURCE_METADATA_LAYER);
      model.setAdopted(containsDefinitionEdit(existing, model) ? true : existing.getAdopted());
    }
  }

  private void applyRuntimeDefaults(WorkflowModel model) {
    if (StringUtils.isBlank(model.getSource())) {
      model.setSource(WorkflowModel.SOURCE_INSTANCE);
    }
    model.setVersion(definitionVersion(model));
    if (model.getAdopted() == null) {
      model.setAdopted(false);
    }
  }

  private Integer definitionVersion(WorkflowModel model) {
    return definitionVersion(model.getJson());
  }

  private Integer definitionVersion(String definitionJson) {
    return ApprovalWorkflowDefinitionDocument.decode(definitionJson)
        .map(ApprovalWorkflowDefinitionDocument::metadata)
        .map(ApprovalWorkflowDefinitionMetadata::getVersion)
        .getOrElseThrow(cause -> new BusinessException("审批流定义不合法", cause));
  }

  private boolean isMetadataLayerModel(WorkflowModel model) {
    return model != null && WorkflowModel.SOURCE_METADATA_LAYER.equals(model.getSource());
  }

  private Integer version(WorkflowModel model) {
    return model == null ? null : model.getVersion();
  }

  private boolean containsDefinitionEdit(Map<String, Object> map) {
    return map != null && (
        map.containsKey("name")
            || map.containsKey("icon")
            || map.containsKey("category")
            || map.containsKey("description")
            || map.containsKey("json")
    );
  }

  private boolean containsDefinitionEdit(WorkflowModel existing, WorkflowModel model) {
    return !Objects.equals(existing.getName(), model.getName())
        || !Objects.equals(existing.getIcon(), model.getIcon())
        || !Objects.equals(existing.getCategory(), model.getCategory())
        || !Objects.equals(existing.getDescription(), model.getDescription())
        || !Objects.equals(existing.getJson(), model.getJson());
  }

}
