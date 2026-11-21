/*
 * @Author: Song Mingxu
 * @Date: 2022-05-12 16:17:23
 * @LastEditors: Song Mingxu
 * @LastEditTime: 2022-05-15 14:27:38
 * @Description: file content
 */
package com.xinpai.spreadsheet.dto;

public class AccessTokenPayload {
  String appId;
  String spreadsheetId;
  String editorName;
  Boolean readonly = false;

  public String getAppId() {
    return this.appId;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }

  public String getSpreadsheetId() {
    return this.spreadsheetId;
  }

  public void setSpreadsheetId(String spreadsheetId) {
    this.spreadsheetId = spreadsheetId;
  }

  public String getEditorName() {
    return this.editorName;
  }

  public void setEditorName(String editorName) {
    this.editorName = editorName;
  }

  public Boolean getReadonly() {
    return this.readonly;
  }

  public void setReadonly(Boolean readonly) {
    this.readonly = readonly;
  }

}
