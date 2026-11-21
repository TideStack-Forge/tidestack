package com.ouroboros.vcs.file.inspector;

import java.util.*;

import com.ouroboros.util.Converter;
import com.ouroboros.vcs.file.VCSFile;

public abstract class AbstractVCSFileInspector implements VCSFileInspector {
  @Override
  public Optional<FileDescription> inspect(VCSFile file) {
    if (!support(file)) {
      return Optional.empty();
    }
    var description = new FileDescription();
    description.setPath(file.getPath())
        .setParent(file.getPath().contains("/")
            ? file.getPath().substring(0, file.getPath().lastIndexOf("/"))
            : "")
        .setSize(file.getSize().toString())
        .setLastModified(Converter.toString(file.getLastModifiedTime()))
        .setCreated(Converter.toString(file.getCreationTime()))
        .setLockBy(file.getLockOwnerName())
        .setLockState(file.getLockState().toString())
        .setVersionState(file.getVersionState().toString());
    postProcessFile(file, description);
    return Optional.of(description);
  }

  @Override
  public Set<DirDescription> inspectDir(VCSFile file) {
    if (!support(file)) {
      return Collections.emptySet();
    }
    var dirs = new LinkedHashSet<DirDescription>();
    // 切分路径
    var path = file.getPath();
    var paths = path.split("/");
    if (paths.length > 1) {
      for (int i = 0; i < paths.length - 1; i++) {
        var dir = new DirDescription();
        var pathArray = Arrays.asList(paths).subList(0, i + 1);
        dir.setName(paths[i])
            .setTitle(paths[i])
            .setIcon("fa fa-folder")
            .setPath(String.join("/", pathArray))
            .setParent(pathArray.size() > 1
                ? String.join("/", pathArray.subList(0, pathArray.size() - 1))
                : "");
        dirs.add(dir);
      }
    }
    postProcessDirs(file, dirs);
    return dirs;
  }

  protected abstract void postProcessFile(VCSFile file, FileDescription description);

  protected abstract void postProcessDirs(VCSFile file, Set<DirDescription> dirs);

  @Override
  public abstract boolean support(VCSFile file);
}
