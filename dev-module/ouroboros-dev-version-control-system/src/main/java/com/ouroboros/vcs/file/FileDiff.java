package com.ouroboros.vcs.file;

import java.util.List;

/**
 * 文件差异信息
 */
public class FileDiff {
  private final String fromRevision;
  private final String toRevision;
  private final String filePath;
  private final DiffType diffType;
  private final List<LineDiff> lineDiffs;
  private final long addedLines;
  private final long deletedLines;

  public FileDiff(String fromRevision, String toRevision, String filePath,
                  DiffType diffType, List<LineDiff> lineDiffs,
                  long addedLines, long deletedLines) {
    this.fromRevision = fromRevision;
    this.toRevision = toRevision;
    this.filePath = filePath;
    this.diffType = diffType;
    this.lineDiffs = lineDiffs;
    this.addedLines = addedLines;
    this.deletedLines = deletedLines;
  }

  public String getFromRevision() {
    return fromRevision;
  }

  public String getToRevision() {
    return toRevision;
  }

  public String getFilePath() {
    return filePath;
  }

  public DiffType getDiffType() {
    return diffType;
  }

  public List<LineDiff> getLineDiffs() {
    return lineDiffs;
  }

  public long getAddedLines() {
    return addedLines;
  }

  public long getDeletedLines() {
    return deletedLines;
  }

  public long getChangedLines() {
    return addedLines + deletedLines;
  }

  public boolean hasChanges() {
    return diffType != DiffType.IDENTICAL;
  }

  /**
   * 差异类型
   */
  public enum DiffType {
    IDENTICAL,    // 完全相同
    MODIFIED,     // 内容修改
    ADDED,        // 新增文件
    DELETED,      // 删除文件
    RENAMED       // 重命名
  }
}