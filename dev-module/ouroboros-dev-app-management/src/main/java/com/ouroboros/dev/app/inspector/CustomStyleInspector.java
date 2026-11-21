package com.ouroboros.dev.app.inspector;

import java.util.Set;

import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

public class CustomStyleInspector extends AbstractVCSFileInspector {
  private static final String FILE_NAME = "style.css";

  @Override
  protected void postProcessFile(VCSFile file, FileDescription description) {
    description.setIcon("fas fa-paint-brush")
        .setType("app-style")
        .setName(FILE_NAME)
        .setTitle("主题样式");
  }

  @Override
  protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {

  }

  @Override
  public boolean support(VCSFile file) {
    return FILE_NAME.equals(file.getFileName());
  }
}
