package com.ouroboros.vcs.file;

import java.time.LocalDateTime;

/**
 * 文件版本信息
 */
public class FileRevision {
  private final String revision;
  private final String author;
  private final LocalDateTime commitTime;
  private final String commitMessage;
  private final long fileSize;
  private final RevisionType type;

  public FileRevision(String revision, String author, LocalDateTime commitTime,
                     String commitMessage, long fileSize, RevisionType type) {
    this.revision = revision;
    this.author = author;
    this.commitTime = commitTime;
    this.commitMessage = commitMessage;
    this.fileSize = fileSize;
    this.type = type;
  }

  public String getRevision() {
    return revision;
  }

  public String getAuthor() {
    return author;
  }

  public LocalDateTime getCommitTime() {
    return commitTime;
  }

  public String getCommitMessage() {
    return commitMessage;
  }

  public long getFileSize() {
    return fileSize;
  }

  public RevisionType getType() {
    return type;
  }

  public boolean isCommit() {
    return type == RevisionType.COMMIT;
  }

  public boolean isBranch() {
    return type == RevisionType.BRANCH;
  }

  public boolean isTag() {
    return type == RevisionType.TAG;
  }

  /**
   * 版本类型
   */
  public enum RevisionType {
    COMMIT,   // 提交
    BRANCH,   // 分支
    TAG       // 标签
  }
}