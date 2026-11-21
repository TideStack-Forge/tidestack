package com.ouroboros.rpc.execution;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.ouroboros.rpc.RPCModelDefinition;
import com.ouroboros.rpc.RPCRequest;

public class RpcInvocationContext {
  private static final String OPTION_ATTRIBUTE_PREFIX = "option:";
  private final RpcInvocationRequest request;
  private final RPCModelDefinition definition;
  private final RpcExecutionResources resources;
  private final long startTimeMillis;
  private final Map<String, Object> attributes = new TreeMap<>(String::compareToIgnoreCase);
  private RPCRequest protocolRequest;
  private Object protocolResponse;
  private Throwable error;
  private RpcInvocationResult result;

  public RpcInvocationContext(RpcInvocationRequest request, RPCModelDefinition definition) {
    this(request, definition, null);
  }

  public RpcInvocationContext(RpcInvocationRequest request, RPCModelDefinition definition, RpcExecutionResources resources) {
    this.resources = resources;
    this.request = request;
    this.definition = definition;
    this.startTimeMillis = System.currentTimeMillis();
  }

  public RpcExecutionResources getResources() { return resources; }

  public boolean isDevelopmentTest() {
    return resources != null && resources.mode() == RpcExecutionResources.Mode.DEVELOPMENT_TEST;
  }

  public RpcInvocationRequest getRequest() {
    return request;
  }

  public RPCModelDefinition getDefinition() {
    return definition;
  }

  public String getModelName() {
    return definition != null ? definition.getName() : request.getModelName();
  }

  public Map<String, Object> getParameters() {
    return request.getParameters();
  }

  public boolean isInternalCall() {
    return request.isInternalCall();
  }

  public String getPurpose() {
    return request.getPurpose();
  }

  public long getStartTimeMillis() {
    return startTimeMillis;
  }

  public Map<String, Object> getOptions() {
    return request.getOptions();
  }

  public Optional<Object> getOption(String key) {
    if (key != null) {
      Optional<Object> override = getAttribute(OPTION_ATTRIBUTE_PREFIX + key);
      if (override.isPresent()) {
        return override;
      }
    }
    return request.getOption(key);
  }

  public <T> Optional<T> getOption(Class<T> type, String key) {
    return getOption(key).map(value -> type.isInstance(value) ? type.cast(value) : null);
  }

  public RpcInvocationContext setAttribute(String key, Object value) {
    attributes.put(key, value);
    return this;
  }

  public Optional<Object> getAttribute(String key) {
    return Optional.ofNullable(attributes.get(key));
  }

  public <T> Optional<T> getAttribute(Class<T> type, String key) {
    return getAttribute(key).map(value -> type.isInstance(value) ? type.cast(value) : null);
  }

  public RpcInvocationContext setOption(String key, Object value) {
    return setAttribute(OPTION_ATTRIBUTE_PREFIX + key, value);
  }

  public RPCRequest getProtocolRequest() {
    return protocolRequest;
  }

  public RpcInvocationContext setProtocolRequest(RPCRequest protocolRequest) {
    this.protocolRequest = protocolRequest;
    return this;
  }

  public Object getProtocolResponse() {
    return protocolResponse;
  }

  public RpcInvocationContext setProtocolResponse(Object protocolResponse) {
    this.protocolResponse = protocolResponse;
    return this;
  }

  public Throwable getError() {
    return error;
  }

  public RpcInvocationContext setError(Throwable error) {
    this.error = error;
    return this;
  }

  public RpcInvocationResult getResult() {
    return result;
  }

  public RpcInvocationContext setResult(RpcInvocationResult result) {
    this.result = result;
    return this;
  }
}
