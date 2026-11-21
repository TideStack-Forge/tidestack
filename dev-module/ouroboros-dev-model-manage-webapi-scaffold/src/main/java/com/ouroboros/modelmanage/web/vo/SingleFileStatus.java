package com.ouroboros.modelmanage.web.vo;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.vcs.VCSUserNameResolver;
import com.ouroboros.vcs.file.VCSFile;

public class SingleFileStatus {
  private String lockState;
  private String versionState;
  private String lockOwner;
  @JsonIgnore
  private static final VCSUserNameResolver USER_NAME_RESOLVER = VCSUserNameResolver.getInstance();

  public SingleFileStatus(VCSFile file) {
    this.lockState = file.getLockState().name();
    this.versionState = file.getVersionState().name();
    this.lockOwner = file.getLockOwner();
  }

  public String getLockState() {
    return lockState;
  }

  public String getVersionState() {
    return versionState;
  }

  public String getLockOwner() {
    return lockOwner;
  }

  @JsonGetter("lockOwnerName")
  public String getLockOwnerName() {
    return USER_NAME_RESOLVER.resolve(lockOwner);
  }
}
