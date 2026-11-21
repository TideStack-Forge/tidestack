package com.ouroboros.fieldtype.dev.inspector;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class FieldTypeInspector {
  private static final String MODEL_FILE_DIRECTORY = "field-types";
  private static final String MODEL_FILE_EXTENSION = ".field-type.json";
  private final BaseDevelopService<Map<String, Object>> fieldTypeDevService;

  public FieldTypeInspector(BaseDevelopService<Map<String, Object>> fieldTypeDevService) {
    this.fieldTypeDevService = fieldTypeDevService;
  }

  private static BaseDevelopService<Map<String, Object>> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(FieldTypeInspector.class).fieldTypeDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      var modelName = stripKnownSuffix(file.getFileName());
      description.setName(modelName)
          .setIcon("fas fa-coins")
          .setType("field-type")
          .setTitle(modelName);
      var model = Optional.ofNullable(currentService())
          .flatMap(service -> service.getOne(modelName))
          .orElse(null);
      if (model == null) {
        return;
      }
      var label = model.get("label");
      description.setTitle(label != null && StringUtils.isNotEmpty(label.toString()) ? label.toString() : modelName);
      var desc = model.get("description");
      if (desc != null && StringUtils.isNotEmpty(desc.toString())) {
        description.setDescription(desc.toString());
      }
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (dir.getPath().equals(MODEL_FILE_DIRECTORY)) {
          dir.setTitle("字段类型");
        }
      });
    }

    @Override
    public boolean support(VCSFile file) {
      return isUnderDirectory(file.getPath(), MODEL_FILE_DIRECTORY)
          && file.getPath().endsWith(MODEL_FILE_EXTENSION);
    }

    private boolean isUnderDirectory(String path, String directory) {
      return path.startsWith(directory + "/");
    }

    private String stripKnownSuffix(String fileName) {
      if (fileName.endsWith(MODEL_FILE_EXTENSION)) {
        return fileName.substring(0, fileName.length() - MODEL_FILE_EXTENSION.length());
      }
      return fileName;
    }
  }
}
