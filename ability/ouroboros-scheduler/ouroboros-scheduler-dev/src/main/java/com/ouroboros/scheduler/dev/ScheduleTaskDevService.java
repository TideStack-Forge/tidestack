package com.ouroboros.scheduler.dev;

import java.util.function.Function;

import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.scheduler.SimpleScheduleTask;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

@DevelopModule("schedule")
@Component
public class ScheduleTaskDevService extends BaseDevelopService<SimpleScheduleTask> {
  private static final String MODEL_DIRECTORY = "schedule";
  private static final String MODEL_FILE_EXTENSION = ".schedule.json";

  @Override
  protected String getModelFileDirectory() {
    return MODEL_DIRECTORY;
  }

  @Override
  protected String getModelFileExtension() {
    return MODEL_FILE_EXTENSION;
  }

  @Override
  protected String getModelDisplayName() {
    return "计划任务";
  }

  @Override
  protected boolean isAutoCommitWhenUpdate() {
    return false;
  }

  @Override
  protected String getFullName(SimpleScheduleTask model) {
    return model.getName();
  }

  @Override
  protected Function<VCSFile, Try<?>> buildSearchResultMapper() {
    return file -> Json.tryToBean(SimpleScheduleTask.class, file.getContent()).map(t -> new ModelFileMetaWrapper<>(t, file));
  }
}
