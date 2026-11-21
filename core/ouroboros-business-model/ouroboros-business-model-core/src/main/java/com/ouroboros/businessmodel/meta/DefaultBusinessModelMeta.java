package com.ouroboros.businessmodel.meta;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.EventDefinition;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;

public class DefaultBusinessModelMeta extends BusinessModelMeta {
  @Override
  public List<BusinessModelFieldMeta> fieldDefinitions() { return getFields(); }

  @Override
  public List<BusinessModelRelationMeta> relationDefinitions() { return getRelations(); }

  List<BusinessModelFieldMeta> fields = Collections.emptyList();
  List<BusinessModelRelationMeta> relations = Collections.emptyList();
  @JsonProperty("actions")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<ActionDefinition> actions;
  @JsonProperty("events")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<EventDefinition> events;
  @JsonProperty("eventSubscriptions")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<EventSubscriptionDefinition> eventSubscriptions;
  /**
   * Whether archive-delete plugin behavior is enabled.
   */
  private Boolean enableArchiveDelete;
  /**
   * Whether SCD2 history plugin behavior is enabled.
   */
  private Boolean enableScd2History;

  public List<BusinessModelFieldMeta> getFields() {
    return fields;
  }

  public void setFields(List<BusinessModelFieldMeta> fields) {
    this.fields = fields;
  }

  public List<BusinessModelRelationMeta> getRelations() {
    return relations;
  }

  public void setRelations(List<BusinessModelRelationMeta> relations) {
    this.relations = relations;
  }

  @JsonIgnore
  public List<ActionDefinition> getActions() {
    return actions == null ? Collections.emptyList() : actions;
  }

  @JsonProperty("actions")
  public void setActions(List<ActionDefinition> actions) {
    this.actions = actions;
  }

  @JsonIgnore
  public List<EventDefinition> getEvents() {
    return events == null ? Collections.emptyList() : events;
  }

  @JsonProperty("events")
  public void setEvents(List<EventDefinition> events) {
    this.events = events;
  }

  @JsonIgnore
  public List<EventSubscriptionDefinition> getEventSubscriptions() {
    return eventSubscriptions == null ? Collections.emptyList() : eventSubscriptions;
  }

  @JsonProperty("eventSubscriptions")
  public void setEventSubscriptions(List<EventSubscriptionDefinition> eventSubscriptions) {
    this.eventSubscriptions = eventSubscriptions;
  }

  /**
   * Returns whether archive-delete plugin behavior is enabled.
   *
   * @return nullable enable flag
   */
  public Boolean getEnableArchiveDelete() {
    return enableArchiveDelete;
  }

  /**
   * Sets whether archive-delete plugin behavior is enabled.
   *
   * @param enableArchiveDelete nullable enable flag
   */
  public void setEnableArchiveDelete(Boolean enableArchiveDelete) {
    this.enableArchiveDelete = enableArchiveDelete;
  }

  /**
   * Returns whether SCD2 history plugin behavior is enabled.
   *
   * @return nullable enable flag
   */
  public Boolean getEnableScd2History() {
    return enableScd2History;
  }

  /**
   * Sets whether SCD2 history plugin behavior is enabled.
   *
   * @param enableScd2History nullable enable flag
   */
  public void setEnableScd2History(Boolean enableScd2History) {
    this.enableScd2History = enableScd2History;
  }

}
