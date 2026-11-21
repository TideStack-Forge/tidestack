package com.ouroboros.vcs.wrapper;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;

public class ModelFileMetaWrapper<M> {
  final private M model;
  @JsonIgnore
  final private VCSFile file;
  @JsonIgnore
  private boolean enableCreationTime = false;

  public ModelFileMetaWrapper(M model, VCSFile file) {
    this.model = model;
    this.file = file;
  }

  public ModelFileMetaWrapper<M> setEnableCreationTime(boolean enable) {
    enableCreationTime = enable;
    return this;
  }

  public M getModel() {
    return model;
  }

  @JsonIgnore
  public VCSFile getFile() {
    return file;
  }

  public LocalDateTime getCreationTime() {
    if (enableCreationTime) {
      return file.getCreationTime();
    }
    return null;
  }

  public LocalDateTime getLastModifiedTime() {
    return file.getLastModifiedTime();
  }

  public LocalDateTime getLastCommittedTime() {
    return file.getLastCommittedTime();
  }

  public Boolean getIsLocked() {
    return file.getLockState() != LockState.UNLOCKED;
  }

  public String getLockUser() {
    return file.getLockOwner();
  }

  public String getLockUserName() {
    return file.getLockOwnerName();
  }

  public LockState getLockState() {
    return file.getLockState();
  }

  public VersionState getVersionState() {
    return file.getVersionState();
  }

  public Long getSize() {
    return file.getSize();
  }

  public Boolean getIsChanged() {
    var versionState = file.getVersionState();
    return versionState == VersionState.CHANGED || versionState == VersionState.UNKNOWN;
  }
}
