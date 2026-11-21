package com.ouroboros.scheduler.quartz;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.scheduler.ScheduleTask;
import com.ouroboros.scheduler.Scheduler;
import com.ouroboros.util.Dates;
import com.ouroboros.util.Json;

@Component("ouroborosQuartzScheduler")
public class QuartzScheduler implements Scheduler {
  private final static Logger logger = LoggerFactory.getLogger(QuartzScheduler.class);
  @Autowired
  private org.quartz.Scheduler quartzScheduler;

  // 取不到配置值则使用默认值
  @Value("${ouroboros.scheduler.quartz.default-group:Ouroboros}")
  private String defaultGroup = "Ouroboros";

  @Override
  public Try<LocalDateTime> schedule(ScheduleTask task) {
    var taskName = task.getName();
    if (!isScheduled(taskName)) {
      return scheduleCron(taskName, task.getCron(), task.getFlowName(), task.getData());
    }

    if (isTaskChanged(task)) {
      cancel(taskName);
      return scheduleCron(taskName, task.getCron(), task.getFlowName(), task.getData());
    }

    var triggerKey = TriggerKey.triggerKey(taskName, defaultGroup);
    var nextFireDate = Try.of(() -> quartzScheduler.getTrigger(triggerKey).getNextFireTime());
    return nextFireDate.map(Dates::toLocalDateTime);
  }

  @Override
  public Try<LocalDateTime> scheduleCron(String taskName, String cron, String flowName, Map<String, Object> data) {
    var job = Try.of(() -> JobBuilder.newJob(LogicFlowJob.class)
        .withIdentity(taskName, defaultGroup)
        .usingJobData("flowName", flowName)
        .usingJobData(buildJobDataMap(data))
        .build());
    if (job.isFailure()) {
      return Try.failure(job.getCause());
    }

    var trigger = Try.of(() -> TriggerBuilder.newTrigger()
        .withIdentity(taskName, defaultGroup)
        .withSchedule(CronScheduleBuilder.cronSchedule(cron))
        .build());
    if (trigger.isFailure()) {
      return Try.failure(trigger.getCause());
    }

    var trySchedule = Try.of(() -> quartzScheduler.scheduleJob(job.get(), trigger.get()));
    if (trySchedule.isFailure()) {
      return Try.failure(trySchedule.getCause());
    }
    return trySchedule.map(Dates::toLocalDateTime);
  }

  @Override
  public Try<LocalDateTime> scheduleOnce(String taskName, LocalDateTime dateTime, String flowName, Map<String, Object> data) {
    return scheduleOnce(taskName, Dates.toDate(dateTime), flowName, data);
  }

  @Override
  public Try<LocalDateTime> scheduleOnce(String taskName, Date dateTime, String flowName, Map<String, Object> data) {
    var job = Try.of(() -> JobBuilder.newJob(LogicFlowJob.class)
        .withIdentity(taskName, defaultGroup)
        .usingJobData("flowName", flowName)
        .usingJobData(buildJobDataMap(data))
        .build());
    if (job.isFailure()) {
      return Try.failure(job.getCause());
    }

    var trigger = Try.of(() -> TriggerBuilder.newTrigger()
        .withIdentity(taskName, defaultGroup)
        .startAt(dateTime)
        .build());
    if (trigger.isFailure()) {
      return Try.failure(trigger.getCause());
    }

    var trySchedule = Try.of(() -> quartzScheduler.scheduleJob(job.get(), trigger.get()));
    return trySchedule.map(Dates::toLocalDateTime);
  }

  @Override
  public Try<LocalDateTime> scheduleOnce(String taskName, Duration delay, String flowName, Map<String, Object> data) {
    var startTime = LocalDateTime.now().plus(delay);
    return scheduleOnce(taskName, startTime, flowName, data);
  }

  @Override
  public Try<LocalDateTime> scheduleOnce(String taskName, Long millisecond, String flowName, Map<String, Object> data) {
    var startTime = LocalDateTime.now().plus(Duration.ofMillis(millisecond));
    return scheduleOnce(taskName, startTime, flowName, data);
  }

  @Override
  public Try<Void> cancel(String taskName) {
    var jobIdentity = JobKey.jobKey(taskName, defaultGroup);
    var delete = Try.of(() -> quartzScheduler.deleteJob(jobIdentity));
    return delete.map(v -> null);
  }

  @Override
  public boolean isScheduled(String taskName) {
    var jobIdentity = JobKey.jobKey(taskName, defaultGroup);
    var check = Try.of(() -> quartzScheduler.checkExists(jobIdentity));
    return check.getOrElse(false);
  }

  private boolean isTaskChanged(ScheduleTask task) {
    return isTriggerChanged(task) || isJobChanged(task);
  }

  private boolean isTriggerChanged(ScheduleTask task) {
    var triggerKey = TriggerKey.triggerKey(task.getName(), defaultGroup);
    var trigger = Try.of(() -> quartzScheduler.getTrigger(triggerKey));
    if (trigger.isFailure()) {
      return true;
    }
    var scheduleBuilder = trigger.get().getScheduleBuilder();
    if (!(scheduleBuilder instanceof CronScheduleBuilder cron)) {
      return true;
    }
    var cronExpression = ((CronTrigger) cron.build()).getCronExpression();
    return !StringUtils.equals(cronExpression, task.getCron());
  }

  private boolean isJobChanged(ScheduleTask task) {
    var jobKey = JobKey.jobKey(task.getName(), defaultGroup);
    var jobDetail = Try.of(() -> quartzScheduler.getJobDetail(jobKey));
    if (jobDetail.isFailure()) {
      return true;
    }
    var jobDataMap = jobDetail.get().getJobDataMap();
    if (ObjectUtils.notEqual(jobDataMap.get("flowName"), task.getFlowName())) {
      return true;
    }
    return isDataChanged(jobDataMap, task.getData());
  }

  private boolean isDataChanged(JobDataMap jobDataMap, Map<String, Object> data) {
    // 决定跟哪个Map比较
    var jsonData = Optional.ofNullable(jobDataMap.get(LogicFlowJob.JSON_DATA_KEY))
        .filter(String.class::isInstance)
        .map(String.class::cast);
    Map<String, Object> compareWith = jsonData.isPresent()
        ? Json.tryToMap(jsonData.get()).getOrElse(Collections::emptyMap)
        : Collections.emptyMap();

    var compareKeys = compareWith.keySet()
        .stream().filter(key -> !LogicFlowJob.JSON_DATA_KEY.equals(key) && !"flowName".equals(key))
        .collect(Collectors.toSet());

    // 比较key
    if (!compareKeys.equals(data.keySet())) {
      return true;
    }

    // 比较value
    return data.entrySet()
        .stream()
        .anyMatch(entry -> ObjectUtils.notEqual(entry.getValue(), compareWith.get(entry.getKey())));
  }

  private JobDataMap buildJobDataMap(Map<String, Object> data) {
    if (data == null) {
      return new JobDataMap();
    }
    var isAllValueBasicType = data.values().stream().allMatch(value -> value instanceof String || value instanceof Boolean
        || value instanceof Integer || value instanceof Long
        || value instanceof Float || value instanceof Double);
    if (isAllValueBasicType) {
      return new JobDataMap(data);
    }
    // 否则以JSON字符串形式保存
    var jobData = new JobDataMap();
    var json = Json.toJsonString(data);
    jobData.put(LogicFlowJob.JSON_DATA_KEY, json);
    return jobData;
  }
}
