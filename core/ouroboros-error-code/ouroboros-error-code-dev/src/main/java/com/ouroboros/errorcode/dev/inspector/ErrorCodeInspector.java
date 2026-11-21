package com.ouroboros.errorcode.dev.inspector;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class ErrorCodeInspector {
  private static final String MODEL_FILE_PATH = "error-codes.error.json";

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      description.setName("error-codes")
          .setTitle("错误码")
          .setIcon("fa fa-bug")
          .setType("error-code");
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
    }

    @Override
    public boolean support(VCSFile file) {
      return MODEL_FILE_PATH.equals(file.getPath());
    }
  }
}
