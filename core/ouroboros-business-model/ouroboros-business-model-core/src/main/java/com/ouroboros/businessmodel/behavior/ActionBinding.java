package com.ouroboros.businessmodel.behavior;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.ouroboros.businessmodel.meta.MetaWithExtraProps;

/**
 * The implementation binding of a business action.
 */
public class ActionBinding extends MetaWithExtraProps {
  private String kind;
  private String flowName;
  private String handlerName;
  @JsonProperty("inputMapping")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Map<String, Object> inputMapping;
  private Object outputMapping;
  @JsonProperty("requiresHttpRequest")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean requiresHttpRequest;

  @JsonIgnore
  public boolean isRequiresHttpRequest() {
    return Boolean.TRUE.equals(requiresHttpRequest);
  }

  @JsonProperty("requiresHttpRequest")
  public void setRequiresHttpRequest(boolean requiresHttpRequest) {
    this.requiresHttpRequest = requiresHttpRequest;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getFlowName() {
    return flowName;
  }

  public void setFlowName(String flowName) {
    this.flowName = flowName;
  }

  public String getHandlerName() {
    return handlerName;
  }

  public void setHandlerName(String handlerName) {
    this.handlerName = handlerName;
  }

  @JsonIgnore
  public Map<String, Object> getInputMapping() {
    return inputMapping == null ? Map.of() : Collections.unmodifiableMap(inputMapping);
  }

  @JsonProperty("inputMapping")
  public void setInputMapping(Map<String, Object> inputMapping) {
    this.inputMapping = inputMapping == null ? null : new LinkedHashMap<>(inputMapping);
  }

  public Object getOutputMapping() {
    return outputMapping;
  }

  public void setOutputMapping(Object outputMapping) {
    this.outputMapping = outputMapping;
  }
}
