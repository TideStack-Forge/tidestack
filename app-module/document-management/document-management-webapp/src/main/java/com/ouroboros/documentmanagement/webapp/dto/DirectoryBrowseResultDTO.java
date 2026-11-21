package com.ouroboros.documentmanagement.webapp.dto;

import java.util.List;

import com.ouroboros.documentmanagement.model.LibraryNode;

/**
 * 目录浏览结果 DTO
 */
public class DirectoryBrowseResultDTO {
  private String libraryId;
  private String directoryId;
  private String directoryName;
  private PermissionsDTO permissions;
  private List<LibraryNode> path;
  private List<NodeItemDTO> items;

  public String getLibraryId() {
    return libraryId;
  }

  public DirectoryBrowseResultDTO setLibraryId(String libraryId) {
    this.libraryId = libraryId;
    return this;
  }

  public String getDirectoryId() {
    return directoryId;
  }

  public DirectoryBrowseResultDTO setDirectoryId(String directoryId) {
    this.directoryId = directoryId;
    return this;
  }

  public String getDirectoryName() {
    return directoryName;
  }

  public DirectoryBrowseResultDTO setDirectoryName(String directoryName) {
    this.directoryName = directoryName;
    return this;
  }

  public PermissionsDTO getPermissions() {
    return permissions;
  }

  public DirectoryBrowseResultDTO setPermissions(PermissionsDTO permissions) {
    this.permissions = permissions;
    return this;
  }

  public List<LibraryNode> getPath() {
    return path;
  }

  public DirectoryBrowseResultDTO setPath(List<LibraryNode> path) {
    this.path = path;
    return this;
  }

  public List<NodeItemDTO> getItems() {
    return items;
  }

  public DirectoryBrowseResultDTO setItems(List<NodeItemDTO> items) {
    this.items = items;
    return this;
  }
}