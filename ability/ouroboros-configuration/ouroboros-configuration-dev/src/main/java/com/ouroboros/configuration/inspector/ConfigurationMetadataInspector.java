package com.ouroboros.configuration.inspector;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class ConfigurationMetadataInspector {
  private static final String MODEL_FILE_DIRECTORY = "configuration";
  private static final String MODEL_FILE_EXTENSION = ".config.json";
  private final BaseDevelopService<ConfigurationMetadata> configurationMetadataDevService;

  public ConfigurationMetadataInspector(BaseDevelopService<ConfigurationMetadata> configurationMetadataDevService) {
    this.configurationMetadataDevService = configurationMetadataDevService;
  }

  private static BaseDevelopService<ConfigurationMetadata> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(ConfigurationMetadataInspector.class).configurationMetadataDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      var modelName = file.getFileName().substring(0, file.getFileName().length() - MODEL_FILE_EXTENSION.length());
      description.setName(modelName)
          .setIcon("configuration")
          .setType("configuration")
          .setTitle(modelName);
      var model = Optional.ofNullable(currentService())
          .flatMap(service -> service.getOne(modelName))
          .orElse(null);
      if (model == null) {
        return;
      }
      description.setTitle(StringUtils.isEmpty(model.getLabel()) ? modelName : model.getLabel())
          .setDescription(model.getDescription());
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (dir.getPath().equals(MODEL_FILE_DIRECTORY)) {
          dir.setTitle("配置项");
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
