package com.ouroboros.scheduler.runtime;

import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.scheduler.ScheduleTask;
import com.ouroboros.scheduler.ScheduleTaskIntegrator;
import com.ouroboros.scheduler.ScheduleTaskProvider;
import com.ouroboros.scheduler.Scheduler;
import com.ouroboros.util.SPI;

@AutoConfiguration
@Configuration
@ComponentScan
public class OuroborosSchedulerRuntimeAutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(OuroborosSchedulerRuntimeAutoConfiguration.class);
  private final Scheduler scheduler;
  private final ScheduleTaskIntegrator scheduleTaskIntegrator;

  public OuroborosSchedulerRuntimeAutoConfiguration(ObjectProvider<Scheduler> scheduler, ObjectProvider<ScheduleTaskIntegrator> scheduleTaskIntegrator) {
    this.scheduler = scheduler.getIfAvailable();
    this.scheduleTaskIntegrator = scheduleTaskIntegrator.getIfAvailable(() -> {
      var providers = SPI.getSortedServiceStream(ScheduleTaskProvider.class).collect(Collectors.toList());
      return new ScheduleTaskIntegratorImpl(providers);
    });
  }

  private void init() {
    logger.info("Ouroboros Scheduler Runtime Loaded");
  }

  @EventListener(OuroborosContextRefreshedEvent.class)
  public void applyScheduleTasks(OuroborosContextRefreshedEvent event) {
    if (scheduler == null) {
      logger.error("计划任务调度器未初始化");
      return;
    }
    if (!(event.getSource() instanceof ApplicationStartedEvent)) {
      return;
    }

    // 取得所有计划任务
    var allTasks = scheduleTaskIntegrator.getAllScheduleTask();
    // 重新安排计划任务
    allTasks.forEach(scheduler::schedule);
  }

  private static class ScheduleTaskIntegratorImpl implements ScheduleTaskIntegrator {
    List<ScheduleTaskProvider> scheduleTaskProviders;

    public ScheduleTaskIntegratorImpl(List<ScheduleTaskProvider> scheduleTaskProviders) {
      this.scheduleTaskProviders = scheduleTaskProviders;
    }

    @Override
    public List<ScheduleTask> getAllScheduleTask() {
      var allTasks = scheduleTaskProviders.stream()
          .flatMap(provider -> provider.getAllScheduleTask().stream())
          .filter(task -> StringUtils.isNotBlank(task.getName()))
          .filter(task -> StringUtils.isNotBlank(task.getCron()))
          .collect(() -> new TreeMap<String, ScheduleTask>(String::compareToIgnoreCase),
              (map, task) -> map.put(task.getName(), task),
              TreeMap::putAll);
      return allTasks.values().stream().collect(Collectors.toList());
    }
  }
}
