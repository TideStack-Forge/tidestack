package com.ouroboros.businessmodel.dev.inspector;

import java.util.Set;

import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

public class ModulesInspector extends AbstractVCSFileInspector {
  private final static String MODULES_FILE_PATH = "business-model/modules.json";

  @Override
  protected void postProcessFile(VCSFile file, FileDescription description) {
    description.setTitle("模块树定义")
        .setDescription("模块树定义")
        .setIcon("fas fa-sitemap")
        .setName(file.getFileName())
        .setType("modules");
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
    return MODULES_FILE_PATH.equals(file.getPath());
  }
}
