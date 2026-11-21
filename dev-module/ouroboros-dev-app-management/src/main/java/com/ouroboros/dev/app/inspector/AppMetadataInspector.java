package com.ouroboros.dev.app.inspector;


import java.util.Set;

import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

public class AppMetadataInspector extends AbstractVCSFileInspector {
  private final static String METADATA_FILE_PATH = "app.metadata.json";

  @Override
  protected void postProcessFile(VCSFile file, FileDescription description) {
    description.setIcon("app-setting");
    description.setType("app-metadata");
    description.setDescription("应用元数据");
    description.setTitle("应用元数据");
    description.setName(file.getFileName());
  }

  @Override
  protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {

  }

  @Override
  public boolean support(VCSFile file) {
    return METADATA_FILE_PATH.equals(file.getPath());
  }
}
