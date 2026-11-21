package com.ouroboros.workflow.approval.logicflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractSimpleNode;
import com.ouroboros.workflow.approval.CurrentUserSupplier;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;

abstract class ApprovalWorkflowLogicFlowNodeSupport<T> extends AbstractSimpleNode<T> {
  protected ApprovalWorkflowLogicFlowNodeSupport(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
  }

  protected Map<String, Object> requireObjectMap(Object input) {
    if (!(input instanceof Map<?, ?> map)) {
      throw new FlowRuntimeException("审批流节点输入必须是对象", this, Collections.singletonList(input));
    }
    return toObjectMap(map);
  }

  protected Map<String, Object> toObjectMap(Object input) {
    if (!(input instanceof Map<?, ?> map)) {
      return Collections.emptyMap();
    }
    return toObjectMap(map);
  }

  protected Optional<String> stringValue(Map<String, Object> map, String key) {
    return Optional.ofNullable(map.get(key))
        .map(String::valueOf)
        .map(String::trim)
        .filter(value -> !value.isEmpty());
  }

  protected String currentUserId() {
    return CurrentUserSupplier.getCurrentUserId()
        .orElse("anonymousUser");
  }

  protected FlowInstanceService flowInstanceService() {
    return SpringContextUtil.getBean(FlowInstanceService.class);
  }

  protected FlowTaskService flowTaskService() {
    return SpringContextUtil.getBean(FlowTaskService.class);
  }

  protected ApprovalCaseService approvalCaseService() {
    return SpringContextUtil.getBean(ApprovalCaseService.class);
  }

  protected RuntimeException runtimeFailure(String message, Throwable cause, Object input) {
    return new FlowRuntimeException(message, cause, this, Collections.singletonList(input));
  }

  protected void requireMetaValue(String value, String message) {
    if (value == null || value.isBlank()) {
      addError(new NodeDesignException(message, meta));
    }
  }

  private Map<String, Object> toObjectMap(Map<?, ?> raw) {
    Map<String, Object> result = new LinkedHashMap<>();
    raw.forEach((key, value) -> {
      if (key != null) {
        result.put(String.valueOf(key), value);
      }
    });
    return result;
  }

  protected Map<String, List<String>> toAssigneeMap(Object input) {
    if (!(input instanceof Map<?, ?> raw)) {
      return Collections.emptyMap();
    }
    Map<String, List<String>> result = new LinkedHashMap<>();
    raw.forEach((key, value) -> {
      if (key != null) {
        result.put(String.valueOf(key), toAssigneeList(value));
      }
    });
    return result;
  }

  private List<String> toAssigneeList(Object value) {
    if (value instanceof Iterable<?> iterable) {
      List<String> result = new ArrayList<>();
      iterable.forEach(item -> resolveAssigneeValue(item).ifPresent(result::add));
      return result;
    }
    return resolveAssigneeValue(value)
        .map(Collections::singletonList)
        .orElseGet(Collections::emptyList);
  }

  private Optional<String> resolveAssigneeValue(Object item) {
    if (item instanceof Map<?, ?> map) {
      Object value = map.containsKey("value") ? map.get("value") : map.get("id");
      return Optional.ofNullable(value)
          .map(String::valueOf)
          .map(String::trim)
          .filter(text -> !text.isEmpty());
    }
    return Optional.ofNullable(item)
        .map(String::valueOf)
        .map(String::trim)
        .filter(text -> !text.isEmpty());
  }
}
