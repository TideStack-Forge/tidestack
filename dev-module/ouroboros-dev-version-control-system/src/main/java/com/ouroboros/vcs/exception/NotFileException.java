package com.ouroboros.vcs.exception;

public class NotFileException extends VCSException {
  private final String filePath;

  public NotFileException(String message, String filePath) {
    super(VCSErrorCode.FILE_IS_DIRECTORY, filePath);
    this.filePath = filePath;
  }

  public NotFileException(String filePath) {
    super(VCSErrorCode.FILE_IS_DIRECTORY, filePath);
    this.filePath = filePath;
  }

  public String getFilePath() {
    return filePath;
  }
}
