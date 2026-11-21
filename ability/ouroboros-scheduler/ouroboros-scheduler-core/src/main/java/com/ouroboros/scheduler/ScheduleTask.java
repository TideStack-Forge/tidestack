package com.ouroboros.scheduler;

import java.util.Map;

public interface ScheduleTask {
  String getName();

  String getCron();

  String getFlowName();

  Map<String, Object> getData();
}
