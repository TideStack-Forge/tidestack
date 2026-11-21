package com.ouroboros.rpc.execution.interceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.rpc.execution.RpcInvocationContext;
import com.ouroboros.rpc.execution.RpcInvocationInterceptor;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.util.Json;
import com.ouroboros.util.JsonBag;

@Component
@Order(200)
public class LoggingRpcInvocationInterceptor implements RpcInvocationInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(LoggingRpcInvocationInterceptor.class);
  private static final String MASK = "************";

  @Override
  public RpcInvocationResult intercept(RpcInvocationContext context, Chain chain) {
    if (context.isDevelopmentTest()) {
      return chain.proceed(context);
    }
    RpcRuntimePolicyModel policy = RuntimePolicyRpcInvocationInterceptor.getPolicy(context);
    if (!shouldLog(context, policy)) {
      return chain.proceed(context);
    }

    logger.info("RPC调用开始 model={} purpose={} internalCall={}",
        context.getModelName(),
        context.getPurpose(),
        context.isInternalCall());

    RpcInvocationResult result = chain.proceed(context);
    String requestSummary = Json.toJsonString(redact(context, buildRequestSummary(context, policy)));

    if (result != null && result.isSuccess()) {
      logger.info("RPC调用完成 model={} success=true durationMs={} request={} response={}",
          context.getModelName(),
          result.getDurationMs(),
          requestSummary,
          Json.toJsonString(redact(context, buildResponseSummary(result.getPayload(), policy, Boolean.TRUE.equals(policy.getLogResponseBody())))));
    } else {
      logger.warn("RPC调用完成 model={} success=false durationMs={} request={} response={} error={}",
          context.getModelName(),
          result == null ? 0L : result.getDurationMs(),
          requestSummary,
          Json.toJsonString(redact(context, buildResponseSummary(result == null ? null : result.getPayload(), policy, Boolean.TRUE.equals(policy.getLogResponseBody())))),
          redact(context, result == null || result.getError() == null ? null : result.getError().getMessage()));
    }
    return result;
  }

  private Object redact(RpcInvocationContext context, Object value) {
    return context.getResources() == null ? value : context.getResources().redact(value);
  }

  private boolean shouldLog(RpcInvocationContext context, RpcRuntimePolicyModel policy) {
    return context != null
        && policy != null
        && Boolean.TRUE.equals(policy.getLoggingEnabled())
        && !context.isInternalCall();
  }

  private Object buildRequestSummary(RpcInvocationContext context, RpcRuntimePolicyModel policy) {
    Object protocolRequest = context.getProtocolRequest();
    Set<String> fieldMasks = toMaskSet(policy.getMaskFields());
    Set<String> headerMasks = toMaskSet(policy.getMaskHeaders());
    Set<String> requestMasks = mergeMaskSets(fieldMasks, headerMasks);
    if (!(protocolRequest instanceof HttpRPCRequest)) {
      return Boolean.TRUE.equals(policy.getLogRequestBody())
          ? maskValue(context.getParameters(), fieldMasks)
          : Collections.singletonMap("modelName", context.getModelName());
    }

    HttpRPCRequest request = (HttpRPCRequest) protocolRequest;
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("method", request.getMethod());
    summary.put("url", request.getUrl());
    summary.put("headers", maskValue(request.getHeaders(), headerMasks));
    summary.put("query", maskValue(request.getQuery(), requestMasks));
    summary.put("cookies", maskValue(request.getCookies(), requestMasks));
    if (Boolean.TRUE.equals(policy.getLogRequestBody())) {
      summary.put("body", maskValue(request.getBody(), fieldMasks));
    }
    return summary;
  }

  private Object buildResponseSummary(Object payload, RpcRuntimePolicyModel policy, boolean includeBody) {
    if (!includeBody) {
      return Collections.singletonMap("logged", false);
    }
    return maskValue(payload, toMaskSet(policy.getMaskFields()));
  }

  private Set<String> toMaskSet(JsonBag bag) {
    if (bag == null || bag.isNull()) {
      return Collections.emptySet();
    }
    Set<String> values = new LinkedHashSet<>();
    if (bag.isList()) {
      for (Object item : bag.getList()) {
        if (item != null) {
          values.add(item.toString().toLowerCase(Locale.ROOT));
        }
      }
      return values;
    }
    if (bag.isValue() && bag.get() != null) {
      values.add(bag.get().toString().toLowerCase(Locale.ROOT));
      return values;
    }
    if (bag.isMap()) {
      for (Object value : bag.getMap().values()) {
        if (value != null) {
          values.add(value.toString().toLowerCase(Locale.ROOT));
        }
      }
    }
    return values;
  }

  private Set<String> mergeMaskSets(Set<String> first, Set<String> second) {
    if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
      return Collections.emptySet();
    }
    Set<String> merged = new LinkedHashSet<>();
    if (first != null) {
      merged.addAll(first);
    }
    if (second != null) {
      merged.addAll(second);
    }
    return merged;
  }

  private Object maskValue(Object source, Set<String> maskKeys) {
    if (source == null) {
      return null;
    }
    if (source instanceof Map<?, ?> map) {
      Map<String, Object> masked = new LinkedHashMap<>();
      map.forEach((key, value) -> {
        String stringKey = String.valueOf(key);
        if (maskKeys.contains(stringKey.toLowerCase(Locale.ROOT))) {
          masked.put(stringKey, MASK);
        } else {
          masked.put(stringKey, maskValue(value, maskKeys));
        }
      });
      return masked;
    }
    if (source instanceof Collection<?> collection) {
      List<Object> masked = new ArrayList<>();
      collection.forEach(item -> masked.add(maskValue(item, maskKeys)));
      return masked;
    }
    return source;
  }
}
