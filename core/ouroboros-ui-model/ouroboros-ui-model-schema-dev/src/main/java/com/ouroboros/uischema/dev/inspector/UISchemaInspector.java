package com.ouroboros.uischema.dev.inspector;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.uischema.wrapper.SchemaNodeWrapper;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class UISchemaInspector {
  private static final String MODEL_FILE_DIRECTORY = "ui-schema";
  private static final String MODEL_FILE_EXTENSION = ".ui-schema.json";
  private static final String LEGACY_MODEL_FILE_EXTENSION = ".json";
  private final BaseDevelopService<SchemaNodeWrapper> uiSchemaDevService;

  public UISchemaInspector(BaseDevelopService<SchemaNodeWrapper> uiSchemaDevService) {
    this.uiSchemaDevService = uiSchemaDevService;
  }

  private static BaseDevelopService<SchemaNodeWrapper> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(UISchemaInspector.class).uiSchemaDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      var model = readModel(file).orElse(null);
      var modelName = model == null ? getModelName(file) : model.getName();
      description.setName(modelName)
          .setIcon("ui-schema")
          .setType("ui-schema")
          .setTitle(modelName);
      if (model == null) {
        return;
      }
      description.setTitle(StringUtils.isEmpty(model.getTitle()) ? "" : model.getTitle());
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (dir.getPath().equals(MODEL_FILE_DIRECTORY)) {
          dir.setTitle("低码界面");
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

    private Optional<SchemaNodeWrapper> readModel(VCSFile file) {
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
