package com.ouroboros.businessmodel.dev.inspector;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class BusinessModelInspector {
  private static final String MODEL_FILE_DIRECTORY = "business-model";
  private static final String MODEL_FILE_EXTENSION = ".business.json";
  private final BaseDevelopService<BusinessModelMeta> businessModelDevService;

  public BusinessModelInspector(BaseDevelopService<BusinessModelMeta> businessModelDevService) {
    this.businessModelDevService = businessModelDevService;
  }

  private static BaseDevelopService<BusinessModelMeta> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(BusinessModelInspector.class).businessModelDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      var metadata = readModel(file).orElse(null);
      description.setName(metadata == null ? getModelName(file) : metadata.getFullName());
      description.setIcon("business-model");
      description.setType("business-model");
      if (metadata == null) {
        return;
      }
      description.setTitle(metadata.getTitle());
      description.setDescription(metadata.getDescription());
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if ("business-model".equals(dir.getPath())) {
          dir.setTitle("业务模型");
        }
      });
    }

    @Override
    public boolean support(VCSFile file) {
      return file.getPath().startsWith(MODEL_FILE_DIRECTORY)
          && file.getPath().endsWith(MODEL_FILE_EXTENSION);
    }

    private Optional<BusinessModelMeta> readModel(VCSFile file) {
      return Optional.ofNullable(currentService())
          .flatMap(service -> service.buildModelFromFileContent(file.getContent()).toJavaOptional());
    }

    private String getModelName(VCSFile file) {
      var relativePath = stripModelDirectory(file.getPath());
      return normalizePath(relativePath.substring(0, relativePath.length() - MODEL_FILE_EXTENSION.length()));
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
