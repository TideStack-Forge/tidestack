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
 * Dynamic business event contract contributed by a business model.
 */
public class EventDefinition extends MetaWithExtraProps {
  private String name;
  private String title;
  private String description;
  private String occurrenceCondition;
  private String subject;
  @JsonProperty("requireUnfilteredModelRead")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean requireUnfilteredModelRead;
  @JsonProperty("payloadSchema")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Map<String, Object> payloadSchema;
  @JsonProperty("allowedProducerKinds")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> allowedProducerKinds;


  @JsonIgnore
  public boolean isRequireUnfilteredModelRead() {
    return Boolean.TRUE.equals(requireUnfilteredModelRead);
  }

  @JsonProperty("requireUnfilteredModelRead")
  public void setRequireUnfilteredModelRead(boolean requireUnfilteredModelRead) {
    this.requireUnfilteredModelRead = requireUnfilteredModelRead;
  }

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

  public String getOccurrenceCondition() {
    return occurrenceCondition;
  }

  public void setOccurrenceCondition(String occurrenceCondition) {
    this.occurrenceCondition = occurrenceCondition;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  @JsonIgnore
  public Map<String, Object> getPayloadSchema() {
    return payloadSchema == null ? Map.of() : Collections.unmodifiableMap(payloadSchema);
  }

  @JsonProperty("payloadSchema")
  public void setPayloadSchema(Map<String, Object> payloadSchema) {
    this.payloadSchema = payloadSchema == null ? null : new LinkedHashMap<>(payloadSchema);
  }

  @JsonIgnore
  public List<String> getAllowedProducerKinds() {
    return allowedProducerKinds == null ? List.of() : Collections.unmodifiableList(allowedProducerKinds);
  }

  @JsonProperty("allowedProducerKinds")
  public void setAllowedProducerKinds(List<String> allowedProducerKinds) {
    this.allowedProducerKinds = allowedProducerKinds == null
        ? null
        : new ArrayList<>(allowedProducerKinds);
  }
}
