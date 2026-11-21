package com.ouroboros.businessmodel.action;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.ouroboros.businessmodel.event.BusinessEventEmissionContext;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.event.BusinessEventPort;
import com.ouroboros.businessmodel.event.BusinessEventSubject;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelMutation;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

/** Converts only the current Action's own verified mutations to bounded model-scoped facts. */
public final class BusinessActionMutationEventEmitter {
  private final Supplier<BusinessEventPort> events;
  private final BusinessMutationEventProperties limits;

  public BusinessActionMutationEventEmitter(Supplier<BusinessEventPort> events, BusinessMutationEventProperties limits) {
    this.events = Objects.requireNonNull(events);
    this.limits = Objects.requireNonNull(limits);
  }

  void emit(String modelFullName, BusinessActionMutationEventPlan plan, List<DataModelMutation> mutations) {
    if (plan == null) {
      return;
    }
    for (var mutation : mutations) {
      if (!modelFullName.equalsIgnoreCase(mutation.modelFullName()) || mutation.affectedRows() <= 0) {
        continue;
      }
      String eventName = plan.eventNames().get(mutation.operation());
      if (eventName == null) {
        throw new BusinessException("默认业务事件无法识别写入操作：" + mutation.operation());
      }
      if (mutation.affectedRows() != mutation.objectIds().size()
          || new HashSet<>(mutation.objectIds()).size() != mutation.objectIds().size()) {
        throw new BusinessException("默认业务事件无法证明实际写入对象数量");
      }
      var model = DataModelCenter.getDataModel(modelFullName)
          .orElseThrow(() -> new BusinessException("默认业务事件的对象模型不可用"));
      if (model.getPrimaryKeys().size() != 1 || !"id".equalsIgnoreCase(model.getPrimaryKeys().get(0).getName())) {
        throw new BusinessException("默认业务事件要求唯一 id 主键");
      }
      var idType = model.getPrimaryKeys().get(0).getValueType();
      var ids = new ArrayList<Object>();
      int emptyBytes = size(payload(mutation.operation(), List.of()));
      int idBytes = 0;
      for (String rawId : mutation.objectIds()) {
        Object id = idType.convert(rawId);
        if (id == null) {
          throw new BusinessException("默认业务事件的对象身份无效");
        }
        int bytes = size(id);
        int count = ids.size() + 1;
        int totalBytes = emptyBytes + idBytes + bytes + count - 1 + Integer.toString(count).length() - 1;
        if (!ids.isEmpty() && (count > limits.getMaxIds() || totalBytes > limits.getMaxPayloadBytes())) {
          record(modelFullName, eventName, mutation.operation(), ids);
          ids = new ArrayList<>();
          idBytes = 0;
        }
        if (emptyBytes + bytes > limits.getMaxPayloadBytes()) {
          throw new BusinessException("默认业务事件单个对象身份超过载荷上限");
        }
        ids.add(id);
        idBytes += bytes;
      }
      if (!ids.isEmpty()) {
        record(modelFullName, eventName, mutation.operation(), ids);
      }
    }
  }

  private void record(String model, String event, String operation, List<Object> ids) {
    var payload = payload(operation, List.copyOf(ids));
    if (size(payload) > limits.getMaxPayloadBytes()) {
      throw new BusinessException("默认业务事件超过载荷上限");
    }
    events.get().emit(new BusinessEventIdentity(model, event), BusinessEventSubject.model(model), payload,
        BusinessEventEmissionContext.fromAction());
  }

  private Map<String, Object> payload(String operation, List<Object> ids) {
    return Map.of("operation", operation, "objectIds", ids, "affectedCount", ids.size());
  }

  private int size(Object value) {
    return Json.tryToJsonString(value).getOrElseThrow(error -> new BusinessException("默认业务事件无法序列化", error))
        .getBytes(StandardCharsets.UTF_8).length;
  }
}
