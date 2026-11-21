package com.ouroboros.upload.runtime.config;

import java.nio.file.Paths;

public class FileSystemStorageProperties {
  private String rootPath;

  public FileSystemStorageProperties() {
    rootPath = Paths.get(System.getProperty("user.dir"), "upload").toString();
  }

  public String getRootPath() {
    return rootPath;
  }

  public FileSystemStorageProperties setRootPath(String rootPath) {
    if (rootPath == null || rootPath.isEmpty()) {
      return this;
    }
    if (rootPath.startsWith(".")) {
      this.rootPath = Paths.get(System.getProperty("user.dir"), rootPath.substring(1)).toString();
    } else {
      this.rootPath = Paths.get(rootPath).toString();
    }
    return this;
  }
}
