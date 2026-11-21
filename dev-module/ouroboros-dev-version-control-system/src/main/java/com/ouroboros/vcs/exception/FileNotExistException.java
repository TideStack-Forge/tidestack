package com.ouroboros.vcs.exception;

public class FileNotExistException extends VCSException {
  private final String filePath;

  public FileNotExistException(String filePath) {
    super(VCSErrorCode.FILE_NOT_FOUND, filePath);
    this.filePath = filePath;
  }

  public FileNotExistException(String message, String filePath) {
    super(message);
    this.filePath = filePath;
  }

  public String getFilePath() {
    return filePath;
  }
}
