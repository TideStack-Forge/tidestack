package com.ouroboros.vcs.file;

/**
 * 行差异信息
 */
public class LineDiff {
  private final int lineNumber;
  private final DiffAction action;
  private final String oldContent;
  private final String newContent;

  public LineDiff(int lineNumber, DiffAction action, String oldContent, String newContent) {
    this.lineNumber = lineNumber;
    this.action = action;
    this.oldContent = oldContent;
    this.newContent = newContent;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public DiffAction getAction() {
    return action;
  }

  public String getOldContent() {
    return oldContent;
  }

  public String getNewContent() {
    return newContent;
  }

  public boolean isAdded() {
    return action == DiffAction.ADD;
  }

  public boolean isDeleted() {
    return action == DiffAction.DELETE;
  }

  public boolean isModified() {
    return action == DiffAction.MODIFY;
  }

  /**
   * 差异动作
   */
  public enum DiffAction {
    ADD,      // 新增行
    DELETE,   // 删除行
    MODIFY    // 修改行
  }
}