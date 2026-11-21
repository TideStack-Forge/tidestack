package com.ouroboros.businessmodel.event.store;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/** Durable immutable facts, separate from per-subscription delivery state. */
@Model(fullName = "ouroboros.BusinessEventOccurrence", rawName = "t_business_event_occurrence", label = "业务 Event 事实")
public class BusinessEventOccurrenceModel {
  @Field
  @PrimaryKey
  private String id;

  @Field
  private String modelFullName;

  @Field
  private String eventName;

  @Field
  private String modelRevision;

  @Field(rawType = "longtext")
  private String occurrenceJson;

  @Field(rawType = "longtext")
  private String schemaJson;

  @Field
  private LocalDateTime occurredAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getModelFullName() {
    return modelFullName;
  }

  public void setModelFullName(String modelFullName) {
    this.modelFullName = modelFullName;
  }

  public String getEventName() {
    return eventName;
  }

  public void setEventName(String eventName) {
    this.eventName = eventName;
  }

  public String getModelRevision() {
    return modelRevision;
  }

  public void setModelRevision(String modelRevision) {
    this.modelRevision = modelRevision;
  }

  public String getOccurrenceJson() {
    return occurrenceJson;
  }

  public void setOccurrenceJson(String occurrenceJson) {
    this.occurrenceJson = occurrenceJson;
  }

  public String getSchemaJson() {
    return schemaJson;
  }

  public void setSchemaJson(String schemaJson) {
    this.schemaJson = schemaJson;
  }

  public LocalDateTime getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(LocalDateTime occurredAt) {
    this.occurredAt = occurredAt;
  }

}
