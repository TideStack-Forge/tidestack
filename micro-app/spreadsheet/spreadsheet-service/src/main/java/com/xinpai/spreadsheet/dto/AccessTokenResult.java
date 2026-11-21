/*
 * @Author: Song Mingxu
 * @Date: 2022-05-12 16:18:39
 * @LastEditors: Song Mingxu
 * @LastEditTime: 2022-05-13 12:15:16
 * @Description: file content
 */
package com.xinpai.spreadsheet.dto;

public class AccessTokenResult {
  String token;
  String spreadsheetId;
  String spreadsheetName;

  public String getToken() {
    return this.token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getSpreadsheetId() {
    return this.spreadsheetId;
  }

  public void setSpreadsheetId(String spreadsheetId) {
    this.spreadsheetId = spreadsheetId;
  }

  public String getSpreadsheetName() {
    return this.spreadsheetName;
  }

  public void setSpreadsheetName(String spreadsheetName) {
    this.spreadsheetName = spreadsheetName;
  }

}
