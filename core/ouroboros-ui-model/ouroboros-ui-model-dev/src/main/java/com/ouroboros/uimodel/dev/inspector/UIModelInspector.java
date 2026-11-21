package com.ouroboros.uimodel.dev.inspector;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.uimodel.dev.model.LowcodeUIModel;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class UIModelInspector {
  private static final String MODEL_FILE_DIRECTORY = "ui-model";
  private static final String MODEL_FILE_EXTENSION = ".ui-model.json";
  private static final String LEGACY_MODEL_FILE_EXTENSION = ".json";
  private final BaseDevelopService<LowcodeUIModel> uiModelDevService;
  public UIModelInspector(BaseDevelopService<LowcodeUIModel> uiModelDevService) {
    this.uiModelDevService = uiModelDevService;
  }

  private static BaseDevelopService<LowcodeUIModel> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(UIModelInspector.class).uiModelDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      var model = readModel(file).orElse(null);
      var modelName = model == null ? getModelName(file) : model.getFullName();
      description.setIcon("ui-model");
      description.setType("ui-model");
      description.setName(modelName);
      if (model == null) {
        return;
      }
      description.setTitle(model.getTitle());
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (dir.getPath().equals(MODEL_FILE_DIRECTORY)) {
          dir.setTitle("UI模型");
          dir.setDescription("用于定义UI路由和渲染方式");
        }
      });
    }

    @Override
    public boolean support(VCSFile file) {
      return file.getPath().startsWith(MODEL_FILE_DIRECTORY)
          && (file.getPath().endsWith(MODEL_FILE_EXTENSION)
          || file.getPath().endsWith(LEGACY_MODEL_FILE_EXTENSION));
    }

    private String getModelName(VCSFile file) {
      var relativePath = stripModelDirectory(file.getPath());
      if (relativePath.endsWith(MODEL_FILE_EXTENSION)) {
        return normalizePath(relativePath.substring(0, relativePath.length() - MODEL_FILE_EXTENSION.length()));
      }
      return normalizePath(relativePath.substring(0, relativePath.length() - LEGACY_MODEL_FILE_EXTENSION.length()));
    }

    private Optional<LowcodeUIModel> readModel(VCSFile file) {
      return Optional.ofNullable(currentService())
          .flatMap(service -> service.buildModelFromFileContent(file.getContent()).toJavaOptional());
    }

    private String stripModelDirectory(String path) {
      if (path.startsWith(MODEL_FILE_DIRECTORY + "/")) {
        return path.substring(MODEL_FILE_DIRECTORY.length() + 1);
      }
      return path;
    }

    private String normalizePath(String path) {
      return path.replace('/', '.').replace('\\', '.');
    }
  }
}
