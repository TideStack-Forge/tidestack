package com.ouroboros.vcs.file.inspector;

import java.util.List;

public interface DescriptionNode {
  String getName();
  String getPath();
  String getParent();
  String getIcon();
  String getTitle();
  String getDescription();
  default List<? extends DescriptionNode> getChildren() {
    return null;
  }
}
