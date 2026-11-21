package com.ouroboros.vcs.file.inspector;

import java.util.Objects;

public class FileDescription implements DescriptionNode {
  private String parent;
  private String path;
  private String fileName;
  private String name;
  private String title;
  private String description;
  private String icon;
  private String type;
  private String size;
  private String lastModified;
  private String lastModifiedBy;
  private String created;
  private String createdBy;
  private String version;
  private String versionState;
  private String lockBy;
  private String lockState;

  @Override
  public String getParent() {
    return parent;
  }

  public FileDescription setParent(String parent) {
    this.parent = parent;
    return this;
  }

  @Override
  public String getPath() {
    return path;
  }

  public FileDescription setPath(String path) {
    this.path = path;
    return this;
  }

  public String getFileName() {
    return fileName;
  }

  public FileDescription setFileName(String fileName) {
    this.fileName = fileName;
    return this;
  }

  @Override
  public String getName() {
    return name;
  }

  public FileDescription setName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public String getTitle() {
    return title;
  }

  public FileDescription setTitle(String title) {
    this.title = title;
    return this;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public FileDescription setDescription(String description) {
    this.description = description;
    return this;
  }

  @Override
  public String getIcon() {
    return icon;
  }

  public FileDescription setIcon(String icon) {
    this.icon = icon;
    return this;
  }

  public String getType() {
    return type;
  }

  public FileDescription setType(String type) {
    this.type = type;
    return this;
  }

  public String getSize() {
    return size;
  }

  public FileDescription setSize(String size) {
    this.size = size;
    return this;
  }

  public String getLastModified() {
    return lastModified;
  }

  public FileDescription setLastModified(String lastModified) {
    this.lastModified = lastModified;
    return this;
  }

  public String getLastModifiedBy() {
    return lastModifiedBy;
  }

  public FileDescription setLastModifiedBy(String lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
    return this;
  }

  public String getCreated() {
    return created;
  }

  public FileDescription setCreated(String created) {
    this.created = created;
    return this;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public FileDescription setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
    return this;
  }

  public String getVersion() {
    return version;
  }

  public FileDescription setVersion(String version) {
    this.version = version;
    return this;
  }

  public String getVersionState() {
    return versionState;
  }

  public FileDescription setVersionState(String versionState) {
    this.versionState = versionState;
    return this;
  }

  public String getLockBy() {
    return lockBy;
  }

  public FileDescription setLockBy(String lockBy) {
    this.lockBy = lockBy;
    return this;
  }

  public String getLockState() {
    return lockState;
  }

  public FileDescription setLockState(String lockState) {
    this.lockState = lockState;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(path);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof FileDescription other && Objects.equals(this.path, other.path);
  }
}
