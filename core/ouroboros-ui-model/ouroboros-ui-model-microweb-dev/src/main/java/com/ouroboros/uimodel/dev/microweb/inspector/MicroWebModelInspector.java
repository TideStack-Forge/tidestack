package com.ouroboros.uimodel.dev.microweb.inspector;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.uimodel.dev.microweb.model.LowCodeMicroWebModel;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class MicroWebModelInspector {
  private static final String MODEL_FILE_DIRECTORY = "micro-web";
  private static final String MODEL_FILE_EXTENSION = ".micro-web.json";
  private static final String LEGACY_MODEL_FILE_EXTENSION = ".json";
  private static final String PACKAGE_PATH = "package";
  private static final String PACKAGE_FILE_EXTENSION = ".zip";
  private final BaseDevelopService<LowCodeMicroWebModel> microWebDevService;
  public MicroWebModelInspector(BaseDevelopService<LowCodeMicroWebModel> microWebDevService) {
    this.microWebDevService = microWebDevService;
  }

  private static BaseDevelopService<LowCodeMicroWebModel> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(MicroWebModelInspector.class).microWebDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      if (file.getFileName().endsWith(PACKAGE_FILE_EXTENSION)) {
        description.setTitle(file.getFileName())
            .setDescription("微前端资源包")
            .setIcon("fa fa-file-archive-o")
            .setType("micro-web-package")
            .setName(file.getFileName());
      }
      if (!isModelFile(file)) {
        return;
      }

      var modelName = stripModelSuffix(file.getFileName());

      description.setTitle(modelName)
          .setIcon("micro-web")
          .setType("micro-web")
          .setName(modelName);
      var model = Optional.ofNullable(currentService())
          .flatMap(service -> service.getOne(modelName))
          .orElse(null);
      if (model == null) {
        return;
      }
      description.setTitle(StringUtils.isEmpty(model.getDescription()) ? model.getName() : model.getDescription());
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (dir.getPath().equals(MODEL_FILE_DIRECTORY)) {
          dir.setTitle("微前端");
        }
        if (dir.getPath().startsWith(MODEL_FILE_DIRECTORY) && dir.getPath().contains(PACKAGE_PATH)) {
          dir.setTitle("微前端资源包");
        }
      });
    }

    @Override
    public boolean support(VCSFile file) {
      return file.getPath().startsWith(MODEL_FILE_DIRECTORY);
    }

    private boolean isModelFile(VCSFile file) {
      return isDirectMicroWebFile(file)
          && (file.getFileName().endsWith(MODEL_FILE_EXTENSION)
          || file.getFileName().endsWith(LEGACY_MODEL_FILE_EXTENSION));
    }

    private String stripModelSuffix(String fileName) {
      if (fileName.endsWith(MODEL_FILE_EXTENSION)) {
        return fileName.substring(0, fileName.length() - MODEL_FILE_EXTENSION.length());
      }
      return fileName.substring(0, fileName.length() - LEGACY_MODEL_FILE_EXTENSION.length());
    }

    private boolean isDirectMicroWebFile(VCSFile file) {
      var path = file.getPath().replace('\\', '/');
      if (!path.startsWith(MODEL_FILE_DIRECTORY + "/")) {
        return false;
      }
      return !path.substring(MODEL_FILE_DIRECTORY.length() + 1).contains("/");
    }
  }
}
