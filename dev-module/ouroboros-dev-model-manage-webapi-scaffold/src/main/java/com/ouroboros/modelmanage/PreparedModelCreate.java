package com.ouroboros.modelmanage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.action.VCSAction;

/** A validated resource set, ready to join its owner's VCS batch without writing early. */
public final class PreparedModelCreate {
  private final String primaryPath;
  private final String primaryContent;
  private final Map<String, byte[]> contents;
  private final List<String> collisionPaths;
  private final VCSManager owner;

  public PreparedModelCreate(String primaryPath, String primaryContent, Map<String, byte[]> contents,
                             List<String> collisionPaths, VCSManager owner) {
    this.primaryPath = primaryPath;
    this.primaryContent = primaryContent;
    var copy = new LinkedHashMap<String, byte[]>();
    contents.forEach((path, bytes) -> copy.put(path, bytes.clone()));
    this.contents = Collections.unmodifiableMap(copy);
    this.collisionPaths = List.copyOf(collisionPaths);
    this.owner = owner;
  }

  public String getPrimaryPath() { return primaryPath; }
  public String getPrimaryContent() { return primaryContent; }
  public Set<String> getPaths() { return contents.keySet(); }
  public Map<String, byte[]> getContents() {
    var copy = new LinkedHashMap<String, byte[]>();
    contents.forEach((path, bytes) -> copy.put(path, bytes.clone()));
    return Collections.unmodifiableMap(copy);
  }

  public void appendTo(VCSAction.BatchAction batch) {
    checkAvailable();
    batch.beforeWrite(this::checkAvailable);
    contents.forEach((path, bytes) -> batch.create(path, bytes.clone()));
  }

  private void checkAvailable() {
    for (String path : collisionPaths) {
      if (owner.getFile(path).exists()) {
        throw new BusinessException("准备创建的模型资源已存在: " + path);
      }
    }
  }
}
