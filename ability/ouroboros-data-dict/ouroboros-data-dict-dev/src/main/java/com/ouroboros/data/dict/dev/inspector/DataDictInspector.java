package com.ouroboros.data.dict.dev.inspector;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ouroboros.data.dict.DataDict;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class DataDictInspector {
  private final BaseDevelopService<DataDict> dataDictService;
  private static String MODEL_FILE_DIRECTORY = "data-dict";
  private static final String MODEL_FILE_EXTENSION = ".dict.json";
  private static final String LEGACY_MODEL_FILE_EXTENSION = ".json";

  public DataDictInspector(BaseDevelopService<DataDict> dataDictService) {
    this.dataDictService = dataDictService;
  }

  private static BaseDevelopService<DataDict> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(DataDictInspector.class).dataDictService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      var model = readModel(file).orElse(null);
      var modelName = model == null ? getModelName(file) : model.getName();
      description.setIcon("fas fa-th-list")
          .setType("data-dict")
          .setName(modelName)
          .setTitle(modelName);
      var metadata = Optional.ofNullable(model)
          .or(() -> Optional.ofNullable(currentService()).flatMap(service -> service.getOne(modelName)))
          .orElse(null);
      if (metadata == null) {
        return;
      }
      description.setTitle(metadata.getTitle());
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      for (DirDescription dir : dirs) {
        if (MODEL_FILE_DIRECTORY.equals(dir.getPath())) {
          dir.setTitle("数据字典");
        }
      }
    }

    @Override
    public boolean support(VCSFile file) {
      return file.getPath().startsWith(MODEL_FILE_DIRECTORY)
          && (file.getPath().endsWith(MODEL_FILE_EXTENSION)
          || file.getPath().endsWith(LEGACY_MODEL_FILE_EXTENSION));
    }

    private Optional<DataDict> readModel(VCSFile file) {
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
