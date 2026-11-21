package com.ouroboros.vcs.file.inspector;

import java.util.Set;

import jakarta.annotation.Priority;

import com.ouroboros.vcs.file.VCSFile;

@Priority(-999)
public class DefaultVCSFileInspector extends AbstractVCSFileInspector {
  @Override
  protected void postProcessFile(VCSFile file, FileDescription description) {
    description.setIcon("fa fa-file");
    description.setType("unknow");
    description.setDescription("unknow file");
    description.setTitle(file.getFileName());
    description.setName(file.getFileName());
  }

  @Override
  protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
  }

  @Override
  public boolean support(VCSFile file) {
    return true;
  }
}
