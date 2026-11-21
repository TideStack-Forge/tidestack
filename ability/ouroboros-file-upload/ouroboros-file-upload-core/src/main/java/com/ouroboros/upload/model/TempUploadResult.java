package com.ouroboros.upload.model;

@SuppressWarnings("unused")
public class TempUploadResult extends UploadResult {
  private String tempFile;

  /**
   * 是否是临时文件，固定返回true
   */
  public Boolean getIsTemp() {
    return true;
  }

  /**
   * 临时文件的路径
   */
  public String getTempFile() {
    return tempFile;
  }

  /**
   * 临时文件的路径
   */
  public void setTempFile(String tempFile) {
    this.tempFile = tempFile;
  }
}
