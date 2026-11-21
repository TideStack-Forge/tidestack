package com.ouroboros.scheduler;

import java.util.List;

public interface ScheduleTaskProvider {
  List<ScheduleTask> getAllScheduleTask();
}
