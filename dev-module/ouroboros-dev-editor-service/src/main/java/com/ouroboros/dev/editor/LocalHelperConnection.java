package com.ouroboros.dev.editor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.command.*;
import com.ouroboros.devhelper.metadata.*;
import com.ouroboros.devhelper.dev.metadata.provider.HelperMetadataProvider;

/** Publishes local snapshots through the same fenced provider used by the platform. */
public final class LocalHelperConnection {
  private final DevHelperCommandService commands;
  private final ApplicationEventPublisher events;
  private final HelperMetadataProvider metadata;
  private final String appName;
  private long checkedAt;
  private Object metadataEpoch;
  private MetadataRefreshProtocol.HelperIdentity identity;
  public LocalHelperConnection(DevHelperCommandService commands, ApplicationEventPublisher events,
      HelperMetadataProvider metadata, @Value("${ouroboros.editor.app-name}") String appName) {
    this.commands = commands; this.events = events; this.metadata = metadata; this.appName = appName;
  }
  public synchronized void ensureReady() {
    if (System.nanoTime() - checkedAt < TimeUnit.SECONDS.toNanos(5) && identity != null) return;
    var presence = execute(Commands.KNOCK_KNOCK.getCommand(), Map.of());
    var current = MetadataRefreshProtocol.helperIdentity(presence).orElseThrow(() -> new IllegalStateException("Helper snapshot protocol is incompatible"));
    var epoch = ((Map<?, ?>) presence.getData()).get("metadataEpoch");
    if (identity != null && identity.matches(current.getInstanceId(), current.getStartedAtEpochMillis(), current.getAppIncarnationId())
        && java.util.Objects.equals(metadataEpoch, epoch)
        && metadata.getApplicationMetadataSnapshot(appName).getStatus() == com.ouroboros.dev.metadata.MetadataSnapshotStatus.CURRENT) {
      checkedAt = System.nanoTime(); return;
    }
    var target = new HelperRuntimeTarget("LOCAL", "local", current.getInstanceId());
    if (identity == null || !identity.matches(current.getInstanceId(), current.getStartedAtEpochMillis(), current.getAppIncarnationId())) {
      events.publishEvent(new HelperRuntimeActivatedEvent(appName, current, target));
      identity = current;
    }
    var operation = UUID.randomUUID().toString();
    events.publishEvent(new MetadataRefreshRequestedEvent(appName, null, current, operation, target));
    execute(Commands.READ_METADATA.getCommand(), MetadataRefreshProtocol.refreshParameters(null, current, operation));
    if (metadata.getApplicationMetadataSnapshot(appName).getStatus() != com.ouroboros.dev.metadata.MetadataSnapshotStatus.CURRENT) throw new IllegalStateException("Helper metadata snapshot was rejected");
    metadataEpoch = epoch;
    checkedAt = System.nanoTime();
  }
  public CommandResult execute(String command, Map<String, Object> parameters) {
    try {
      var fenced = new java.util.LinkedHashMap<String, Object>(parameters);
      if (identity != null && !Commands.KNOCK_KNOCK.getCommand().equals(command)
          && !Commands.READ_METADATA.getCommand().equals(command)) MetadataRefreshProtocol.addHelperIdentity(fenced, identity);
      var result = commands.exec(appName, command, fenced, 30000).get(31, TimeUnit.SECONDS);
      if (!Boolean.TRUE.equals(result.getSuccess())) throw new IllegalStateException(result.getMessage());
      return result;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt(); throw new IllegalStateException("Helper request interrupted", error);
    } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) {
      throw new IllegalStateException("Local helper is unavailable", error);
    }
  }
}
