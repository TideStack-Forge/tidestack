package com.ouroboros.appmodule.dev.inspector;

import java.util.Set;

import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

public class ModuleDiffInspector extends AbstractVCSFileInspector {
  private final static String MODULE_DIFF_FILE_PATH = "modules.diff.json";
  @Override
  protected void postProcessFile(VCSFile file, FileDescription description) {
    description.setIcon("app-module");
    description.setType("modules-diff");
    description.setDescription("对应用的模块树进行重新编排的Diff文件");
    description.setTitle("模块编排文件");
    description.setName(file.getFileName());
  }

  @Override
  protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {

  }

  @Override
  public boolean support(VCSFile file) {
    return MODULE_DIFF_FILE_PATH.equals(file.getPath());
  }
}
