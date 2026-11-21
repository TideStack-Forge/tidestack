package com.ouroboros.vcs.versioning.git;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.vcs.VCSUserNameResolver;
import com.ouroboros.vcs.versioning.ChangeLog;

public class GitChangeLog implements ChangeLog {
  private final static Logger logger = LoggerFactory.getLogger(GitChangeLog.class);
  @JsonIgnore
  private final static VCSUserNameResolver USER_NAME_RESOLVER = VCSUserNameResolver.getInstance();
  @JsonIgnore
  private final static Pattern pattern = Pattern.compile(",? ?(创建人:|操作人:|创建人：|操作人：|变更人:) ?(.*)$");
  @JsonIgnore
  private final RevCommit commit;
  @JsonIgnore
  private final Repository repository;

  public GitChangeLog(RevCommit commit, Repository repository) {
    this.commit = commit;
    this.repository = repository;
  }

  @Override
  public String getId() {
    return commit.getName();
  }

  @Override
  public Map<String, String> getChanges() {
    var changes = getChangedFilesWithType(repository, commit);
    return changes.entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString()));
  }

  private Map<String, DiffEntry.ChangeType> getChangedFilesWithType(Repository repository, RevCommit commit) {

    Map<String, DiffEntry.ChangeType> fileChanges = new LinkedHashMap<>();

    try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE); RevWalk revWalk = new RevWalk(repository);) {
      diffFormatter.setRepository(repository);
      var parsedCommit = revWalk.parseCommit(commit.getId());

      RevCommit parent = parsedCommit.getParentCount() > 0 ? revWalk.parseCommit(parsedCommit.getParent(0)) : null;
      List<DiffEntry> diffs = parent != null
          ? diffFormatter.scan(parent.getTree(), parsedCommit.getTree())
          : diffFormatter.scan(null, parsedCommit.getTree());

      for (DiffEntry diff : diffs) {
        String path = diff.getChangeType() == DiffEntry.ChangeType.DELETE
            ? diff.getOldPath()
            : diff.getNewPath();
        fileChanges.put(path, diff.getChangeType());
      }
    } catch (Exception e) {
      logger.warn("获取文件变更信息失败！", e);
    }

    return fileChanges;
  }

  @Override
  public LocalDateTime getTime() {
    return LocalDateTime.ofInstant(commit.getAuthorIdent().getWhen().toInstant(), commit.getAuthorIdent().getTimeZone().toZoneId());
  }

  @Override
  public String getAuthor() {
    var message = Optional.ofNullable(commit.getFullMessage()).orElse("");
    var matcher = pattern.matcher(message);
    if (matcher.find()) {
      return matcher.group(2);
    }
    return commit.getAuthorIdent().getName();
  }

  @Override
  public String getAuthorName() {
    return USER_NAME_RESOLVER.resolve(getAuthor());
  }

  @Override
  public String getMessage() {
    var message = Optional.ofNullable(commit.getFullMessage()).orElse("<空>");
    var finalMessage = message.replaceFirst(pattern.pattern(), "");
    return finalMessage.isEmpty() ? "<空>" : finalMessage;
  }
}
