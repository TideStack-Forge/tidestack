package com.ouroboros.vcs.action;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.filesystem.FileSystemManager;
import com.ouroboros.vcs.lock.LockManager;
import com.ouroboros.vcs.versioning.VersioningManager;

public class DefaultUpdateFileAction extends AbstractSingleFileAction<VCSAction.UpdateFileAction> implements VCSAction.UpdateFileAction {
  private final static Logger LOGGER = LoggerFactory.getLogger(DefaultUpdateFileAction.class);

  private final FileSystemManager fileSystemManager;

  public DefaultUpdateFileAction(VersioningManager versioningManager, FileSystemManager fileSystemManager, LockManager lockManager, VCSUserProvider vcsUserProvider, VCSFile file) {
    super(versioningManager, lockManager, vcsUserProvider, file);
    this.fileSystemManager = fileSystemManager;
  }

  @Override
  public UpdateFileAction content(String content) {
    var inputStream = StringUtils.isEmpty(content)
        ? new ByteArrayInputStream(new byte[0])
        : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    return content(inputStream);
  }

  @Override
  public UpdateFileAction content(byte[] content) {
    return content(new ByteArrayInputStream(content));
  }

  @Override
  public UpdateFileAction content(InputStream content) {
    contentWriter = () -> fileSystemManager.write(file.getPath(), content)
        .onFailure(e -> LOGGER.error("写入内容失败", e))
        .isSuccess();
    return this;
  }

  @Override
  protected void doPrepareCommitContent(VersioningManager.OperationChain chain) {
    chain.add(file.getPath());
  }
}