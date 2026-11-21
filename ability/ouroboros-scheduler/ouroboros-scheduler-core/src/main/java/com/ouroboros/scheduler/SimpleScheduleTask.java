package com.ouroboros.scheduler;

import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleScheduleTask implements ScheduleTask {
  private String name;
  private String description;
  private String cron;
  private String flowName;
  private Map<String, Object> data = new LinkedHashMap<>();

  @Override
  public String getName() {
    return name;
  }

  public SimpleScheduleTask setName(String name) {
    this.name = name;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public SimpleScheduleTask setDescription(String description) {
    this.description = description;
    return this;
  }

  @Override
  public String getCron() {
    return cron;
  }

  public SimpleScheduleTask setCron(String cron) {
    this.cron = cron;
    return this;
  }

  @Override
  public String getFlowName() {
    return flowName;
  }

  public SimpleScheduleTask setFlowName(String flowName) {
    this.flowName = flowName;
    return this;
  }

  @Override
  public Map<String, Object> getData() {
    return data;
  }

  public SimpleScheduleTask setData(Map<String, Object> data) {
    this.data = data;
    return this;
  }
}
