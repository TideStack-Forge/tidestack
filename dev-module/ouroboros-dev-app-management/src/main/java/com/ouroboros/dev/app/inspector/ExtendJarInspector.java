package com.ouroboros.dev.app.inspector;

import java.util.Set;

import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

public class ExtendJarInspector extends AbstractVCSFileInspector {
  private static final String EXTEND_JAR_DIR = "extend";
  @Override
  protected void postProcessFile(VCSFile file, FileDescription description) {
    description.setIcon("fa-brands fa-java")
        .setType("jar")
        .setName(file.getFileName());
  }

  @Override
  protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
    for (var dir : dirs) {
      if (EXTEND_JAR_DIR.equals(dir.getPath())) {
        dir.setTitle("扩展Jar包");
      }
    }
  }

  @Override
  public boolean support(VCSFile file) {
    return file.getPath().startsWith(EXTEND_JAR_DIR + "/");
  }
}
