package com.ouroboros.dev.app.model;

import java.util.ArrayList;
import java.util.List;

public class DebugArtifactUploadRequest {
  private String devKey;
  private final List<UploadFile> files = new ArrayList<UploadFile>();

  public String getDevKey() {
    return devKey;
  }

  public void setDevKey(String devKey) {
    this.devKey = devKey;
  }

  public List<UploadFile> getFiles() {
    return files;
  }

  public static class UploadFile {
    private String fileName;
    private String contentBase64;

    public String getFileName() {
      return fileName;
    }

    public void setFileName(String fileName) {
      this.fileName = fileName;
    }

    public String getContentBase64() {
      return contentBase64;
    }

    public void setContentBase64(String contentBase64) {
      this.contentBase64 = contentBase64;
    }
  }
}
