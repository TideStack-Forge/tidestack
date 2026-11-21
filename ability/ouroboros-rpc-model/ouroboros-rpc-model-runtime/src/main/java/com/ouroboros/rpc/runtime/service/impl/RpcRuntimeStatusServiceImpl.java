package com.ouroboros.rpc.runtime.service.impl;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.rpc.runtime.model.RpcRuntimeStatusModel;
import com.ouroboros.rpc.runtime.service.RpcRuntimeStatusService;

@Service
public class RpcRuntimeStatusServiceImpl implements RpcRuntimeStatusService {

  public static final String AVAILABILITY_HEALTHY = "HEALTHY";

  @Autowired
  private TypedDataModel<String, RpcRuntimeStatusModel> dataModel;

  @Override
  public RpcRuntimeStatusModel getStatus(String modelName) {
    requireModelName(modelName);

    RpcRuntimeStatusModel status = unwrap(getDataModel().get(modelName), "读取 RPC 运行时状态失败");
    return status == null ? defaultStatus(modelName) : normalizeStatus(modelName, status);
  }

  @Override
  public RpcRuntimeStatusModel saveStatus(String modelName, RpcRuntimeStatusModel status) {
    requireModelName(modelName);

    RpcRuntimeStatusModel existing = unwrap(getDataModel().get(modelName), "读取 RPC 运行时状态失败");
    RpcRuntimeStatusModel merged = mergeStatus(modelName, existing, status);

    if (existing == null) {
      unwrap(getDataModel().insert(merged), "写入 RPC 运行时状态失败");
    } else {
      unwrap(getDataModel().update(modelName, merged), "更新 RPC 运行时状态失败");
    }
    return merged;
  }

  @Override
  public RpcRuntimeStatusModel resetStatus(String modelName) {
    requireModelName(modelName);

    RpcRuntimeStatusModel existing = unwrap(getDataModel().get(modelName), "读取 RPC 运行时状态失败");
    RpcRuntimeStatusModel reset = defaultStatus(modelName);
    if (existing != null) {
      reset.setTenantId(existing.getTenantId());
    }

    if (existing == null) {
      unwrap(getDataModel().insert(reset), "写入 RPC 运行时状态失败");
    } else {
      unwrap(getDataModel().update(modelName, reset), "重置 RPC 运行时状态失败");
    }
    return reset;
  }

  protected TypedDataModel<String, RpcRuntimeStatusModel> getDataModel() {
    return dataModel;
  }

  protected RpcRuntimeStatusModel defaultStatus(String modelName) {
    RpcRuntimeStatusModel status = new RpcRuntimeStatusModel();
    status.setModelName(modelName);
    status.setAvailability(AVAILABILITY_HEALTHY);
    status.setConsecutiveFailureCount(0L);
    status.setSuccessCount(0L);
    status.setFailureCount(0L);
    status.setAvgLatencyMs(0L);
    return status;
  }

  protected RpcRuntimeStatusModel normalizeStatus(String modelName, RpcRuntimeStatusModel source) {
    RpcRuntimeStatusModel normalized = defaultStatus(modelName);
    if (source == null) {
      return normalized;
    }

    normalized.setTenantId(source.getTenantId());
    normalized.setAvailability(source.getAvailability() == null ? normalized.getAvailability() : source.getAvailability());
    normalized.setLastHealthCheckAt(source.getLastHealthCheckAt());
    normalized.setLastHealthCheckSuccess(source.getLastHealthCheckSuccess());
    normalized.setLastInvokeAt(source.getLastInvokeAt());
    normalized.setLastSuccessAt(source.getLastSuccessAt());
    normalized.setLastFailureAt(source.getLastFailureAt());
    normalized.setLastFailureReason(source.getLastFailureReason());
    normalized.setConsecutiveFailureCount(source.getConsecutiveFailureCount() == null ? normalized.getConsecutiveFailureCount() : source.getConsecutiveFailureCount());
    normalized.setSuccessCount(source.getSuccessCount() == null ? normalized.getSuccessCount() : source.getSuccessCount());
    normalized.setFailureCount(source.getFailureCount() == null ? normalized.getFailureCount() : source.getFailureCount());
    normalized.setAvgLatencyMs(source.getAvgLatencyMs() == null ? normalized.getAvgLatencyMs() : source.getAvgLatencyMs());
    return normalized;
  }

  protected RpcRuntimeStatusModel mergeStatus(String modelName, RpcRuntimeStatusModel existing, RpcRuntimeStatusModel update) {
    RpcRuntimeStatusModel merged = existing == null ? defaultStatus(modelName) : normalizeStatus(modelName, existing);
    if (update == null) {
      return merged;
    }

    merged.setAvailability(update.getAvailability() == null ? merged.getAvailability() : update.getAvailability());
    merged.setLastHealthCheckAt(update.getLastHealthCheckAt() == null ? merged.getLastHealthCheckAt() : update.getLastHealthCheckAt());
    merged.setLastHealthCheckSuccess(update.getLastHealthCheckSuccess() == null ? merged.getLastHealthCheckSuccess() : update.getLastHealthCheckSuccess());
    merged.setLastInvokeAt(update.getLastInvokeAt() == null ? merged.getLastInvokeAt() : update.getLastInvokeAt());
    merged.setLastSuccessAt(update.getLastSuccessAt() == null ? merged.getLastSuccessAt() : update.getLastSuccessAt());
    merged.setLastFailureAt(update.getLastFailureAt() == null ? merged.getLastFailureAt() : update.getLastFailureAt());
    merged.setLastFailureReason(update.getLastFailureReason() == null ? merged.getLastFailureReason() : update.getLastFailureReason());
    merged.setConsecutiveFailureCount(update.getConsecutiveFailureCount() == null ? merged.getConsecutiveFailureCount() : update.getConsecutiveFailureCount());
    merged.setSuccessCount(update.getSuccessCount() == null ? merged.getSuccessCount() : update.getSuccessCount());
    merged.setFailureCount(update.getFailureCount() == null ? merged.getFailureCount() : update.getFailureCount());
    merged.setAvgLatencyMs(update.getAvgLatencyMs() == null ? merged.getAvgLatencyMs() : update.getAvgLatencyMs());
    return merged;
  }

  private void requireModelName(String modelName) {
    if (modelName == null || modelName.trim().isEmpty()) {
      throw new IllegalArgumentException("modelName 不能为空");
    }
  }

  private <T> T unwrap(Try<T> result, String message) {
    return result.getOrElseThrow(cause -> OuroborosExceptionMapper.toOuroborosException(cause, ex -> new InternalException(message, ex)));
  }
}
