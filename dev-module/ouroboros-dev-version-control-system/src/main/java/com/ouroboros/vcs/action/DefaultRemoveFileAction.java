package com.ouroboros.vcs.action;


import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.filesystem.FileSystemManager;
import com.ouroboros.vcs.lock.LockManager;
import com.ouroboros.vcs.versioning.VersioningManager;

public class DefaultRemoveFileAction extends AbstractSingleFileAction<VCSAction.RemoveFileAction> implements VCSAction.RemoveFileAction {
  private final FileSystemManager fileSystemManager;

  public DefaultRemoveFileAction(VersioningManager versioningManager, FileSystemManager fileSystemManager, LockManager lockManager, VCSUserProvider vcsUserProvider, VCSFile file) {
    super(versioningManager, lockManager, vcsUserProvider, file);
    this.fileSystemManager = fileSystemManager;
  }

  @Override
  protected boolean doWriteContent() {
    var deleteResult = fileSystemManager.delete(file.getPath())
        .peek(ignore -> versioningManager.notifyFileChange(file.getPath()));
    return deleteResult.isSuccess();
  }

  @Override
  protected void doPrepareCommitContent(VersioningManager.OperationChain chain) {
    chain.remove(file.getPath());
  }
}
