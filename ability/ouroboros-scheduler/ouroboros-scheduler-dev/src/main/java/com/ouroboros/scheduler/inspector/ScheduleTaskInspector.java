package com.ouroboros.scheduler.inspector;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.scheduler.SimpleScheduleTask;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class ScheduleTaskInspector {
  private static final String MODEL_FILE_DIRECTORY = "schedule";
  private static final String MODEL_FILE_EXTENSION = ".schedule.json";
  private final BaseDevelopService<SimpleScheduleTask> scheduleTaskDevService;

  public ScheduleTaskInspector(BaseDevelopService<SimpleScheduleTask> scheduleTaskDevService) {
    this.scheduleTaskDevService = scheduleTaskDevService;
  }

  private static BaseDevelopService<SimpleScheduleTask> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(ScheduleTaskInspector.class).scheduleTaskDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      var modelName = file.getFileName().substring(0, file.getFileName().length() - MODEL_FILE_EXTENSION.length());
      description.setName(modelName)
          .setIcon("schedule")
          .setType("schedule")
          .setTitle(modelName);
      var model = Optional.ofNullable(currentService())
          .flatMap(service -> service.getOne(modelName))
          .orElse(null);
      if (model == null) {
        return;
      }
      description.setTitle(StringUtils.isEmpty(model.getName()) ? modelName : model.getName())
          .setDescription(model.getDescription());
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (dir.getPath().equals(MODEL_FILE_DIRECTORY)) {
          dir.setTitle("计划任务");
        }
      });
    }

    @Override
    public boolean support(VCSFile file) {
      return file.getPath().startsWith(MODEL_FILE_DIRECTORY)
          && file.getPath().endsWith(MODEL_FILE_EXTENSION);
    }
  }
}
