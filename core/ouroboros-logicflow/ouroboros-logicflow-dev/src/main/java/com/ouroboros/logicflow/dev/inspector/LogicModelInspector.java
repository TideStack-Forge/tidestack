package com.ouroboros.logicflow.dev.inspector;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class LogicModelInspector {
  private static final String MODEL_FILE_DIRECTORY = "logic-flow";
  private static final String MODEL_FILE_EXTENSION = ".logicflow.json";
  private static final String LEGACY_MODEL_FILE_EXTENSION = ".json";
  private final BaseDevelopService<FlowModelMeta> logicModelDevService;

  public LogicModelInspector(BaseDevelopService<FlowModelMeta> logicModelDevService) {
    this.logicModelDevService = logicModelDevService;
  }

  private static BaseDevelopService<FlowModelMeta> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(LogicModelInspector.class).logicModelDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      var metadata = readModel(file).orElse(null);
      var modelName = metadata == null ? getModelName(file) : metadata.getName();
      description.setIcon("logicflow")
          .setName(modelName)
          .setTitle(modelName)
          .setType("logicflow");

      if (metadata == null) {
        return;
      }
      // TODO: 将来要将 title 和 description 分开
      description.setTitle(StringUtils.isEmpty(metadata.getDescription()) ? "" : metadata.getDescription())
          .setDescription(metadata.getDescription())
          .setName(metadata.getName());
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (MODEL_FILE_DIRECTORY.equals(dir.getPath())) {
          dir.setTitle("逻辑流");
        }
      });
    }

    @Override
    public boolean support(VCSFile file) {
      return file.getPath().startsWith(MODEL_FILE_DIRECTORY)
          && (file.getPath().endsWith(MODEL_FILE_EXTENSION)
          || file.getPath().endsWith(LEGACY_MODEL_FILE_EXTENSION));
    }

    private Optional<FlowModelMeta> readModel(VCSFile file) {
      return Optional.ofNullable(currentService())
          .flatMap(service -> service.buildModelFromFileContent(file.getContent()).toJavaOptional());
    }

    private String getModelName(VCSFile file) {
      var relativePath = stripModelDirectory(file.getPath());
      if (relativePath.endsWith(MODEL_FILE_EXTENSION)) {
        return normalizePath(relativePath.substring(0, relativePath.length() - MODEL_FILE_EXTENSION.length()));
      }
      return normalizePath(relativePath.substring(0, relativePath.length() - LEGACY_MODEL_FILE_EXTENSION.length()));
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
