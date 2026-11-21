package com.ouroboros.workflow.approval.dynamicform.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.Dates;
import com.ouroboros.workflow.approval.dynamicform.model.DynamicFormWorkflowBinding;
import com.ouroboros.workflow.approval.dynamicform.model.DynamicFormWorkflowHistory;

@Service
public class DynamicFormWorkflowRecordService {
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 200;
  private final Object bindingMutationLock = new Object();

  public DynamicFormWorkflowBinding recordInitialForm(String instanceId, String formKey, String formId, String userId,
                                                      Map<String, Object> afterData) {
    DynamicFormWorkflowBinding savedBinding = findOrCreateBinding(instanceId, formKey, formId, userId);
    insertHistory(newHistory(
        savedBinding,
        null,
        DynamicFormWorkflowHistory.ACTION_START,
        Collections.emptyMap(),
        snapshot(afterData),
        snapshot(afterData),
        userId));
    return savedBinding;
  }

  public DynamicFormWorkflowHistory recordTaskFormUpdate(String instanceId, String taskId, String formKey,
                                                         String formId, String userId,
                                                         Map<String, Object> beforeData,
                                                         Map<String, Object> afterData,
                                                         Set<String> submittedFieldNames) {
    DynamicFormWorkflowBinding binding = findOrCreateBinding(instanceId, formKey, formId, userId);
    updateBinding(binding.getId(), userId);
    return insertHistory(newHistory(
        binding,
        taskId,
        DynamicFormWorkflowHistory.ACTION_UPDATE,
        snapshot(beforeData),
        snapshot(afterData),
        changedData(beforeData, afterData, submittedFieldNames),
        userId));
  }

  public List<DynamicFormWorkflowBinding> listBindings(String instanceId, String formKey, String formId) {
    return listBindings(instanceId, formKey, formId, 0, DEFAULT_PAGE_SIZE);
  }

  public List<DynamicFormWorkflowBinding> listBindings(String instanceId, String formKey, String formId,
                                                       Integer offset, Integer limit) {
    Map<String, Object> where = buildScopedWhere(instanceId, null, formKey, formId);
    return queryBindings(where, safeOffset(offset), safeLimit(limit));
  }

  public Long countBindings(String instanceId, String formKey, String formId) {
    return countBindings(buildScopedWhere(instanceId, null, formKey, formId));
  }

  public List<DynamicFormWorkflowHistory> listHistory(String instanceId, String taskId, String formKey, String formId) {
    return listHistory(instanceId, taskId, formKey, formId, 0, DEFAULT_PAGE_SIZE);
  }

  public List<DynamicFormWorkflowHistory> listHistory(String instanceId, String taskId, String formKey, String formId,
                                                      Integer offset, Integer limit) {
    Map<String, Object> where = buildScopedWhere(instanceId, taskId, formKey, formId);
    return queryHistory(where, safeOffset(offset), safeLimit(limit));
  }

  public Long countHistory(String instanceId, String taskId, String formKey, String formId) {
    return countHistory(buildScopedWhere(instanceId, taskId, formKey, formId));
  }

  protected DynamicFormWorkflowBinding findOrCreateBinding(String instanceId, String formKey, String formId,
                                                           String userId) {
    synchronized (bindingMutationLock) {
      Optional<DynamicFormWorkflowBinding> existing = findBinding(instanceId, formKey, formId);
      if (existing.isPresent()) {
        return existing.get();
      }
      try {
        return insertBinding(newBinding(instanceId, formKey, formId, userId));
      } catch (RuntimeException e) {
        if (!isUniqueConstraintViolation(e)) {
          throw e;
        }
        return findBinding(instanceId, formKey, formId)
            .orElseThrow(() -> new PlatformException("动态表单流程绑定已存在但回查失败", e));
      }
    }
  }

  protected DynamicFormWorkflowBinding insertBinding(DynamicFormWorkflowBinding binding) {
    var result = bindingModel().insert(binding);
    if (result.isSuccess()) {
      return result.get();
    }
    throw new PlatformException("记录动态表单流程绑定失败", result.getCause());
  }

  protected DynamicFormWorkflowHistory insertHistory(DynamicFormWorkflowHistory history) {
    var result = historyModel().insert(history);
    if (result.isSuccess()) {
      return result.get();
    }
    throw new PlatformException("记录动态表单流程历史失败", result.getCause());
  }

  protected void updateBinding(String bindingId, String userId) {
    if (StringUtils.isBlank(bindingId)) {
      return;
    }
    Map<String, Object> data = new HashMap<>();
    data.put("updatedAt", Dates.now());
    data.put("updatedBy", userId);
    var result = bindingModel().update(bindingId, data);
    if (result.isFailure()) {
      throw new PlatformException("更新动态表单流程绑定失败", result.getCause());
    }
  }

  protected Optional<DynamicFormWorkflowBinding> findBinding(String instanceId, String formKey, String formId) {
    List<DynamicFormWorkflowBinding> bindings = queryBindings(buildWhere(instanceId, null, formKey, formId), 0, 1);
    return bindings.stream().findFirst();
  }

  protected List<DynamicFormWorkflowBinding> queryBindings(Map<String, Object> where, Integer offset, Integer limit) {
    var result = bindingModel().query(null, where, "createdAt asc", offset, limit);
    if (result.isSuccess()) {
      return result.get();
    }
    throw new PlatformException("查询动态表单流程绑定失败", result.getCause());
  }

  protected Long countBindings(Map<String, Object> where) {
    var result = bindingModel().count(where);
    if (result.isSuccess()) {
      return result.get();
    }
    throw new PlatformException("统计动态表单流程绑定失败", result.getCause());
  }

  protected List<DynamicFormWorkflowHistory> queryHistory(Map<String, Object> where, Integer offset, Integer limit) {
    var result = historyModel().query(null, where, "createdAt asc", offset, limit);
    if (result.isSuccess()) {
      return result.get();
    }
    throw new PlatformException("查询动态表单流程历史失败", result.getCause());
  }

  protected Long countHistory(Map<String, Object> where) {
    var result = historyModel().count(where);
    if (result.isSuccess()) {
      return result.get();
    }
    throw new PlatformException("统计动态表单流程历史失败", result.getCause());
  }

  protected TypedDataModel<String, DynamicFormWorkflowBinding> bindingModel() {
    return TypedDataModelCenter.getDataModel(String.class, DynamicFormWorkflowBinding.class)
        .orElseThrow(() -> new PlatformException("动态表单流程绑定模型不存在"));
  }

  protected TypedDataModel<String, DynamicFormWorkflowHistory> historyModel() {
    return TypedDataModelCenter.getDataModel(String.class, DynamicFormWorkflowHistory.class)
        .orElseThrow(() -> new PlatformException("动态表单流程历史模型不存在"));
  }

  private DynamicFormWorkflowBinding newBinding(String instanceId, String formKey, String formId, String userId) {
    DynamicFormWorkflowBinding binding = new DynamicFormWorkflowBinding();
    binding.setInstanceId(instanceId);
    binding.setFormKey(formKey);
    binding.setFormId(formId);
    binding.setCreatedAt(Dates.now());
    binding.setCreatedBy(userId);
    binding.setUpdatedAt(Dates.now());
    binding.setUpdatedBy(userId);
    return binding;
  }

  private DynamicFormWorkflowHistory newHistory(DynamicFormWorkflowBinding binding, String taskId, String action,
                                                Map<String, Object> beforeData, Map<String, Object> afterData,
                                                Map<String, Object> changedData, String userId) {
    DynamicFormWorkflowHistory history = new DynamicFormWorkflowHistory();
    history.setBindingId(binding.getId());
    history.setInstanceId(binding.getInstanceId());
    history.setTaskId(taskId);
    history.setFormKey(binding.getFormKey());
    history.setFormId(binding.getFormId());
    history.setAction(action);
    history.setBeforeData(beforeData);
    history.setAfterData(afterData);
    history.setChangedData(changedData);
    history.setCreatedAt(Dates.now());
    history.setCreatedBy(userId);
    return history;
  }

  private Map<String, Object> buildWhere(String instanceId, String taskId, String formKey, String formId) {
    Map<String, Object> where = new LinkedHashMap<>();
    putIfPresent(where, "instanceId", instanceId);
    putIfPresent(where, "taskId", taskId);
    putIfPresent(where, "formKey", formKey);
    putIfPresent(where, "formId", formId);
    return where;
  }

  private Map<String, Object> buildScopedWhere(String instanceId, String taskId, String formKey, String formId) {
    Map<String, Object> where = buildWhere(instanceId, taskId, formKey, formId);
    if (!where.containsKey("instanceId") && !where.containsKey("formId")) {
      throw new PlatformException("查询动态表单流程记录必须提供流程实例ID或表单数据ID");
    }
    return where;
  }

  private int safeOffset(Integer offset) {
    return offset == null || offset < 0 ? 0 : offset;
  }

  private int safeLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(limit, MAX_PAGE_SIZE);
  }

  private boolean isUniqueConstraintViolation(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor != null) {
      if (cursor instanceof UniqueConstraintViolationException) {
        return true;
      }
      cursor = cursor.getCause();
    }
    return false;
  }

  private void putIfPresent(Map<String, Object> where, String name, String value) {
    if (StringUtils.isNotBlank(value)) {
      where.put(name, value);
    }
  }

  private Map<String, Object> snapshot(Map<String, Object> data) {
    return data == null ? Collections.emptyMap() : new LinkedHashMap<>(data);
  }

  private Map<String, Object> changedData(Map<String, Object> beforeData, Map<String, Object> afterData,
                                          Set<String> submittedFieldNames) {
    Map<String, Object> before = snapshot(beforeData);
    Map<String, Object> after = snapshot(afterData);
    Set<String> fieldNames = submittedFieldNames == null || submittedFieldNames.isEmpty()
        ? after.keySet()
        : submittedFieldNames;
    return fieldNames.stream()
        .filter(fieldName -> !Objects.equals(before.get(fieldName), after.get(fieldName)))
        .collect(Collectors.toMap(fieldName -> fieldName, after::get, (left, right) -> right, LinkedHashMap::new));
  }
}
