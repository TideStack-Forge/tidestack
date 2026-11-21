package com.ouroboros.devhelper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataRefreshSnapshot;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshEvent;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;

class RefreshMetadataCommandSnapshotTest {

  @Test
  void shouldPreserveEmptyModulesAndAssignMonotonicRevision() {
    var refreshEvent = new AtomicReference<OuroborosContextRefreshEvent>();
    ApplicationEventPublisher publisher = event -> refreshEvent.set((OuroborosContextRefreshEvent) event);
    var command = new RefreshMetadataCommand(
        publisher,
        () -> Stream.of(
            collector("data-model", List.of(metadata("data-model", "Customer"))),
            collector("logic-flow", List.of())
        ),
        new HelperIdentity("helper-a", 1000L, "app-incarnation")
    );

    var firstRequest = request("crm", 7L, "first");
    var firstResult = command.execute(firstRequest);
    command.onContextRefresh(new OuroborosContextRefreshedEvent(refreshEvent.get()));

    var firstSnapshot = (MetadataRefreshSnapshot) firstResult.join().getData();
    assertEquals("crm", firstSnapshot.getAppName());
    assertEquals(7L, firstSnapshot.getRuntimeGeneration());
    assertEquals(MetadataRefreshProtocol.CURRENT_SCHEMA_VERSION, firstSnapshot.getSchemaVersion());
    assertEquals("helper-a", firstSnapshot.getHelperInstanceId());
    assertEquals(1000L, firstSnapshot.getHelperStartedAtEpochMillis());
    assertEquals("app-incarnation", firstSnapshot.getAppIncarnationId());
    assertEquals("first", firstSnapshot.getRefreshOperationId());
    assertEquals(1L, firstSnapshot.getRefreshRevision());
    assertEquals(1, firstSnapshot.getModules().get("data-model").size());
    assertTrue(firstSnapshot.getModules().containsKey("logic-flow"));
    assertTrue(firstSnapshot.getModules().get("logic-flow").isEmpty());

    var secondRequest = request("crm", 7L, "second");
    var secondResult = command.execute(secondRequest);
    command.onContextRefresh(new OuroborosContextRefreshedEvent(refreshEvent.get()));

    var secondSnapshot = (MetadataRefreshSnapshot) secondResult.join().getData();
    assertEquals(2L, secondSnapshot.getRefreshRevision());
  }

  @Test
  void shouldPublishAnAuthoritativeEmptySnapshotWhenNoCollectorsExist() {
    var refreshEvent = new AtomicReference<OuroborosContextRefreshEvent>();
    ApplicationEventPublisher publisher = event -> refreshEvent.set((OuroborosContextRefreshEvent) event);
    var command = new RefreshMetadataCommand(
        publisher,
        Stream::empty,
        new HelperIdentity("helper-empty", 2000L, "app-incarnation")
    );
    var request = request("empty-app", 1L, "empty", "helper-empty");

    var result = command.execute(request);
    command.onContextRefresh(new OuroborosContextRefreshedEvent(refreshEvent.get()));

    var snapshot = (MetadataRefreshSnapshot) result.join().getData();
    assertTrue(snapshot.getModules().isEmpty());
    assertEquals(1L, snapshot.getRefreshRevision());
  }

  @Test
  void shouldRejectRequestsForAnotherHelperInstance() {
    var refreshEvent = new AtomicReference<OuroborosContextRefreshEvent>();
    ApplicationEventPublisher publisher = event -> refreshEvent.set((OuroborosContextRefreshEvent) event);
    var command = new RefreshMetadataCommand(
        publisher,
        Stream::empty,
        new HelperIdentity("helper-current", 3000L, "app-incarnation")
    );

    var result = command.execute(request("crm", 1L, "mismatch", "helper-old")).join();

    assertFalse(result.getSuccess());
    assertEquals(null, refreshEvent.get());
  }

  @Test
  void shouldRejectRequestsForAnotherHelperStartIdentity() {
    var refreshEvent = new AtomicReference<OuroborosContextRefreshEvent>();
    ApplicationEventPublisher publisher = event -> refreshEvent.set((OuroborosContextRefreshEvent) event);
    var command = new RefreshMetadataCommand(
        publisher,
        Stream::empty,
        new HelperIdentity("helper-current", 3000L, "app-incarnation")
    );

    var result = command.execute(request(
        "crm",
        1L,
        "mismatch-start",
        "helper-current",
        2000L
    )).join();

    assertFalse(result.getSuccess());
    assertEquals(null, refreshEvent.get());
  }

  @Test
  void publisherExceptionShouldCompleteTheCommandWithFailure() {
    var command = new RefreshMetadataCommand(
        event -> {
          throw new IllegalStateException("publisher failed");
        },
        Stream::empty,
        new HelperIdentity("helper-a", 1000L, "app-incarnation")
    );

    var result = command.execute(request("crm", 1L, "publisher-failure")).join();

    assertFalse(result.getSuccess());
    assertEquals("publisher failed", result.getMessage());
  }

  @Test
  void collectorExceptionShouldCompleteTheCommandWithFailure() {
    var refreshEvent = new AtomicReference<OuroborosContextRefreshEvent>();
    var command = new RefreshMetadataCommand(
        event -> refreshEvent.set((OuroborosContextRefreshEvent) event),
        () -> {
          throw new IllegalStateException("collector failed");
        },
        new HelperIdentity("helper-a", 1000L, "app-incarnation")
    );
    var future = command.execute(request("crm", 1L, "collector-failure"));

    command.onContextRefresh(new OuroborosContextRefreshedEvent(refreshEvent.get()));

    assertFalse(future.join().getSuccess());
    assertEquals("collector failed", future.join().getMessage());
  }

  private static CommandRequest request(String appName, long runtimeGeneration, String sequenceId) {
    return request(appName, runtimeGeneration, sequenceId, "helper-a");
  }

  private static CommandRequest request(
      String appName,
      long runtimeGeneration,
      String sequenceId,
      String helperInstanceId
  ) {
    long startedAtEpochMillis = "helper-empty".equals(helperInstanceId) ? 2000L
        : "helper-current".equals(helperInstanceId) ? 3000L : 1000L;
    return request(appName, runtimeGeneration, sequenceId, helperInstanceId, startedAtEpochMillis);
  }

  private static CommandRequest request(
      String appName,
      long runtimeGeneration,
      String sequenceId,
      String helperInstanceId,
      long helperStartedAtEpochMillis
  ) {
    return new CommandRequest()
        .setAppName(appName)
        .setRuntimeGeneration(runtimeGeneration)
        .setHelperInstanceId(helperInstanceId)
        .setHelperStartedAtEpochMillis(Long.valueOf(helperStartedAtEpochMillis))
        .setAppIncarnationId("app-incarnation")
        .setRefreshOperationId(sequenceId)
        .setParams(Map.of(
            MetadataRefreshProtocol.SCHEMA_VERSION_KEY,
            Integer.valueOf(MetadataRefreshProtocol.CURRENT_SCHEMA_VERSION),
            MetadataRefreshProtocol.APP_INCARNATION_ID_KEY,
            "app-incarnation"
        ))
        .setSequenceId(sequenceId);
  }

  private static MetadataCollector collector(String type, List<Metadata> metadata) {
    return new MetadataCollector() {
      @Override
      public String getMetadataType() {
        return type;
      }

      @Override
      public List<Metadata> getAllMetadata() {
        return metadata;
      }
    };
  }

  private static Metadata metadata(String type, String name) {
    var metadata = new Metadata();
    metadata.setType(type);
    metadata.setName(name);
    return metadata;
  }
}
