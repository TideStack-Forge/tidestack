package com.ouroboros.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

import io.vavr.control.Try;

public interface Scheduler {
  default Try<LocalDateTime> schedule(ScheduleTask task) {
    var taskName = task.getName();
    if (isScheduled(taskName)) {
      cancel(taskName);
    }
    return scheduleCron(taskName, task.getCron(), task.getFlowName(), task.getData());
  }

  Try<LocalDateTime> scheduleCron(String taskName, String cron, String flowName, Map<String, Object> data);

  Try<LocalDateTime> scheduleOnce(String taskName, Date dateTime, String flowName, Map<String, Object> data);

  Try<LocalDateTime> scheduleOnce(String taskName, LocalDateTime dateTime, String flowName, Map<String, Object> data);

  Try<LocalDateTime> scheduleOnce(String taskName, Duration delay, String flowName, Map<String, Object> data);

  Try<LocalDateTime> scheduleOnce(String taskName, Long millisecond, String flowName, Map<String, Object> data);

  Try<Void> cancel(String taskName);

  boolean isScheduled(String taskName);
}
