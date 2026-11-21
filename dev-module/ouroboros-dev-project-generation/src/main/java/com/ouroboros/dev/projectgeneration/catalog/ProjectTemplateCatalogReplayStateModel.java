package com.ouroboros.dev.projectgeneration.catalog;

import java.time.LocalDateTime;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "t_dev_project_template_catalog_replay_state", label = "项目模板目录防重放高水位")
public class ProjectTemplateCatalogReplayStateModel {

  @Field(size = 96)
  @PrimaryKey
  private String catalogStateId;

  @Field(type = "Long")
  private Long sequence;

  @Field(size = 64)
  private String digest;

  @Field(type = "Long")
  private Long version;

  @Field(nullable = true)
  private LocalDateTime updatedAt;

  public String getCatalogStateId() {
    return catalogStateId;
  }

  public void setCatalogStateId(String catalogStateId) {
    this.catalogStateId = catalogStateId;
  }

  public Long getSequence() {
    return sequence;
  }

  public void setSequence(Long sequence) {
    this.sequence = sequence;
  }

  public String getDigest() {
    return digest;
  }

  public void setDigest(String digest) {
    this.digest = digest;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
