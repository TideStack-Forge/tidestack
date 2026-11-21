/*
 * @Author: Song Mingxu
 * @Date: 2022-05-13 10:36:36
 * @LastEditors: Song Mingxu
 * @LastEditTime: 2022-05-13 10:37:16
 * @Description: file content
 */
package com.xinpai.spreadsheet.dto;

public class CreateSpreadsheetRequest {
  String appId;
  String appSecret;
  String spreadsheetName;
  String editorName;

  public String getAppId() {
    return this.appId;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }

  public String getAppSecret() {
    return this.appSecret;
  }

  public void setAppSecret(String appSecret) {
    this.appSecret = appSecret;
  }

  public String getSpreadsheetName() {
    return this.spreadsheetName;
  }

  public void setSpreadsheetName(String spreadsheetName) {
    this.spreadsheetName = spreadsheetName;
  }

  public String getEditorName() {
    return this.editorName;
  }

  public void setEditorName(String editorName) {
    this.editorName = editorName;
  }

}
