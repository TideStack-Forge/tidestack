package com.ouroboros.vcs.versioning;

import java.time.LocalDateTime;

public interface Tag {
  String getName();
  String getMessage();
  String getAuthor();
  String getAuthorName();
  LocalDateTime getTime();
}
