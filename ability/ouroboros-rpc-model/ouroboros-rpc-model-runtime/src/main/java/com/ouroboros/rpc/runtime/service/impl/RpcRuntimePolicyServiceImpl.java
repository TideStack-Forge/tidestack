package com.ouroboros.rpc.runtime.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.service.RpcRuntimePolicyService;

@Service
public class RpcRuntimePolicyServiceImpl implements RpcRuntimePolicyService {
  private static final Set<String> ALLOWED_AVAILABILITY_STATUS = new LinkedHashSet<>(Arrays.asList("HEALTHY", "UNAVAILABLE"));

  @Autowired
  private TypedDataModel<String, RpcRuntimePolicyModel> dataModel;

  @Override
  public RpcRuntimePolicyModel getPolicy(String modelName) {
    requireModelName(modelName);

    RpcRuntimePolicyModel policy = unwrap(getDataModel().get(modelName), "读取 RPC 运行时策略失败");
    return policy == null ? defaultPolicy(modelName) : normalizePolicy(modelName, policy, false);
  }

  @Override
  public RpcRuntimePolicyModel updatePolicy(String modelName, RpcRuntimePolicyModel policy) {
    requireModelName(modelName);

    RpcRuntimePolicyModel existing = unwrap(getDataModel().get(modelName), "读取 RPC 运行时策略失败");
    RpcRuntimePolicyModel normalized = normalizePolicy(modelName, policy, true);
    if (existing != null) {
      normalized.setTenantId(existing.getTenantId());
    }
    normalized.setUpdatedAt(LocalDateTime.now());

    if (existing == null) {
      unwrap(getDataModel().insert(normalized), "写入 RPC 运行时策略失败");
    } else {
      unwrap(getDataModel().update(modelName, normalized), "更新 RPC 运行时策略失败");
    }
    return normalized;
  }

  protected TypedDataModel<String, RpcRuntimePolicyModel> getDataModel() {
    return dataModel;
  }

  protected RpcRuntimePolicyModel defaultPolicy(String modelName) {
    RpcRuntimePolicyModel policy = new RpcRuntimePolicyModel();
    policy.setModelName(modelName);
    policy.setEnabled(true);
    policy.setLoggingEnabled(false);
    policy.setLogRequestBody(false);
    policy.setLogResponseBody(false);
    policy.setMetricsEnabled(false);
    policy.setAvailabilityEnabled(false);
    return policy;
  }

  protected RpcRuntimePolicyModel normalizePolicy(String modelName, RpcRuntimePolicyModel source) {
    return normalizePolicy(modelName, source, false);
  }

  private RpcRuntimePolicyModel normalizePolicy(String modelName, RpcRuntimePolicyModel source, boolean strictValidation) {
    RpcRuntimePolicyModel normalized = defaultPolicy(modelName);
    if (source == null) {
      return normalized;
    }

    normalized.setTenantId(source.getTenantId());
    normalized.setEnabled(source.getEnabled() == null ? normalized.getEnabled() : source.getEnabled());
    normalized.setLoggingEnabled(source.getLoggingEnabled() == null ? normalized.getLoggingEnabled() : source.getLoggingEnabled());
    normalized.setLogRequestBody(source.getLogRequestBody() == null ? normalized.getLogRequestBody() : source.getLogRequestBody());
    normalized.setLogResponseBody(source.getLogResponseBody() == null ? normalized.getLogResponseBody() : source.getLogResponseBody());
    normalized.setMaskHeaders(source.getMaskHeaders());
    normalized.setMaskFields(source.getMaskFields());
    normalized.setMetricsEnabled(source.getMetricsEnabled() == null ? normalized.getMetricsEnabled() : source.getMetricsEnabled());
    normalized.setAvailabilityEnabled(source.getAvailabilityEnabled() == null ? normalized.getAvailabilityEnabled() : source.getAvailabilityEnabled());
    normalized.setForcedAvailabilityStatus(normalizeForcedAvailabilityStatus(source.getForcedAvailabilityStatus(), strictValidation));
    normalized.setTimeoutOverrideMs(normalizeTimeoutOverrideMs(source.getTimeoutOverrideMs(), strictValidation));
    normalized.setRetryOverride(normalizeRetryOverride(source.getRetryOverride(), strictValidation));
    normalized.setUpdatedAt(source.getUpdatedAt());
    normalized.setUpdatedBy(source.getUpdatedBy());
    normalized.setNormalizationWarnings(buildNormalizationWarnings(source, normalized, strictValidation));
    return normalized;
  }

  private void requireModelName(String modelName) {
    if (modelName == null || modelName.trim().isEmpty()) {
      throw new IllegalArgumentException("modelName 不能为空");
    }
  }

  private Long normalizeTimeoutOverrideMs(Long timeoutOverrideMs, boolean strictValidation) {
    if (timeoutOverrideMs == null) {
      return null;
    }
    if (timeoutOverrideMs <= 0L) {
      if (strictValidation) {
        throw new ValidationException("timeoutOverrideMs 必须大于 0");
      }
      return null;
    }
    return timeoutOverrideMs;
  }

  private Integer normalizeRetryOverride(Integer retryOverride, boolean strictValidation) {
    if (retryOverride == null) {
      return null;
    }
    if (retryOverride < 0) {
      if (strictValidation) {
        throw new ValidationException("retryOverride 不能小于 0");
      }
      return null;
    }
    return retryOverride;
  }

  private String normalizeForcedAvailabilityStatus(String forcedAvailabilityStatus, boolean strictValidation) {
    if (forcedAvailabilityStatus == null) {
      return null;
    }
    String trimmed = forcedAvailabilityStatus.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    String normalizedStatus = trimmed.toUpperCase();
    if (!ALLOWED_AVAILABILITY_STATUS.contains(normalizedStatus)) {
      if (strictValidation) {
        throw new ValidationException("forcedAvailabilityStatus 仅支持 HEALTHY 或 UNAVAILABLE");
      }
      return null;
    }
    return normalizedStatus;
  }

  private List<String> buildNormalizationWarnings(
      RpcRuntimePolicyModel source,
      RpcRuntimePolicyModel normalized,
      boolean strictValidation
  ) {
    if (strictValidation || source == null) {
      return Collections.emptyList();
    }
    List<String> warnings = new ArrayList<>();
    if (source.getForcedAvailabilityStatus() != null
        && normalized.getForcedAvailabilityStatus() == null
        && !source.getForcedAvailabilityStatus().trim().isEmpty()) {
      warnings.add("forcedAvailabilityStatus 存在非法值，运行时已按空值处理");
    }
    if (source.getTimeoutOverrideMs() != null && normalized.getTimeoutOverrideMs() == null) {
      warnings.add("timeoutOverrideMs 存在非法值，运行时已忽略");
    }
    if (source.getRetryOverride() != null && normalized.getRetryOverride() == null) {
      warnings.add("retryOverride 存在非法值，运行时已忽略");
    }
    return warnings;
  }

  private <T> T unwrap(Try<T> result, String message) {
    return result.getOrElseThrow(cause -> OuroborosExceptionMapper.toOuroborosException(cause, ex -> new InternalException(message, ex)));
  }
}
