package com.ouroboros.rpc.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class RpcInvocationRequest {
  private final String modelName;
  private final Map<String, Object> parameters;
  private final boolean internalCall;
  private final String purpose;
  private final Map<String, Object> options;

  public RpcInvocationRequest(String modelName, Map<String, Object> parameters) {
    this(modelName, parameters, false, null, Collections.emptyMap());
  }

  public RpcInvocationRequest(
      String modelName,
      Map<String, Object> parameters,
      boolean internalCall,
      String purpose,
      Map<String, Object> options
  ) {
    this.modelName = modelName;
    this.parameters = parameters == null
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    this.internalCall = internalCall;
    this.purpose = purpose;
    this.options = options == null
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(new LinkedHashMap<>(options));
  }

  public String getModelName() {
    return modelName;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public boolean isInternalCall() {
    return internalCall;
  }

  public String getPurpose() {
    return purpose;
  }

  public Map<String, Object> getOptions() {
    return options;
  }

  public Optional<Object> getOption(String key) {
    return Optional.ofNullable(options.get(key));
  }

  public <T> Optional<T> getOption(Class<T> type, String key) {
    return getOption(key).map(value -> type.isInstance(value) ? type.cast(value) : null);
  }
}
