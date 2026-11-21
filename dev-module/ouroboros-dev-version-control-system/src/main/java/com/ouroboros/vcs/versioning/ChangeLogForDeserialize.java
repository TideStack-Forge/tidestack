package com.ouroboros.vcs.versioning;


import java.time.LocalDateTime;
import java.util.Map;

class ChangeLogForDeserialize implements ChangeLog {
  String id;
  LocalDateTime time;
  String author;
  String authorName;
  String message;
  Map<String, String> changes;

  @Override
  public String getId() {
    return id;
  }

  public ChangeLogForDeserialize setId(String id) {
    this.id = id;
    return this;
  }

  @Override
  public Map<String, String> getChanges() {
    return changes;
  }

  public ChangeLogForDeserialize setChanges(Map<String, String> changes) {
    this.changes = changes;
    return this;
  }

  @Override
  public LocalDateTime getTime() {
    return time;
  }

  public ChangeLogForDeserialize setTime(LocalDateTime time) {
    this.time = time;
    return this;
  }

  @Override
  public String getAuthor() {
    return author;
  }

  public ChangeLogForDeserialize setAuthor(String author) {
    this.author = author;
    return this;
  }

  @Override
  public String getAuthorName() {
    return authorName;
  }

  public ChangeLogForDeserialize setAuthorName(String authorName) {
    this.authorName = authorName;
    return this;
  }

  @Override
  public String getMessage() {
    return message;
  }

  public ChangeLogForDeserialize setMessage(String message) {
    this.message = message;
    return this;
  }
}