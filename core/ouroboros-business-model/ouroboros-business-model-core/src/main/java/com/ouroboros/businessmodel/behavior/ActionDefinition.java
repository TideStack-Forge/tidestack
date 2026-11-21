package com.ouroboros.businessmodel.behavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.ouroboros.businessmodel.meta.MetaWithExtraProps;

/**
 * Dynamic business action contract contributed by a business model.
 */
public class ActionDefinition extends MetaWithExtraProps {
  private String name;
  private String title;
  private String description;
  @JsonProperty("isEnabled")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean isEnabled;
  @JsonProperty("inputSchema")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Map<String, Object> inputSchema;
  @JsonProperty("outputSchema")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Map<String, Object> outputSchema;
  private ActionBinding binding;
  private Boolean readOnly;
  private Object authorization;
  private Object precondition;
  @JsonProperty("emits")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<EventReference> emits;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @JsonIgnore
  public Boolean getIsEnabled() {
    return isEnabled == null ? false : isEnabled;
  }

  @JsonProperty("isEnabled")
  public void setIsEnabled(Boolean enabled) {
    isEnabled = enabled;
  }

  @JsonIgnore
  public Map<String, Object> getInputSchema() {
    return inputSchema == null ? Map.of() : Collections.unmodifiableMap(inputSchema);
  }

  @JsonProperty("inputSchema")
  public void setInputSchema(Map<String, Object> inputSchema) {
    this.inputSchema = inputSchema == null ? null : new LinkedHashMap<>(inputSchema);
  }

  @JsonIgnore
  public Map<String, Object> getOutputSchema() {
    return outputSchema == null ? Map.of() : Collections.unmodifiableMap(outputSchema);
  }

  @JsonProperty("outputSchema")
  public void setOutputSchema(Map<String, Object> outputSchema) {
    this.outputSchema = outputSchema == null ? null : new LinkedHashMap<>(outputSchema);
  }

  public ActionBinding getBinding() {
    return binding;
  }

  public void setBinding(ActionBinding binding) {
    this.binding = binding;
  }

  public Boolean getReadOnly() {
    return readOnly;
  }

  public void setReadOnly(Boolean readOnly) {
    this.readOnly = readOnly;
  }

  public Object getAuthorization() {
    return authorization;
  }

  public void setAuthorization(Object authorization) {
    this.authorization = authorization;
  }

  public Object getPrecondition() {
    return precondition;
  }

  public void setPrecondition(Object precondition) {
    this.precondition = precondition;
  }

  @JsonIgnore
  public List<EventReference> getEmits() {
    return emits == null ? List.of() : Collections.unmodifiableList(emits);
  }

  @JsonProperty("emits")
  public void setEmits(List<EventReference> emits) {
    this.emits = emits == null ? null : new ArrayList<>(emits);
  }
}
