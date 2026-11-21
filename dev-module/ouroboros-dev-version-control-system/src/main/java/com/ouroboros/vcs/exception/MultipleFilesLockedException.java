package com.ouroboros.vcs.exception;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MultipleFilesLockedException extends VCSException {
  private final Map<String, String> lockedFileOwnersMap;

  public MultipleFilesLockedException(Map<String, String> lockedFileOwnersMap) {
    super(VCSErrorCode.MULTIPLE_FILES_LOCKED, formatLockedFilesMap(lockedFileOwnersMap));
    this.lockedFileOwnersMap = Collections.unmodifiableMap(lockedFileOwnersMap);
  }

  private static String formatLockedFilesMap(Map<String, String> lockedFileOwnersMap) {
    return lockedFileOwnersMap.entrySet().stream()
        .map(entry -> entry.getKey() + ":" + entry.getValue())
        .collect(Collectors.joining(", "));
  }

  public Map<String, String> getLockedFileOwnersMap() {
    return lockedFileOwnersMap;
  }

  public Set<String> getLockedFiles() {
    return lockedFileOwnersMap.keySet();
  }

  public Set<String> getLockedFileOwners() {
    return lockedFileOwnersMap.values()
        .stream()
        .collect(Collectors.toSet());
  }
}
