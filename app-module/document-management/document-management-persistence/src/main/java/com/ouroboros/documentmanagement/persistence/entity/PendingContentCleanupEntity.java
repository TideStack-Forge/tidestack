package com.ouroboros.documentmanagement.persistence.entity;

import java.time.LocalDateTime;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/** Narrow document-owned cleanup model; never a global file index. */
@Model(label = "文档内容待清理记录", rawName = "doc_content_cleanup")
public class PendingContentCleanupEntity {
  @PrimaryKey(generator = "snowflake")
  @Field(label = "ID")
  private String id;
  @Field(label = "待清理内容键", size = 1000, nullable = false)
  private String storagePath;
  @Field(label = "已尝试次数", nullable = false)
  private Integer attempts;
  @Field(label = "下次尝试时间", nullable = false)
  private LocalDateTime nextAttemptAt;
  @Field(label = "最后错误", size = 2000, nullable = true)
  private String lastError;
  @Field(label = "创建时间", nullable = false)
  private LocalDateTime createdAt;
  public String getId() { return id; }
  public void setId(String value) { id = value; }
  public String getStoragePath() { return storagePath; }
  public void setStoragePath(String value) { storagePath = value; }
  public Integer getAttempts() { return attempts; }
  public void setAttempts(Integer value) { attempts = value; }
  public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
  public void setNextAttemptAt(LocalDateTime value) { nextAttemptAt = value; }
  public String getLastError() { return lastError; }
  public void setLastError(String value) { lastError = value; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
