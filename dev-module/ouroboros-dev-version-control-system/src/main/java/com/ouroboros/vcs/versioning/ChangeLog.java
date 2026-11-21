package com.ouroboros.vcs.versioning;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = ChangeLogForDeserialize.class)
public interface ChangeLog {
  String getId();

  /**
   * 获取变更的文件列表
   * key 为文件路径，value为变更类型，取值范围： ADD, MODIFY, DELETE, RENAME, COPY
   * @return
   */
  Map<String, String> getChanges();

  LocalDateTime getTime();

  String getAuthor();

/**
 * 获取作者名称的方法
 *
 * @return 返回一个字符串类型的作者名称
 */
  String getAuthorName();

  String getMessage();
}
