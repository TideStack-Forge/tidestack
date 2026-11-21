package com.ouroboros.vcs.exception;

public class FileExistException extends VCSException {
  private final String filePath;

  public FileExistException(String filePath) {
    super(VCSErrorCode.FILE_ALREADY_EXISTS, filePath);
    this.filePath = filePath;
  }

  public FileExistException(String message, String filePath) {
    super(message);
    this.filePath = filePath;
  }

  public String getFilePath() {
    return filePath;
  }
}
