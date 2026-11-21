package com.ouroboros.dev.app.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.websocket.StompCompensationRegistrar;

@Service
public class AppDevHelperLogService {
  private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final AppLogCacheService appLogCacheService;
  private final ConcurrentMap<String, Set<String>> subscribersByApp = new ConcurrentHashMap<String, Set<String>>();
  private final ConcurrentMap<String, List<String>> runtimeSnapshotsByApp = new ConcurrentHashMap<String, List<String>>();
  private StompCompensationRegistrar.UserTopicChannel helperLogsChannel;

  public AppDevHelperLogService(AppLogCacheService appLogCacheService) {
    this.appLogCacheService = appLogCacheService;
  }

  public void bindChannel(StompCompensationRegistrar.UserTopicChannel helperLogsChannel) {
    this.helperLogsChannel = helperLogsChannel;
  }

  public void appendSystemLog(String appName, String message) {
    append(appName, format("SYSTEM", message));
  }

  public void appendCommandResult(String appName, CommandResult result) {
    if (result == null) {
      return;
    }
    StringBuilder builder = new StringBuilder();
    builder.append(result.getSuccess() ? "SUCCESS" : "ERROR");
    if (StringUtils.isNotBlank(result.getCommand())) {
      builder.append(" ").append(result.getCommand());
    }
    if (result.getRuntimeGeneration() != null) {
      builder.append(" generation=").append(result.getRuntimeGeneration());
    }
    if (StringUtils.isNotBlank(result.getMessage())) {
      builder.append(" ").append(result.getMessage());
    }
    append(appName, format("RUNTIME", builder.toString()));
  }

  public void appendRuntimeOutput(String appName, List<String> runtimeLines) {
    if (StringUtils.isBlank(appName) || runtimeLines == null || runtimeLines.isEmpty()) {
      return;
    }
    String normalizedAppName = appName.trim();
    List<String> normalizedRuntimeLines = runtimeLines.stream()
        .map(this::normalizeRuntimeLine)
        .filter(StringUtils::isNotEmpty)
        .collect(Collectors.toList());
    if (normalizedRuntimeLines.isEmpty()) {
      return;
    }
    List<String> previousSnapshot = runtimeSnapshotsByApp.getOrDefault(normalizedAppName, Collections.<String>emptyList());
    int appendFrom = appendStartIndex(previousSnapshot, normalizedRuntimeLines);
    if (appendFrom < normalizedRuntimeLines.size()) {
      append(normalizedAppName, joinLines(normalizedRuntimeLines.subList(appendFrom, normalizedRuntimeLines.size())));
    }
    runtimeSnapshotsByApp.put(normalizedAppName, new ArrayList<String>(normalizedRuntimeLines));
  }

  public List<String> getCachedLogs(String appName) {
    return appLogCacheService.getCachedHelperLogs(appName);
  }

  public Iterable<String> getReplayPayloads(String appName) {
    List<String> logs = getCachedLogs(appName);
    if (logs == null || logs.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.singletonList(joinCachedPayloads(logs));
  }

  public void resetRuntimeOutput(String appName) {
    if (StringUtils.isBlank(appName)) {
      return;
    }
    runtimeSnapshotsByApp.remove(appName.trim());
  }

  public void registerSubscriber(String appName, String username) {
    if (StringUtils.isBlank(appName) || StringUtils.isBlank(username)) {
      return;
    }
    subscribersByApp.computeIfAbsent(appName.trim(), key -> ConcurrentHashMap.newKeySet()).add(username.trim());
  }

  public void clear(String appName) {
    appLogCacheService.clearHelperCache(appName);
    subscribersByApp.remove(appName);
    runtimeSnapshotsByApp.remove(appName);
  }

  private void append(String appName, String line) {
    if (StringUtils.isBlank(appName) || StringUtils.isBlank(line)) {
      return;
    }
    String normalizedAppName = appName.trim();
    String normalizedLine = line.endsWith("\n") ? line : line + "\n";
    appLogCacheService.addHelperLog(normalizedAppName, normalizedLine);
    if (helperLogsChannel == null) {
      return;
    }
    Map<String, String> pathVariables = new LinkedHashMap<String, String>();
    pathVariables.put("appName", normalizedAppName);
    subscribersByApp.getOrDefault(normalizedAppName, java.util.Collections.emptySet())
        .forEach(username -> helperLogsChannel.sendToUser(username, pathVariables, normalizedLine));
  }

  private String format(String type, String message) {
    return "[" + LocalDateTime.now().format(TS_FORMATTER) + "] [" + type + "] " + StringUtils.defaultString(message);
  }

  private String normalizeRuntimeLine(String line) {
    String normalized = StringUtils.defaultString(line).replace("\r\n", "\n").replace('\r', '\n');
    if (normalized.endsWith("\n")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private String joinLines(List<String> lines) {
    StringBuilder builder = new StringBuilder();
    lines.forEach(line -> builder.append(line).append("\n"));
    return builder.toString();
  }

  private String joinCachedPayloads(List<String> logs) {
    StringBuilder builder = new StringBuilder();
    logs.forEach(builder::append);
    return builder.toString();
  }

  private int appendStartIndex(List<String> previousSnapshot, List<String> currentSnapshot) {
    if (previousSnapshot == null || previousSnapshot.isEmpty()) {
      return 0;
    }
    int commonPrefixLength = commonPrefixLength(previousSnapshot, currentSnapshot);
    if (commonPrefixLength > 0) {
      return commonPrefixLength;
    }
    int overlap = suffixPrefixOverlap(previousSnapshot, currentSnapshot);
    return overlap > 0 ? overlap : 0;
  }

  private int commonPrefixLength(List<String> previousSnapshot, List<String> currentSnapshot) {
    int limit = Math.min(previousSnapshot.size(), currentSnapshot.size());
    int index = 0;
    while (index < limit && previousSnapshot.get(index).equals(currentSnapshot.get(index))) {
      index++;
    }
    return index;
  }

  private int suffixPrefixOverlap(List<String> previousSnapshot, List<String> currentSnapshot) {
    int limit = Math.min(previousSnapshot.size(), currentSnapshot.size());
    for (int size = limit; size > 0; size--) {
      List<String> previousSuffix = previousSnapshot.subList(previousSnapshot.size() - size, previousSnapshot.size());
      List<String> currentPrefix = currentSnapshot.subList(0, size);
      if (previousSuffix.equals(currentPrefix)) {
        return size;
      }
    }
    return 0;
  }
}
