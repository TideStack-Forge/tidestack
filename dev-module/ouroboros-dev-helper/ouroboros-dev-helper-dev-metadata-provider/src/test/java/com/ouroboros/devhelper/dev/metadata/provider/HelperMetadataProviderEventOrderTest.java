package com.ouroboros.devhelper.dev.metadata.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.metadata.HelperRuntimeActivatedEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeTarget;
import com.ouroboros.devhelper.metadata.MetadataRefreshRequestedEvent;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataRefreshSnapshot;
import com.ouroboros.devhelper.metadata.MetadataSnapshotRejectedEvent;

class HelperMetadataProviderEventOrderTest {

  @AfterEach
  void tearDown() {
    CacheManager.getCacheManager().clear();
  }

  @Test
  void providerShouldActivateBeforeAnImmediateRefreshConsumerRuns() {
    AtomicReference<Consumer<CommandResult>> refreshListener = new AtomicReference<Consumer<CommandResult>>();
    DevHelperCommandService commandService = mock(DevHelperCommandService.class);
    doAnswer(invocation -> {
      if (Commands.REFRESH_METADATA.getCommand().equals(invocation.getArgument(0))) {
        refreshListener.set(invocation.getArgument(1));
      }
      return null;
    }).when(commandService).registerCommandResultListener(any(String.class), any());

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      HelperMetadataProvider provider = new HelperMetadataProvider(commandService, context);
      context.registerBean(HelperMetadataProvider.class, () -> provider);
      context.registerBean(
          KubernetesLikeImmediateRefreshConsumer.class,
          () -> new KubernetesLikeImmediateRefreshConsumer(refreshListener, context)
      );
      context.refresh();

      context.publishEvent(new HelperRuntimeActivatedEvent(
          "crm",
          new HelperIdentity("helper-a", 1000L, "incarnation-crm"),
          runtimeTarget()
      ));

      assertTrue(provider.getAppMetadataSnapshot("crm").isPresent());
    }
  }

  @Test
  void providerShouldClearRejectedRequestBeforeALateSuccessArrives() {
    AtomicReference<Consumer<CommandResult>> refreshListener = new AtomicReference<Consumer<CommandResult>>();
    DevHelperCommandService commandService = mock(DevHelperCommandService.class);
    doAnswer(invocation -> {
      if (Commands.REFRESH_METADATA.getCommand().equals(invocation.getArgument(0))) {
        refreshListener.set(invocation.getArgument(1));
      }
      return null;
    }).when(commandService).registerCommandResultListener(any(String.class), any());

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      HelperMetadataProvider provider = new HelperMetadataProvider(commandService, context);
      context.registerBean(HelperMetadataProvider.class, () -> provider);
      context.registerBean(
          KubernetesLikeRejectedRefreshConsumer.class,
          () -> new KubernetesLikeRejectedRefreshConsumer(refreshListener, context)
      );
      context.refresh();

      context.publishEvent(new HelperRuntimeActivatedEvent(
          "crm",
          new HelperIdentity("helper-a", 1000L, "incarnation-crm"),
          runtimeTarget()
      ));

      assertFalse(provider.getAppMetadataSnapshot("crm").isPresent());
    }
  }

  private static HelperRuntimeTarget runtimeTarget() {
    return new HelperRuntimeTarget("KUBERNETES", "dev-helper", "devrt-dev-helper-crm");
  }

  private static final class KubernetesLikeImmediateRefreshConsumer {
    private final AtomicReference<Consumer<CommandResult>> refreshListener;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private KubernetesLikeImmediateRefreshConsumer(
        AtomicReference<Consumer<CommandResult>> refreshListener,
        org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
      this.refreshListener = refreshListener;
      this.eventPublisher = eventPublisher;
    }

    @EventListener(HelperRuntimeActivatedEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onHelperRuntimeActivated(HelperRuntimeActivatedEvent event) {
      eventPublisher.publishEvent(new MetadataRefreshRequestedEvent(
          event.getAppName(),
          Long.valueOf(1L),
          event.getHelperIdentity(),
          "refresh-1",
          event.getRuntimeTarget()
      ));
      MetadataRefreshSnapshot snapshot = new MetadataRefreshSnapshot()
          .setSchemaVersion(com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.CURRENT_SCHEMA_VERSION)
          .setAppName(event.getAppName())
          .setRuntimeGeneration(Long.valueOf(1L))
          .setHelperInstanceId(event.getHelperIdentity().getInstanceId())
          .setHelperStartedAtEpochMillis(event.getHelperIdentity().getStartedAtEpochMillis())
          .setAppIncarnationId(event.getHelperIdentity().getAppIncarnationId())
          .setRefreshOperationId("refresh-1")
          .setRefreshRevision(1L)
          .setCollectedAtEpochMillis(System.currentTimeMillis())
          .setModules(Map.of());
      refreshListener.get().accept(new CommandResult()
          .setAppName(event.getAppName())
          .setCommand(Commands.REFRESH_METADATA.getCommand())
          .setRuntimeGeneration(Long.valueOf(1L))
          .setHelperInstanceId(event.getHelperIdentity().getInstanceId())
          .setHelperStartedAtEpochMillis(Long.valueOf(event.getHelperIdentity().getStartedAtEpochMillis()))
          .setAppIncarnationId(event.getHelperIdentity().getAppIncarnationId())
          .setRefreshOperationId("refresh-1")
          .setSuccess(true)
          .setData(snapshot));
    }

  }

  private static final class KubernetesLikeRejectedRefreshConsumer {
    private final AtomicReference<Consumer<CommandResult>> refreshListener;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private KubernetesLikeRejectedRefreshConsumer(
        AtomicReference<Consumer<CommandResult>> refreshListener,
        org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
      this.refreshListener = refreshListener;
      this.eventPublisher = eventPublisher;
    }

    @EventListener(HelperRuntimeActivatedEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onHelperRuntimeActivated(HelperRuntimeActivatedEvent event) {
      eventPublisher.publishEvent(new MetadataRefreshRequestedEvent(
          event.getAppName(),
          Long.valueOf(1L),
          event.getHelperIdentity(),
          "refresh-1",
          event.getRuntimeTarget()
      ));
      eventPublisher.publishEvent(new MetadataSnapshotRejectedEvent(
          event.getAppName(),
          Long.valueOf(1L),
          event.getHelperIdentity().getInstanceId(),
          event.getHelperIdentity().getStartedAtEpochMillis(),
          event.getHelperIdentity().getAppIncarnationId(),
          "refresh-1",
          "MQ send failed",
          event.getRuntimeTarget()
      ));
      MetadataRefreshSnapshot snapshot = new MetadataRefreshSnapshot()
          .setSchemaVersion(com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.CURRENT_SCHEMA_VERSION)
          .setAppName(event.getAppName())
          .setRuntimeGeneration(Long.valueOf(1L))
          .setHelperInstanceId(event.getHelperIdentity().getInstanceId())
          .setHelperStartedAtEpochMillis(event.getHelperIdentity().getStartedAtEpochMillis())
          .setAppIncarnationId(event.getHelperIdentity().getAppIncarnationId())
          .setRefreshOperationId("refresh-1")
          .setRefreshRevision(1L)
          .setCollectedAtEpochMillis(System.currentTimeMillis())
          .setModules(Map.of());
      refreshListener.get().accept(new CommandResult()
          .setAppName(event.getAppName())
          .setCommand(Commands.REFRESH_METADATA.getCommand())
          .setRuntimeGeneration(Long.valueOf(1L))
          .setHelperInstanceId(event.getHelperIdentity().getInstanceId())
          .setHelperStartedAtEpochMillis(Long.valueOf(event.getHelperIdentity().getStartedAtEpochMillis()))
          .setAppIncarnationId(event.getHelperIdentity().getAppIncarnationId())
          .setRefreshOperationId("refresh-1")
          .setSuccess(true)
          .setData(snapshot));
    }
  }
}
