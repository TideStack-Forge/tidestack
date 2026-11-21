package com.ouroboros.vcs.action;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.filesystem.FileSystemManager;
import com.ouroboros.vcs.lock.LockManager;
import com.ouroboros.vcs.versioning.VersioningManager;

public class DefaultCreateFileAction extends AbstractSingleFileAction<VCSAction.CreateFileAction> implements VCSAction.CreateFileAction {
  private final FileSystemManager fileSystemManager;

  public DefaultCreateFileAction(VersioningManager versioningManager, FileSystemManager fileSystemManager, LockManager lockManager, VCSUserProvider vcsUserProvider, VCSFile file) {
    super(versioningManager, lockManager, vcsUserProvider, file);
    this.fileSystemManager = fileSystemManager;
  }

  @Override
  public DefaultCreateFileAction content(String content) {
    var inputStream = StringUtils.isEmpty(content)
        ? new ByteArrayInputStream(new byte[0])
        : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    return content(inputStream);
  }

  @Override
  public DefaultCreateFileAction content(byte[] content) {
    return content(new ByteArrayInputStream(content));
  }

  @Override
  public DefaultCreateFileAction content(InputStream content) {
    contentWriter = () -> fileSystemManager.createFile(file.getPath(), content).isSuccess();
    return this;
  }

  @Override
  protected void doPrepareCommitContent(VersioningManager.OperationChain chain) {
    chain.add(file.getPath());
  }
}
