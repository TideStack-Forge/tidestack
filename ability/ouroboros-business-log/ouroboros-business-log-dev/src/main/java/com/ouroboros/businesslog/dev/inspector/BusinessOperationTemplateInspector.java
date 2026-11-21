package com.ouroboros.businesslog.dev.inspector;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.businesslog.DefaultBusinessOperationTemplate;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class BusinessOperationTemplateInspector {

  private static final String MODEL_FILE_DIRECTORY = "business-log-template";
  private static final String MODEL_FILE_EXTENSION = ".business-log.json";
  private final BaseDevelopService<DefaultBusinessOperationTemplate> businessOperationTemplateDevService;

  public BusinessOperationTemplateInspector(BaseDevelopService<DefaultBusinessOperationTemplate> businessOperationTemplateDevService) {
    this.businessOperationTemplateDevService = businessOperationTemplateDevService;
  }

  private static BaseDevelopService<DefaultBusinessOperationTemplate> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(BusinessOperationTemplateInspector.class).businessOperationTemplateDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {

    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      String modelName = file.getFileName().substring(0, file.getFileName().length() - MODEL_FILE_EXTENSION.length());
      description.setName(modelName)
          .setIcon("fas fa-clipboard-list")
          .setType("business-log-template")
          .setTitle(modelName);

      DefaultBusinessOperationTemplate model = Optional.ofNullable(currentService())
          .flatMap(service -> service.getOne(modelName))
          .orElse(null);
      if (model == null) {
        return;
      }

      String title = StringUtils.defaultIfBlank(model.getOperation(), modelName);
      String descriptionText = String.format("%s %s",
          StringUtils.defaultString(model.getHttpMethod()),
          StringUtils.defaultString(model.getUrl())
      ).trim();

      description.setTitle(title)
          .setDescription(StringUtils.defaultIfBlank(descriptionText, null));
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (MODEL_FILE_DIRECTORY.equals(dir.getPath())) {
          dir.setTitle("业务日志模板");
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
