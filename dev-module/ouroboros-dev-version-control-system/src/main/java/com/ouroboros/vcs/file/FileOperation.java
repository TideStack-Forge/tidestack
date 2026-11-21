package com.ouroboros.vcs.file;

/**
 * 文件操作类型
 */
public enum FileOperation {
  READ(true),
  WRITE(false),
  DELETE(false),
  MOVE(false),
  LOCK(false),
  UNLOCK(false),
  COMMIT(false),
  ROLLBACK(false);

  private final boolean readOnly;

  FileOperation(boolean readOnly) {
    this.readOnly = readOnly;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public boolean isWriteOperation() {
    return !readOnly;
  }
}