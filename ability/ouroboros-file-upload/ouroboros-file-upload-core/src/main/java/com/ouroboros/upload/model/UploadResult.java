package com.ouroboros.upload.model;

@SuppressWarnings("unused")
public class UploadResult {
  private String value;
  private String url;
  private String name;

  /**
   * 上传文件的值，一般是文件的路径
   */
  public String getValue() {
    return value;
  }

  /**
   * 上传文件的值，一般是文件的路径
   */
  public void setValue(String value) {
    this.value = value;
  }

  /**
   * 上传文件的url
   */
  public String getUrl() {
    return url;
  }

  /**
   * 上传文件的url
   */
  public void setUrl(String url) {
    this.url = url;
  }

  /**
   * 上传文件的名称
   */
  public String getName() {
    return name;
  }

  /**
   * 上传文件的名称
   */
  public void setName(String name) {
    this.name = name;
  }
}
