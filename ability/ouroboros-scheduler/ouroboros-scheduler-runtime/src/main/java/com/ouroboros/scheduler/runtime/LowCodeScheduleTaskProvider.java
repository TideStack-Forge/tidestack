package com.ouroboros.scheduler.runtime;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.scheduler.ScheduleTask;
import com.ouroboros.scheduler.ScheduleTaskProvider;
import com.ouroboros.scheduler.SimpleScheduleTask;
import com.ouroboros.util.Json;

@Component
public class LowCodeScheduleTaskProvider {
  private static final Logger logger = LoggerFactory.getLogger(LowCodeScheduleTaskProvider.class);
  private static final String SCHEDULE_TASK_DIRECTORY = "schedule";
  private static final String SCHEDULE_TASK_SUFFIX = ".schedule.json";

  @Autowired
  AppContext appContext;
  List<ScheduleTask> scheduleTaskList;

  public LowCodeScheduleTaskProvider() {
    Proxy.delegate = this;
  }

  @EventListener(OuroborosMetadataRefreshingEvent.class)
  private void refresh() {
    List<ScheduleTask> result = new ArrayList<>();
    var walkPath = Paths.get(appContext.getHomePath(), SCHEDULE_TASK_DIRECTORY);
    if (!Files.exists(walkPath)) {
      scheduleTaskList = result;
      return;
    }
    try (var steam = Files.walk(walkPath)) {
      // 获取所有文件路径
      var files = steam.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(SCHEDULE_TASK_SUFFIX))
          .collect(Collectors.toList());

      // 解析文件
      files.stream()
          .map(path -> {
            var fileContent = Try.of(() -> String.join("\n", Files.readAllLines(path))).getOrElse("").trim();
            if (fileContent.isEmpty()) {
              return Either.<RuntimeException, SimpleScheduleTask>left(new RuntimeException("计划任务定义文件 " + path + " 为空"));
            }
            return Json.tryToBean(SimpleScheduleTask.class, fileContent)
                .toEither()
                .mapLeft(e -> new RuntimeException("解析计划任务定义文件 " + path + " 失败", e));
          })
          .forEach(either -> either
              .peek(result::add)
              .peekLeft(e -> logger.error(e.getMessage(), e)));
    } catch (Exception e) {
      logger.error("刷新计划任务列表时发生错误", e);
    }
    scheduleTaskList = result;
  }

  public List<ScheduleTask> getAllScheduleTask() {
    if (scheduleTaskList == null) {
      refresh();
    }
    return scheduleTaskList;
  }

  public static class Proxy implements ScheduleTaskProvider {
    private static LowCodeScheduleTaskProvider delegate;

    @Override
    public List<ScheduleTask> getAllScheduleTask() {
      return delegate.getAllScheduleTask();
    }
  }
}
