package com.ouroboros.security.externalauth;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parameters for handling a callback from the external IdP.
 *
 * <p>回调参数以"参数名 → 值列表"原始形态承载：不同渠道的授权码参数名不同（钉钉 {@code authCode}、
 * 企微/飞书 {@code code}），归一化由各 provider 自己完成，平台不再假定通用参数名 {@code code}。
 */
public class CallbackParams {

  private final String state;
  private final Map<String, Object> cachedState;
  private final String correlationId;
  private final Map<String, List<String>> callbackParameters;

  private CallbackParams(
      String state,
      Map<String, Object> cachedState,
      String correlationId,
      Map<String, List<String>> callbackParameters) {
    this.state = state;
    this.cachedState = cachedState != null
        ? Collections.unmodifiableMap(new HashMap<>(cachedState)) : Collections.emptyMap();
    this.correlationId = correlationId;
    this.callbackParameters = immutableParameters(callbackParameters);
  }

  /**
   * Creates parameters for a redirect callback without interpreting provider-specific fields.
   */
  public static CallbackParams fromRedirectCallback(
      String state,
      Map<String, List<String>> callbackParameters,
      Map<String, Object> cachedState,
      String correlationId) {
    return new CallbackParams(state, cachedState, correlationId, callbackParameters);
  }

  public String getState() { return state; }
  public Map<String, Object> getCachedState() { return cachedState; }
  public String getCorrelationId() { return correlationId; }

  public boolean hasCallbackParameters() {
    return !callbackParameters.isEmpty();
  }

  public boolean hasCallbackParameter(String name) {
    return callbackParameters.containsKey(name);
  }

  /**
   * Returns a callback field only when it occurs exactly once. 重复参数按缺失处理，
   * 保证"重复授权码"这类歧义输入 fail-closed。
   */
  public String getSingleCallbackParameter(String name) {
    List<String> values = callbackParameters.get(name);
    return values != null && values.size() == 1 ? values.get(0) : null;
  }

  public Map<String, List<String>> getCallbackParameters() {
    return callbackParameters;
  }

  private static Map<String, List<String>> immutableParameters(
      Map<String, List<String>> callbackParameters) {
    if (callbackParameters == null || callbackParameters.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, List<String>> copy = new LinkedHashMap<>();
    callbackParameters.forEach((name, values) -> copy.put(
        name,
        values == null ? Collections.emptyList() : List.copyOf(values)));
    return Collections.unmodifiableMap(copy);
  }
}
