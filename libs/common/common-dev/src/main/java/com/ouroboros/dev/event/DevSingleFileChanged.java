package com.ouroboros.dev.event;

public class DevSingleFileChanged extends DevAppEvent {
  private String fileName;

  public DevSingleFileChanged(Object source, String appName, String fileName) {
    super(source, appName);
    this.fileName = fileName;
  }

  public String getFileName() {
    return fileName;
  }
}
