package com.ouroboros.vcs.file.inspector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DirDescription implements DescriptionNode {
  private String parent;
  private String path;
  private String name;
  private String title;
  private String icon;
  private String description;
  private List<DescriptionNode> children = new ArrayList<>();

  @Override
  public String getParent() {
    return parent;
  }

  public DirDescription setParent(String parent) {
    this.parent = parent;
    return this;
  }

  @Override
  public String getPath() {
    return path;
  }

  public DirDescription setPath(String path) {
    this.path = path;
    return this;
  }

  @Override
  public String getName() {
    return name;
  }

  public DirDescription setName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public String getTitle() {
    return title;
  }

  public DirDescription setTitle(String title) {
    this.title = title;
    return this;
  }

  @Override
  public String getIcon() {
    return icon;
  }

  public DirDescription setIcon(String icon) {
    this.icon = icon;
    return this;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public DirDescription setDescription(String description) {
    this.description = description;
    return this;
  }

  @Override
  public List<? extends DescriptionNode> getChildren() {
    return children;
  }

  public DirDescription setChildren(List<? extends DescriptionNode> children) {
    this.children = new ArrayList<>(children);
    return this;
  }

  public DirDescription addChild(DescriptionNode child) {
    this.children.add(child);
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(path);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof DirDescription other && Objects.equals(this.path, other.path);
  }
}
