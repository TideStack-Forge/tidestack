package com.ouroboros.notification.inspector;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.notification.template.DefaultNotificationTemplate;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class NotificationTemplateInspector {
  private static final String MODEL_FILE_DIRECTORY = "notification-template";
  private static final String MODEL_FILE_EXTENSION = ".notification.json";
  private final BaseDevelopService<DefaultNotificationTemplate> notificationTemplateDevService;

  public NotificationTemplateInspector(BaseDevelopService<DefaultNotificationTemplate> notificationTemplateDevService) {
    this.notificationTemplateDevService = notificationTemplateDevService;
  }

  private static BaseDevelopService<DefaultNotificationTemplate> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(NotificationTemplateInspector.class).notificationTemplateDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      var modelName = file.getFileName().substring(0, file.getFileName().length() - MODEL_FILE_EXTENSION.length());
      description.setName(modelName)
          .setIcon("fas fa-bell")
          .setType("notification-template")
          .setTitle(modelName);
      var model = Optional.ofNullable(currentService())
          .flatMap(service -> service.getOne(modelName))
          .orElse(null);
      if (model == null) {
        return;
      }
      description.setTitle(StringUtils.isEmpty(model.getTitle()) ? modelName : model.getTitle());
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (dir.getPath().equals(MODEL_FILE_DIRECTORY)) {
          dir.setTitle("通知模板");
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
