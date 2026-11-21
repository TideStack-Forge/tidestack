package com.ouroboros.vcs.file.inspector;

import java.util.Optional;
import java.util.Set;

import com.ouroboros.vcs.file.VCSFile;

public interface VCSFileInspector {
  default boolean support(VCSFile file) {
    return false;
  }
  Optional<FileDescription> inspect(VCSFile file);
  Set<DirDescription> inspectDir(VCSFile file);
}
