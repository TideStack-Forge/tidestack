package com.ouroboros.devhelper.dev.metadata.provider;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.dev.event.CreateAppEvent;
import com.ouroboros.dev.event.DeleteAppEvent;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshotProvider;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.metadata.HelperRuntimeActivatedEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeRetiredEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeTarget;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperRetirementFence;
import com.ouroboros.devhelper.metadata.MetadataRefreshRequestedEvent;
import com.ouroboros.devhelper.metadata.MetadataRefreshSnapshot;
import com.ouroboros.devhelper.metadata.MetadataSnapshotPublishedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotRejectedEvent;
import com.ouroboros.util.Json;

@Component
@Order(100)
public class HelperMetadataProvider implements ApplicationMetadataSnapshotProvider {
  private static final Logger logger = LoggerFactory.getLogger(HelperMetadataProvider.class);
  private final static String METADATA_CACHE_NAME_TPL = "ouroboros-dev:%s:metadata";
  private static final String METADATA_SNAPSHOT_CACHE_KEY = "snapshot";
  private final Object snapshotPublicationMonitor = new Object();
  private final Map<String, HelperIdentity> activeHelperIdentities = new HashMap<String, HelperIdentity>();
  private final Map<String, HelperRuntimeTarget> activeHelperTargets = new HashMap<String, HelperRuntimeTarget>();
  private final Map<String, HelperRetirementFence> helperRetirementFences =
      new HashMap<String, HelperRetirementFence>();
  private final Map<String, MetadataRefreshRequestedEvent> currentRefreshRequests =
      new HashMap<String, MetadataRefreshRequestedEvent>();
  private final Set<String> staleSnapshots = new HashSet<String>();
  private final Set<String> deletedApps = new HashSet<String>();
  private final ApplicationEventPublisher eventPublisher;

  public HelperMetadataProvider(
      DevHelperCommandService devHelperCommandService,
      ApplicationEventPublisher eventPublisher
  ) {
    this.eventPublisher = eventPublisher;
    devHelperCommandService.registerCommandResultListener(
        Commands.REFRESH_METADATA.getCommand(),
        this::refreshMetadataListener
    );
    devHelperCommandService.registerCommandResultListener(Commands.READ_METADATA.getCommand(), this::refreshMetadataListener);
    logger.info("HelperMetadataProvider initialized");
  }

  @EventListener(HelperRuntimeActivatedEvent.class)
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public void onHelperRuntimeActivated(HelperRuntimeActivatedEvent event) {
    var appName = event == null ? "" : normalizeAppName(event.getAppName());
    var identity = event == null ? null : event.getHelperIdentity();
    var runtimeTarget = event == null ? null : event.getRuntimeTarget();
    if (appName.isEmpty()
        || identity == null
        || normalizeHelperInstanceId(identity.getInstanceId()).isEmpty()
        || identity.getStartedAtEpochMillis() <= 0L
        || normalizeAppIncarnationId(identity.getAppIncarnationId()).isEmpty()
        || !isValidRuntimeTarget(runtimeTarget)) {
      logger.warn("Ignoring invalid helper activation event for app: {}", appName);
      return;
    }
    var candidate = new HelperIdentity(
        identity.getInstanceId().trim(),
        identity.getStartedAtEpochMillis(),
        identity.getAppIncarnationId().trim()
    );
    synchronized (snapshotPublicationMonitor) {
      if (deletedApps.contains(appName)) {
        return;
      }
      var current = activeHelperIdentities.get(appName);
      if (!canActivateHelper(appName, candidate, current)) {
        logger.info("Ignoring stale helper activation event for app: {}", appName);
        return;
      }
      if (current != null) {
        retireHelper(appName, current);
      }
      currentRefreshRequests.remove(appName);
      activeHelperIdentities.put(appName, candidate);
      activeHelperTargets.put(appName, runtimeTarget);
    }
  }

  @EventListener(MetadataRefreshRequestedEvent.class)
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public void onMetadataRefreshRequested(MetadataRefreshRequestedEvent event) {
    var appName = event == null ? "" : normalizeAppName(event.getAppName());
    var identity = event == null ? null : event.getHelperIdentity();
    var operationId = event == null ? "" : normalizeRefreshOperationId(event.getRefreshOperationId());
    var runtimeTarget = event == null ? null : event.getRuntimeTarget();
    if (appName.isEmpty()
        || identity == null
        || normalizeHelperInstanceId(identity.getInstanceId()).isEmpty()
        || identity.getStartedAtEpochMillis() <= 0L
        || normalizeAppIncarnationId(identity.getAppIncarnationId()).isEmpty()
        || operationId.isEmpty()
        || !isValidRuntimeTarget(runtimeTarget)) {
      return;
    }
    synchronized (snapshotPublicationMonitor) {
      var activeIdentity = activeHelperIdentities.get(appName);
      if (deletedApps.contains(appName)
          || activeIdentity == null
          || !activeIdentity.matches(
              identity.getInstanceId(),
              identity.getStartedAtEpochMillis(),
              identity.getAppIncarnationId()
          )
          || !Objects.equals(activeHelperTargets.get(appName), runtimeTarget)) {
        return;
      }
      currentRefreshRequests.put(appName, event);
    }
  }

  @EventListener(MetadataSnapshotRejectedEvent.class)
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public void onMetadataSnapshotRejected(MetadataSnapshotRejectedEvent event) {
    var appName = event == null ? "" : normalizeAppName(event.getAppName());
    var helperInstanceId = event == null ? "" : normalizeHelperInstanceId(event.getHelperInstanceId());
    var appIncarnationId = event == null ? "" : normalizeAppIncarnationId(event.getAppIncarnationId());
    var refreshOperationId = event == null
        ? ""
        : normalizeRefreshOperationId(event.getRefreshOperationId());
    var runtimeTarget = event == null ? null : event.getRuntimeTarget();
    if (appName.isEmpty()
        || helperInstanceId.isEmpty()
        || appIncarnationId.isEmpty()
        || refreshOperationId.isEmpty()
        || event.getHelperStartedAtEpochMillis() <= 0L
        || !isValidRuntimeTarget(runtimeTarget)) {
      return;
    }
    synchronized (snapshotPublicationMonitor) {
      var current = currentRefreshRequests.get(appName);
      if (current == null
          || !Objects.equals(current.getRuntimeGeneration(), event.getRuntimeGeneration())
          || !Objects.equals(current.getRefreshOperationId(), refreshOperationId)
          || current.getHelperIdentity() == null
          || !current.getHelperIdentity().matches(
              helperInstanceId,
              event.getHelperStartedAtEpochMillis(),
              appIncarnationId
          )
          || !Objects.equals(current.getRuntimeTarget(), runtimeTarget)) {
        return;
      }
      currentRefreshRequests.remove(appName);
      staleSnapshots.add(appName);
    }
  }

  private boolean canActivateHelper(String appName, HelperIdentity candidate, HelperIdentity current) {
    if (helperRetirementFences.getOrDefault(appName, HelperRetirementFence.empty()).rejects(candidate)) {
      return false;
    }
    if (current != null) {
      if (current.matches(
          candidate.getInstanceId(),
          candidate.getStartedAtEpochMillis(),
          candidate.getAppIncarnationId()
      )) {
        return false;
      }
      return MetadataRefreshProtocol.canActivateHelper(candidate, current);
    }
    return true;
  }

  // 刷新元数据
  private void refreshMetadataListener(CommandResult result) {
    if (result == null) {
      logger.warn("Ignoring null metadata refresh result");
      return;
    }
    logger.info("Refreshing metadata for app: {}, success={}", result.getAppName(), result.getSuccess());
    var candidate = extractSnapshot(result);
    if (candidate.isEmpty()) {
      if (result.getSuccess()) {
        publishSnapshotRejected(result, "元数据快照格式或内容不完整");
      } else {
        publishSnapshotRejected(
            result,
            result.getMessage() == null || result.getMessage().trim().isEmpty()
                ? "元数据刷新失败"
                : result.getMessage()
        );
      }
      return;
    }
    if (!publishSnapshot(candidate.get())) {
      publishSnapshotRejected(result, "元数据快照不属于当前活动刷新或版本已过期");
    }
  }

  private Optional<MetadataRefreshSnapshot> extractSnapshot(CommandResult result) {
    if (!result.getSuccess()) {
      logger.warn("Command result not successful for app: {}", result.getAppName());
      return Optional.empty();
    }
    var parsed = Json.tryToBean(MetadataRefreshSnapshot.class, Json.toJsonString(result.getData()));
    if (parsed.isFailure()) {
      logger.warn("Unable to decode metadata snapshot for app: {}", result.getAppName(), parsed.getCause());
      return Optional.empty();
    }
    var snapshot = parsed.get();
    if (snapshot == null) {
      logger.warn("Decoded null metadata snapshot for app: {}", result.getAppName());
      return Optional.empty();
    }
    if (!isValidSnapshot(result, snapshot)) {
      logger.warn("Rejecting incomplete metadata snapshot for app: {}", result.getAppName());
      return Optional.empty();
    }
    return Optional.of(snapshot);
  }

  private boolean isValidSnapshot(CommandResult result, MetadataRefreshSnapshot snapshot) {
    var resultAppName = normalizeAppName(result.getAppName());
    var snapshotAppName = normalizeAppName(snapshot.getAppName());
    if (resultAppName.isEmpty() || !resultAppName.equals(snapshotAppName)) {
      return false;
    }
    if (!Objects.equals(result.getRuntimeGeneration(), snapshot.getRuntimeGeneration())) {
      return false;
    }
    if (snapshot.getSchemaVersion() != MetadataRefreshProtocol.CURRENT_SCHEMA_VERSION
        || !Objects.equals(result.getHelperInstanceId(), snapshot.getHelperInstanceId())
        || !Objects.equals(
            result.getHelperStartedAtEpochMillis(),
            Long.valueOf(snapshot.getHelperStartedAtEpochMillis())
        )
        || !Objects.equals(result.getAppIncarnationId(), snapshot.getAppIncarnationId())
        || normalizeAppIncarnationId(snapshot.getAppIncarnationId()).isEmpty()
        || !Objects.equals(result.getRefreshOperationId(), snapshot.getRefreshOperationId())
        || normalizeRefreshOperationId(snapshot.getRefreshOperationId()).isEmpty()
        || normalizeHelperInstanceId(snapshot.getHelperInstanceId()).isEmpty()
        || snapshot.getHelperStartedAtEpochMillis() <= 0L) {
      return false;
    }
    if (snapshot.getRefreshRevision() <= 0 || snapshot.getCollectedAtEpochMillis() <= 0) {
      return false;
    }
    if (snapshot.getModules() == null) {
      return false;
    }
    return snapshot.getModules().entrySet().stream().allMatch(entry -> {
      var moduleName = entry.getKey();
      var metadataList = entry.getValue();
      return moduleName != null
          && moduleName.equals(normalizeModuleName(moduleName))
          && !moduleName.isEmpty()
          && metadataList != null
          && metadataList.stream().allMatch(metadata -> isValidMetadata(moduleName, metadata));
    });
  }

  private boolean isValidMetadata(String moduleName, Metadata metadata) {
    return metadata != null
        && !normalizeModuleName(metadata.getName()).isEmpty()
        && moduleName.equals(metadata.getType());
  }

  private boolean publishSnapshot(MetadataRefreshSnapshot candidate) {
    var appName = normalizeAppName(candidate.getAppName());
    MetadataRefreshSnapshot publishedSnapshot;
    HelperRuntimeTarget runtimeTarget;
    synchronized (snapshotPublicationMonitor) {
      var activeIdentity = activeHelperIdentities.get(appName);
      if (deletedApps.contains(appName)
          || activeIdentity == null
          || !activeIdentity.matches(
              candidate.getHelperInstanceId(),
              candidate.getHelperStartedAtEpochMillis(),
              candidate.getAppIncarnationId()
          )) {
        logger.info("Ignoring metadata snapshot from inactive helper for app: {}", appName);
        return false;
      }
      if (!matchesCurrentRefreshRequest(appName, candidate)) {
        logger.info("Ignoring metadata snapshot from superseded refresh operation for app: {}", appName);
        return false;
      }
      runtimeTarget = currentRefreshRequests.get(appName).getRuntimeTarget();
      var current = readSnapshot(appName);
      if (current.isPresent() && !isNewer(candidate, current.get())) {
        logger.info(
            "Ignoring stale metadata snapshot for app: {}, runtimeGeneration={}, refreshRevision={}",
            appName,
            candidate.getRuntimeGeneration(),
            candidate.getRefreshRevision()
        );
        return false;
      }
      publishedSnapshot = freezeSnapshot(candidate);
      CacheManager.getCacheManager().put(
          buildCacheName(appName),
          METADATA_SNAPSHOT_CACHE_KEY,
          publishedSnapshot
      );
      currentRefreshRequests.remove(appName);
      staleSnapshots.remove(appName);
    }
    var metadataCount = publishedSnapshot.getModules().values().stream().mapToInt(List::size).sum();
    logger.info(
        "Metadata snapshot published for app: {}, runtimeGeneration={}, refreshRevision={}, count={}",
        appName,
        candidate.getRuntimeGeneration(),
        candidate.getRefreshRevision(),
        metadataCount
    );
    eventPublisher.publishEvent(new MetadataSnapshotPublishedEvent(
        appName,
        candidate.getRuntimeGeneration(),
        candidate.getHelperInstanceId(),
        candidate.getHelperStartedAtEpochMillis(),
        candidate.getAppIncarnationId(),
        candidate.getRefreshOperationId(),
        candidate.getRefreshRevision(),
        runtimeTarget
    ));
    return true;
  }

  private void publishSnapshotRejected(CommandResult result, String reason) {
    var appName = normalizeAppName(result.getAppName());
    if (appName.isEmpty()) {
      return;
    }
    HelperRuntimeTarget runtimeTarget = clearCurrentRefreshRequest(result);
    if (runtimeTarget == null) {
      return;
    }
    eventPublisher.publishEvent(new MetadataSnapshotRejectedEvent(
        appName,
        result.getRuntimeGeneration(),
        result.getHelperInstanceId(),
        result.getHelperStartedAtEpochMillis() == null
            ? 0L
            : result.getHelperStartedAtEpochMillis().longValue(),
        result.getAppIncarnationId(),
        result.getRefreshOperationId(),
        reason,
        result.getFailureType(),
        runtimeTarget
    ));
  }

  private boolean matchesCurrentRefreshRequest(String appName, MetadataRefreshSnapshot snapshot) {
    var current = currentRefreshRequests.get(appName);
    return current != null
        && Objects.equals(current.getRuntimeGeneration(), snapshot.getRuntimeGeneration())
        && Objects.equals(current.getRefreshOperationId(), snapshot.getRefreshOperationId())
        && current.getHelperIdentity() != null
        && current.getHelperIdentity().matches(
            snapshot.getHelperInstanceId(),
            snapshot.getHelperStartedAtEpochMillis(),
            snapshot.getAppIncarnationId()
        );
  }

  private HelperRuntimeTarget clearCurrentRefreshRequest(CommandResult result) {
    var appName = result == null ? "" : normalizeAppName(result.getAppName());
    if (appName.isEmpty()) {
      return null;
    }
    synchronized (snapshotPublicationMonitor) {
      var current = currentRefreshRequests.get(appName);
      if (current != null
          && current.getHelperIdentity() != null
          && Objects.equals(current.getRuntimeGeneration(), result.getRuntimeGeneration())
          && Objects.equals(current.getRefreshOperationId(), result.getRefreshOperationId())
          && current.getHelperIdentity().matches(
              result.getHelperInstanceId(),
              result.getHelperStartedAtEpochMillis() == null
                  ? 0L
                  : result.getHelperStartedAtEpochMillis().longValue(),
              result.getAppIncarnationId()
          )) {
        currentRefreshRequests.remove(appName);
        staleSnapshots.add(appName);
        return current.getRuntimeTarget();
      }
      return null;
    }
  }

  private boolean isNewer(MetadataRefreshSnapshot candidate, MetadataRefreshSnapshot current) {
    if (!Objects.equals(candidate.getAppIncarnationId(), current.getAppIncarnationId())) {
      return true;
    }
    var candidateGeneration = candidate.getRuntimeGeneration();
    var currentGeneration = current.getRuntimeGeneration();
    if (candidateGeneration != null && currentGeneration != null) {
      var generationComparison = candidateGeneration.compareTo(currentGeneration);
      if (generationComparison != 0) {
        return generationComparison > 0;
      }
    } else if (candidateGeneration != null) {
      return true;
    } else if (currentGeneration != null) {
      return false;
    }
    if (!Objects.equals(candidate.getHelperInstanceId(), current.getHelperInstanceId())
        || candidate.getHelperStartedAtEpochMillis() != current.getHelperStartedAtEpochMillis()) {
      return true;
    }
    return candidate.getRefreshRevision() > current.getRefreshRevision();
  }

  private String buildCacheName(String appName) {
    return String.format(METADATA_CACHE_NAME_TPL, normalizeAppName(appName));
  }

  private String normalizeAppName(String appName) {
    return appName == null ? "" : appName.trim();
  }

  private String normalizeModuleName(String moduleName) {
    return moduleName == null ? "" : moduleName.trim();
  }

  private String normalizeHelperInstanceId(String helperInstanceId) {
    return helperInstanceId == null ? "" : helperInstanceId.trim();
  }

  private String normalizeAppIncarnationId(String appIncarnationId) {
    return appIncarnationId == null ? "" : appIncarnationId.trim();
  }

  private String normalizeRefreshOperationId(String refreshOperationId) {
    return refreshOperationId == null ? "" : refreshOperationId.trim();
  }

  private Optional<MetadataRefreshSnapshot> readSnapshot(String appName) {
    return CacheManager.getCacheManager()
        .get(buildCacheName(appName), METADATA_SNAPSHOT_CACHE_KEY)
        .filter(MetadataRefreshSnapshot.class::isInstance)
        .map(MetadataRefreshSnapshot.class::cast);
  }

  public Optional<MetadataRefreshSnapshot> getAppMetadataSnapshot(String appName) {
    synchronized (snapshotPublicationMonitor) {
      return readSnapshot(appName).map(this::copySnapshot);
    }
  }

  @Override
  public ApplicationMetadataSnapshot getApplicationMetadataSnapshot(String appName) {
    var normalizedAppName = normalizeAppName(appName);
    if (normalizedAppName.isEmpty()) {
      return ApplicationMetadataSnapshot.unavailable();
    }
    synchronized (snapshotPublicationMonitor) {
      var snapshot = readSnapshot(normalizedAppName);
      if (snapshot.isEmpty()) {
        return ApplicationMetadataSnapshot.unavailable();
      }
      var value = snapshot.get();
      var identity = new java.util.LinkedHashMap<String, Object>();
      if (value.getRuntimeGeneration() != null) identity.put("runtimeGeneration", value.getRuntimeGeneration());
      if (value.getHelperInstanceId() != null) identity.put("helperInstanceId", value.getHelperInstanceId());
      if (value.getAppIncarnationId() != null) identity.put("appIncarnationId", value.getAppIncarnationId());
      identity.put("refreshRevision", value.getRefreshRevision());
      if (isCurrentSnapshot(normalizedAppName, value)) {
        return ApplicationMetadataSnapshot.currentStable(value.getModules()).withRuntimeIdentity(identity);
      }
      return ApplicationMetadataSnapshot.staleStable(value.getModules()).withRuntimeIdentity(identity);
    }
  }

  private boolean isCurrentSnapshot(String appName, MetadataRefreshSnapshot snapshot) {
    var activeIdentity = activeHelperIdentities.get(appName);
    return activeIdentity != null
        && activeIdentity.matches(
            snapshot.getHelperInstanceId(),
            snapshot.getHelperStartedAtEpochMillis(),
            snapshot.getAppIncarnationId()
        )
        && !currentRefreshRequests.containsKey(appName)
        && !staleSnapshots.contains(appName);
  }

  private MetadataRefreshSnapshot copySnapshot(MetadataRefreshSnapshot snapshot) {
    return copySnapshot(snapshot, false);
  }

  private MetadataRefreshSnapshot freezeSnapshot(MetadataRefreshSnapshot snapshot) {
    return copySnapshot(snapshot, true);
  }

  private MetadataRefreshSnapshot copySnapshot(MetadataRefreshSnapshot snapshot, boolean frozen) {
    var copiedModules = snapshot.getModules().entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> {
              var copiedMetadata = entry.getValue().stream()
                  .map(metadata -> copyMetadata(metadata, frozen))
                  .collect(Collectors.toList());
              return frozen ? Collections.unmodifiableList(copiedMetadata) : copiedMetadata;
            },
            (left, right) -> right,
            java.util.LinkedHashMap::new
        ));
    return new MetadataRefreshSnapshot()
        .setSchemaVersion(snapshot.getSchemaVersion())
        .setAppName(snapshot.getAppName())
        .setRuntimeGeneration(snapshot.getRuntimeGeneration())
        .setHelperInstanceId(snapshot.getHelperInstanceId())
        .setHelperStartedAtEpochMillis(snapshot.getHelperStartedAtEpochMillis())
        .setAppIncarnationId(snapshot.getAppIncarnationId())
        .setRefreshOperationId(snapshot.getRefreshOperationId())
        .setRefreshRevision(snapshot.getRefreshRevision())
        .setCollectedAtEpochMillis(snapshot.getCollectedAtEpochMillis())
        .setModules(frozen ? Collections.unmodifiableMap(copiedModules) : copiedModules);
  }

  private Metadata copyMetadata(Metadata metadata, boolean frozen) {
    var copy = new Metadata();
    copy.setName(metadata.getName());
    copy.setType(metadata.getType());
    copy.setLabel(metadata.getLabel());
    copy.setSourceClassName(metadata.getSourceClassName());
    copy.setSourceType(metadata.getSourceType());
    copy.setPriority(metadata.getPriority());
    if (metadata.getDetails() != null) {
      var copiedDetails = Json.toMap(Json.toJsonString(metadata.getDetails()));
      copy.setDetails(frozen ? Collections.unmodifiableMap(copiedDetails) : copiedDetails);
    }
    return copy;
  }

  @Override
  public List<Metadata> getAppAllMetadataByModule(String appName, String moduleName) {
    var normalizedModuleName = normalizeModuleName(moduleName);
    if (normalizeAppName(appName).isEmpty() || normalizedModuleName.isEmpty()) {
      return Collections.emptyList();
    }
    return getAppMetadataSnapshot(appName)
        .map(snapshot -> snapshot.getModules().getOrDefault(normalizedModuleName, Collections.emptyList()))
        .orElse(Collections.emptyList());
  }

  @EventListener(DeleteAppEvent.class)
  public void onDeleteApp(DeleteAppEvent event) {
    var appName = event == null ? "" : normalizeAppName(event.getAppName());
    if (appName.isEmpty()) {
      return;
    }
    synchronized (snapshotPublicationMonitor) {
      deletedApps.add(appName);
      retireActiveHelper(appName);
      currentRefreshRequests.remove(appName);
      staleSnapshots.remove(appName);
      CacheManager.getCacheManager().clear(buildCacheName(appName));
    }
  }

  @EventListener(CreateAppEvent.class)
  public void onCreateApp(CreateAppEvent event) {
    var appName = event == null ? "" : normalizeAppName(event.getAppName());
    if (appName.isEmpty()) {
      return;
    }
    synchronized (snapshotPublicationMonitor) {
      deletedApps.remove(appName);
      retireActiveHelper(appName);
      currentRefreshRequests.remove(appName);
      staleSnapshots.remove(appName);
      CacheManager.getCacheManager().clear(buildCacheName(appName));
    }
  }

  @EventListener(HelperRuntimeRetiredEvent.class)
  public void onHelperRuntimeRetired(HelperRuntimeRetiredEvent event) {
    var appName = event == null ? "" : normalizeAppName(event.getAppName());
    var helperInstanceId = event == null ? "" : normalizeHelperInstanceId(event.getHelperInstanceId());
    var appIncarnationId = event == null ? "" : normalizeAppIncarnationId(event.getAppIncarnationId());
    var runtimeTarget = event == null ? null : event.getRuntimeTarget();
    if (appName.isEmpty()
        || helperInstanceId.isEmpty()
        || appIncarnationId.isEmpty()
        || event.getHelperStartedAtEpochMillis() <= 0L
        || !isValidRuntimeTarget(runtimeTarget)) {
      return;
    }
    synchronized (snapshotPublicationMonitor) {
      var retiredIdentity = new HelperIdentity(
          helperInstanceId,
          event.getHelperStartedAtEpochMillis(),
          appIncarnationId
      );
      retireHelper(appName, retiredIdentity);
      var activeIdentity = activeHelperIdentities.get(appName);
      if (activeIdentity == null
          || !activeIdentity.matches(
              helperInstanceId,
              event.getHelperStartedAtEpochMillis(),
              event.getAppIncarnationId()
          )
          || !Objects.equals(activeHelperTargets.get(appName), runtimeTarget)) {
        return;
      }
      activeHelperIdentities.remove(appName);
      activeHelperTargets.remove(appName);
      currentRefreshRequests.remove(appName);
    }
  }

  private void retireActiveHelper(String appName) {
    var active = activeHelperIdentities.remove(appName);
    activeHelperTargets.remove(appName);
    currentRefreshRequests.remove(appName);
    if (active == null) {
      return;
    }
    retireHelper(appName, active);
  }

  private boolean isValidRuntimeTarget(HelperRuntimeTarget runtimeTarget) {
    return runtimeTarget != null
        && !normalizeRuntimeTargetPart(runtimeTarget.getBackendType()).isEmpty()
        && !normalizeRuntimeTargetPart(runtimeTarget.getProfile()).isEmpty()
        && !normalizeRuntimeTargetPart(runtimeTarget.getRuntimeId()).isEmpty();
  }

  private String normalizeRuntimeTargetPart(String value) {
    return value == null ? "" : value.trim();
  }

  private void retireHelper(String appName, HelperIdentity identity) {
    helperRetirementFences.compute(
        appName,
        (ignored, current) -> (current == null ? HelperRetirementFence.empty() : current).retire(identity)
    );
  }
}
