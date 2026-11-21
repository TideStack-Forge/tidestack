package com.ouroboros.documentmanagement.webapp.dto;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.documentmanagement.model.DirectoryNode;
import com.ouroboros.documentmanagement.model.LibraryNode;
import com.ouroboros.documentmanagement.model.NodeType;

/**
 * 文档库树形节点 DTO
 */
public class LibraryTreeNodeDTO {
  private String label; // 显示名称
  private String value; // 文档库 ID
  private String type; // "library"
  private String icon; // "fa fa-database"
  private boolean hasChildren; // 是否有子节点
  private boolean defer; // 是否延迟加载子节点
  private String deferApi; // 加载子节点的API路径
  private String libraryId; // 文档库ID
  private boolean disabled; // 是否已禁用
  private boolean locked; // 是否已锁定
  private List<LibraryTreeNodeDTO> children;

  public static LibraryTreeNodeDTO fromLibraryNode(LibraryNode node) {
    var children = !(node instanceof DirectoryNode dirNode)
        ? null
        : dirNode.getChildren() == null
        ? null
        : dirNode.getChildren().stream()
        .map(LibraryTreeNodeDTO::fromLibraryNode)
        .collect(Collectors.toList());
    return new LibraryTreeNodeDTO()
        .setLabel(node.getName())
        .setValue(node.getId())
        .setType(node.getType().getCode())
        .setIcon(node.getType() == NodeType.DIRECTORY ? "fa fa-folder" : "fa fa-file")
        .setHasChildren(ObjectUtils.isNotEmpty(children))
        .setChildren(children)
        .setLibraryId(node.getLibraryId())
        .setDefer(false);
  }

  public String getLabel() {
    return label;
  }

  public LibraryTreeNodeDTO setLabel(String label) {
    this.label = label;
    return this;
  }

  public String getValue() {
    return value;
  }

  public LibraryTreeNodeDTO setValue(String value) {
    this.value = value;
    return this;
  }

  public String getType() {
    return type;
  }

  public LibraryTreeNodeDTO setType(String type) {
    this.type = type;
    return this;
  }

  public String getIcon() {
    return icon;
  }

  public LibraryTreeNodeDTO setIcon(String icon) {
    this.icon = icon;
    return this;
  }

  public boolean isHasChildren() {
    return hasChildren;
  }

  public LibraryTreeNodeDTO setHasChildren(boolean hasChildren) {
    this.hasChildren = hasChildren;
    return this;
  }

  public boolean isDefer() {
    return defer;
  }

  public LibraryTreeNodeDTO setDefer(boolean defer) {
    this.defer = defer;
    return this;
  }

  public String getDeferApi() {
    return deferApi;
  }

  public LibraryTreeNodeDTO setDeferApi(String deferApi) {
    this.deferApi = deferApi;
    return this;
  }

  public String getLibraryId() {
    return libraryId;
  }

  public LibraryTreeNodeDTO setLibraryId(String libraryId) {
    this.libraryId = libraryId;
    return this;
  }

  public List<LibraryTreeNodeDTO> getChildren() {
    return children;
  }

  public LibraryTreeNodeDTO setChildren(List<LibraryTreeNodeDTO> children) {
    this.children = children;
    return this;
  }

  public boolean isDisabled() {
    return disabled;
  }

  public LibraryTreeNodeDTO setDisabled(boolean disabled) {
    this.disabled = disabled;
    return this;
  }

  public boolean isLocked() {
    return locked;
  }

  public LibraryTreeNodeDTO setLocked(boolean locked) {
    this.locked = locked;
    return this;
  }
}
