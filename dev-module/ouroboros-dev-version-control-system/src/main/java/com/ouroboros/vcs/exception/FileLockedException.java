package com.ouroboros.vcs.exception;

public class FileLockedException extends VCSException {
  private final String filePath;
  private final String lockOwner;

  public FileLockedException(String filePath, String lockOwner) {
    super(VCSErrorCode.FILE_LOCKED, filePath, lockOwner);
    this.filePath = filePath;
    this.lockOwner = lockOwner;
  }

  public FileLockedException() {
    super(VCSErrorCode.FILE_LOCKED, "", "");
    this.filePath = "";
    this.lockOwner = "";
  }

  public String getFilePath() {
    return filePath;
  }

  public String getLockOwner() {
    return lockOwner;
  }
}
