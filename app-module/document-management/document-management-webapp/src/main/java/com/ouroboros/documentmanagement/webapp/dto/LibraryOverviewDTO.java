package com.ouroboros.documentmanagement.webapp.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档库概览 DTO
 */
public class LibraryOverviewDTO {
  // 基本信息
  private String id;
  private String name;
  private String displayName;
  private String description;
  private String owner;
  private String ownerDisplayName; // 所有者显示名称
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  // 状态
  private boolean deleted;
  private boolean disabled;
  private boolean locked;

  // 权限角色
  private boolean isAdmin; // 是否是管理员
  private boolean isOwner; // 是否是所有者

  // 统计数据（格式化后的）
  private List<StatisticItemDTO> statistics;

  private String rootDirectoryId;

  public String getId() {
    return id;
  }

  public LibraryOverviewDTO setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public LibraryOverviewDTO setName(String name) {
    this.name = name;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public LibraryOverviewDTO setDisplayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public LibraryOverviewDTO setDescription(String description) {
    this.description = description;
    return this;
  }

  public String getOwner() {
    return owner;
  }

  public LibraryOverviewDTO setOwner(String owner) {
    this.owner = owner;
    return this;
  }

  public String getOwnerDisplayName() {
    return ownerDisplayName;
  }

  public LibraryOverviewDTO setOwnerDisplayName(String ownerDisplayName) {
    this.ownerDisplayName = ownerDisplayName;
    return this;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LibraryOverviewDTO setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public LibraryOverviewDTO setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public LibraryOverviewDTO setDeleted(boolean deleted) {
    this.deleted = deleted;
    return this;
  }

  public boolean isDisabled() {
    return disabled;
  }

  public LibraryOverviewDTO setDisabled(boolean disabled) {
    this.disabled = disabled;
    return this;
  }

  public boolean isLocked() {
    return locked;
  }

  public LibraryOverviewDTO setLocked(boolean locked) {
    this.locked = locked;
    return this;
  }

  public boolean isAdmin() {
    return isAdmin;
  }

  public LibraryOverviewDTO setIsAdmin(boolean isAdmin) {
    this.isAdmin = isAdmin;
    return this;
  }

  public boolean isOwner() {
    return isOwner;
  }

  public LibraryOverviewDTO setIsOwner(boolean isOwner) {
    this.isOwner = isOwner;
    return this;
  }

  public List<StatisticItemDTO> getStatistics() {
    return statistics;
  }

  public LibraryOverviewDTO setStatistics(List<StatisticItemDTO> statistics) {
    this.statistics = statistics;
    return this;
  }

  public String getRootDirectoryId() {
    return rootDirectoryId;
  }

  public LibraryOverviewDTO setRootDirectoryId(String rootDirectoryId) {
    this.rootDirectoryId = rootDirectoryId;
    return this;
  }
}