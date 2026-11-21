package com.ouroboros.vcs.versioning.git;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

import org.eclipse.jgit.revwalk.RevTag;

import com.ouroboros.vcs.VCSUserNameResolver;
import com.ouroboros.vcs.versioning.Tag;

public class GitTag implements Tag {
  private static final long serialVersionUID = 1L;
  private final static VCSUserNameResolver USER_NAME_RESOLVER = VCSUserNameResolver.getInstance();
  private final static Pattern pattern = Pattern.compile(",? ?(创建人:|操作人:|创建人：|操作人：|变更人:) ?(.*)$");
  private String name;
  private String message;
  private String author;
  private LocalDateTime time;
  public GitTag(RevTag tag) {
    this.name = tag.getTagName();
    this.message = tag.getFullMessage();
    this.author = tag.getTaggerIdent().getName();
    this.time = LocalDateTime.ofInstant(tag.getTaggerIdent().getWhen().toInstant(), tag.getTaggerIdent().getTimeZone().toZoneId());
  }

  public GitTag(String name, String message, String author, LocalDateTime time) {
    this.name = name;
    this.message = message;
    this.author = author;
    this.time = time;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getMessage() {
    var msg = Optional.ofNullable(message).orElse("<空>");
    var finalMessage = msg.replaceFirst(pattern.pattern(), "");
    return finalMessage.isEmpty() ? "<空>" : finalMessage;
  }

  @Override
  public String getAuthor() {
    var msg = Optional.ofNullable(message).orElse("");
    var matcher = pattern.matcher(msg);
    if (matcher.find()) {
      return matcher.group(2);
    }
    return author;
  }

  @Override
  public String getAuthorName() {
    return USER_NAME_RESOLVER.resolve(getAuthor());
  }

  @Override
  public LocalDateTime getTime() {
    return time;
  }
}
