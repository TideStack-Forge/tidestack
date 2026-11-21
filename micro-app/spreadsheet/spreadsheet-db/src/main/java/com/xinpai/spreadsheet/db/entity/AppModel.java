/*
 * @Author: Song Mingxu
 * @Date: 2022-05-10 15:42:36
 * @LastEditors: Song Mingxu
 * @LastEditTime: 2022-05-12 12:20:04
 * @Description: file content
 */
package com.xinpai.spreadsheet.db.entity;

import java.time.LocalDateTime;

public class AppModel {
  // app id
  String id;

  // app 的二级 secret， 二级 secret 可由一级 secret 生成
  // 一级 secret 只在创建应用的时候生成，返回给前端后不会保存
  String secret;

  // app名
  String name;

  // 描述
  String description;

  // 状态
  String status;

  // 创建时间
  LocalDateTime created_at;

  // 更新时间
  LocalDateTime updated_at;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getCreated_at() {
    return created_at;
  }

  public void setCreated_at(LocalDateTime created_at) {
    this.created_at = created_at;
  }

  public LocalDateTime getUpdated_at() {
    return updated_at;
  }

  public void setUpdated_at(LocalDateTime updated_at) {
    this.updated_at = updated_at;
  }

  
}
