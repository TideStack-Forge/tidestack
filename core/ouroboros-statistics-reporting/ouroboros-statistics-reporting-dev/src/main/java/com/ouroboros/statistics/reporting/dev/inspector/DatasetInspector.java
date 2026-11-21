package com.ouroboros.statistics.reporting.dev.inspector;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class DatasetInspector {
  private static final String MODEL_FILE_DIRECTORY = "statistics-reporting/datasets";
  private static final String MODEL_FILE_EXTENSION = ".dataset.json";
  private final BaseDevelopService<LowcodeDatasetDefinition> datasetDevService;

  public DatasetInspector(BaseDevelopService<LowcodeDatasetDefinition> datasetDevService) {
    this.datasetDevService = datasetDevService;
  }

  private static BaseDevelopService<LowcodeDatasetDefinition> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(DatasetInspector.class).datasetDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      String modelName = file.getFileName().substring(0, file.getFileName().length() - MODEL_FILE_EXTENSION.length());
      description.setName(modelName)
          .setTitle(modelName)
          .setIcon("fa fa-database")
          .setType("dataset");

      Optional.ofNullable(currentService())
          .flatMap(service -> service.getOne(modelName))
          .ifPresent(model -> description.setTitle(model.getTitle()));
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (MODEL_FILE_DIRECTORY.equals(dir.getPath())) {
          dir.setTitle("统计数据集");
        }
      });
    }

    @Override
    public boolean support(VCSFile file) {
      return file.getPath().startsWith(MODEL_FILE_DIRECTORY + "/")
          && file.getPath().endsWith(MODEL_FILE_EXTENSION);
    }
  }
}
