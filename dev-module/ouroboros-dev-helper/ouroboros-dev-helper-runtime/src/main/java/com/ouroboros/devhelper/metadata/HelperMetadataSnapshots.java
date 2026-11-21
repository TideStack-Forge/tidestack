package com.ouroboros.devhelper.metadata;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.util.HelperRuntimeIdentity;
import com.ouroboros.util.SPI;
import org.springframework.stereotype.Component;

/** The same snapshot and revision sequence for refresh and read-only editor requests. */
@Component
public final class HelperMetadataSnapshots {
  private final Supplier<Stream<MetadataCollector>> metadataCollectorStream;
  private final HelperIdentity helperIdentity;
  private final AtomicLong revision = new AtomicLong();
  public HelperMetadataSnapshots() {
    this(() -> SPI.getSortedServiceStream(MetadataCollector.class), HelperRuntimeIdentity.current());
  }
  public HelperMetadataSnapshots(Supplier<Stream<MetadataCollector>> collectors, HelperIdentity identity) {
    this.metadataCollectorStream = collectors;
    this.helperIdentity = identity;
  }
  public boolean accepts(CommandRequest request) {
    return MetadataRefreshProtocol.isCurrentSchema(request.getParams())
        && helperIdentity.matches(request.getHelperInstanceId(),
            Objects.requireNonNullElse(request.getHelperStartedAtEpochMillis(), 0L), request.getAppIncarnationId())
        && request.getRefreshOperationId() != null && !request.getRefreshOperationId().isBlank();
  }
  private final AtomicLong epoch = new AtomicLong(1);
  public long epoch() { return epoch.get(); }
  @org.springframework.context.event.EventListener
  public void refreshed(com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent event) { epoch.incrementAndGet(); }
  public synchronized MetadataRefreshSnapshot collect(CommandRequest request) {
      var modules = metadataCollectorStream.get()
          .collect(Collectors.groupingBy(
              MetadataCollector::getMetadataType,
              LinkedHashMap::new,
              Collectors.flatMapping(collector -> collector.getAllMetadata().stream(), Collectors.toList())
          ));
      var snapshot = new MetadataRefreshSnapshot()
          .setSchemaVersion(MetadataRefreshProtocol.CURRENT_SCHEMA_VERSION)
          .setAppName(request.getAppName())
          .setRuntimeGeneration(request.getRuntimeGeneration())
          .setHelperInstanceId(helperIdentity.getInstanceId())
          .setHelperStartedAtEpochMillis(helperIdentity.getStartedAtEpochMillis())
          .setAppIncarnationId(helperIdentity.getAppIncarnationId())
          .setRefreshOperationId(request.getRefreshOperationId())
          .setRefreshRevision(revision.incrementAndGet())
          .setCollectedAtEpochMillis(System.currentTimeMillis())
          .setModules(modules);

    return snapshot;
  }
}
