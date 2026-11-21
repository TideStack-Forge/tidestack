package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.projectgeneration.catalog.FileProjectTemplateCatalogLkgStore;
import com.ouroboros.dev.projectgeneration.catalog.InMemoryProjectTemplateCatalogReplayStateStore;
import com.ouroboros.dev.projectgeneration.catalog.LastKnownGoodCatalog;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogFetchResult;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogRemoteException;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogSnapshot;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogSnapshotManager;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogUnavailableException;
import com.ouroboros.dev.projectgeneration.catalog.SignedProjectTemplateCatalogVerifier;

class ProjectTemplateCatalogSnapshotManagerTest {

  @TempDir
  private Path tempDir;

  @Test
  void shouldAcceptRemoteSnapshotIntoSharedReplayStateAndLocalLkg() {
    var fixture = new SignedCatalogFixture();
    var store = new InMemoryProjectTemplateCatalogReplayStateStore();
    var manager = new ProjectTemplateCatalogSnapshotManager(
        "default",
        fixture.verifier(),
        store,
        () -> fixture.fetchResult(50),
        fixture.clock
    );

    ProjectTemplateCatalogSnapshot snapshot = manager.currentOrRefresh();

    assertEquals(50, snapshot.sequence());
    assertEquals(50, store.current("default").orElseThrow().sequence());
  }

  @Test
  void shouldUseOnlyLkgMatchingSharedReplayPairWhenRemoteUnavailable() {
    var fixture = new SignedCatalogFixture();
    var store = new InMemoryProjectTemplateCatalogReplayStateStore();
    ProjectTemplateCatalogSnapshot snapshot = fixture.snapshot(60);
    store.accept("default", snapshot.sequence(), snapshot.digest());
    var manager = new ProjectTemplateCatalogSnapshotManager(
        "default",
        fixture.verifier(),
        store,
        () -> {
          throw new ProjectTemplateCatalogRemoteException("offline");
        },
        fixture.clock
    );
    manager.installLastKnownGood(new LastKnownGoodCatalog(
        snapshot.catalogBytes(),
        snapshot.signatureEnvelopeBytes(),
        fixture.now.minusSeconds(30),
        snapshot.expiresAt(),
        snapshot.sequence(),
        snapshot.digest(),
        snapshot.signatureKeyId()
    ));

    ProjectTemplateCatalogSnapshot loaded = manager.currentOrRefresh();

    assertEquals(60, loaded.sequence());
  }

  @Test
  void shouldNotServeLocalSnapshotAfterSharedPairAdvancedElsewhere() {
    var fixture = new SignedCatalogFixture();
    var store = new InMemoryProjectTemplateCatalogReplayStateStore();
    AtomicInteger calls = new AtomicInteger();
    var manager = new ProjectTemplateCatalogSnapshotManager(
        "default",
        fixture.verifier(),
        store,
        () -> {
          if (calls.incrementAndGet() == 1) {
            return fixture.fetchResult(70);
          }
          throw new ProjectTemplateCatalogRemoteException("offline");
        },
        fixture.clock
    );
    manager.currentOrRefresh();
    ProjectTemplateCatalogSnapshot advanced = fixture.snapshot(71);
    store.accept("default", advanced.sequence(), advanced.digest());

    assertThrows(ProjectTemplateCatalogUnavailableException.class, manager::currentOrRefresh);
  }

  @Test
  void shouldSingleFlightConcurrentRefreshesPerNode() throws Exception {
    var fixture = new SignedCatalogFixture();
    var store = new InMemoryProjectTemplateCatalogReplayStateStore();
    AtomicInteger calls = new AtomicInteger();
    var manager = new ProjectTemplateCatalogSnapshotManager(
        "default",
        fixture.verifier(),
        store,
        () -> {
          calls.incrementAndGet();
          return fixture.fetchResult(80);
        },
        fixture.clock
    );

    var executor = Executors.newFixedThreadPool(6);
    try {
      List<Callable<ProjectTemplateCatalogSnapshot>> tasks = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
        tasks.add(manager::currentOrRefresh);
      }
      var results = executor.invokeAll(tasks);
      for (var result : results) {
        assertEquals(80, result.get().sequence());
      }
    } finally {
      executor.shutdownNow();
    }

    assertEquals(1, calls.get());
  }

  @Test
  void shouldPersistRemoteSuccessToDiskLkg() {
    var fixture = new SignedCatalogFixture();
    var store = new InMemoryProjectTemplateCatalogReplayStateStore();
    var lkgStore = new FileProjectTemplateCatalogLkgStore(tempDir.resolve("catalog.lkg"));
    var manager = new ProjectTemplateCatalogSnapshotManager(
        "default",
        fixture.verifier(),
        store,
        () -> fixture.fetchResult(81),
        lkgStore,
        fixture.clock
    );

    ProjectTemplateCatalogSnapshot snapshot = manager.currentOrRefresh();

    LastKnownGoodCatalog persisted = lkgStore.load().orElseThrow();
    assertEquals(snapshot.sequence(), persisted.sequence());
    assertEquals(snapshot.digest(), persisted.digest());
  }

  @Test
  void shouldLoadMatchingDiskLkgWhenRemoteUnavailable() {
    var fixture = new SignedCatalogFixture();
    var store = new InMemoryProjectTemplateCatalogReplayStateStore();
    var lkgStore = new FileProjectTemplateCatalogLkgStore(tempDir.resolve("catalog.lkg"));
    ProjectTemplateCatalogSnapshot snapshot = fixture.snapshot(82);
    store.accept("default", snapshot.sequence(), snapshot.digest());
    lkgStore.save(new LastKnownGoodCatalog(
        snapshot.catalogBytes(),
        snapshot.signatureEnvelopeBytes(),
        fixture.now.minusSeconds(30),
        snapshot.expiresAt(),
        snapshot.sequence(),
        snapshot.digest(),
        snapshot.signatureKeyId()
    ));
    var manager = new ProjectTemplateCatalogSnapshotManager(
        "default",
        fixture.verifier(),
        store,
        () -> {
          throw new ProjectTemplateCatalogRemoteException("offline");
        },
        lkgStore,
        fixture.clock
    );

    ProjectTemplateCatalogSnapshot loaded = manager.currentOrRefresh();

    assertEquals(82, loaded.sequence());
  }

  @Test
  void shouldRejectExpiredDiskLkgWhenRemoteUnavailable() {
    var fixture = new SignedCatalogFixture();
    var store = new InMemoryProjectTemplateCatalogReplayStateStore();
    var lkgStore = new FileProjectTemplateCatalogLkgStore(tempDir.resolve("catalog.lkg"));
    ProjectTemplateCatalogSnapshot snapshot = fixture.snapshot(83);
    store.accept("default", snapshot.sequence(), snapshot.digest());
    lkgStore.save(new LastKnownGoodCatalog(
        snapshot.catalogBytes(),
        snapshot.signatureEnvelopeBytes(),
        fixture.now.minusSeconds(60),
        fixture.now.minusSeconds(1),
        snapshot.sequence(),
        snapshot.digest(),
        snapshot.signatureKeyId()
    ));
    var manager = new ProjectTemplateCatalogSnapshotManager(
        "default",
        fixture.verifier(),
        store,
        () -> {
          throw new ProjectTemplateCatalogRemoteException("offline");
        },
        lkgStore,
        fixture.clock
    );

    assertThrows(ProjectTemplateCatalogUnavailableException.class, manager::currentOrRefresh);
  }

  private static final class SignedCatalogFixture {

    private final java.security.KeyPair keyPair = CatalogTestSupport.ed25519KeyPair();
    private final Instant now = Instant.parse("2026-07-30T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    ProjectTemplateCatalogFetchResult fetchResult(long sequence) {
      ProjectTemplateCatalogSnapshot snapshot = snapshot(sequence);
      return new ProjectTemplateCatalogFetchResult(snapshot.catalogBytes(), snapshot.signatureEnvelopeBytes());
    }

    ProjectTemplateCatalogSnapshot snapshot(long sequence) {
      return snapshot(sequence, now.minusSeconds(60), now.plusSeconds(3600));
    }

    ProjectTemplateCatalogSnapshot snapshot(long sequence, Instant issuedAt, Instant expiresAt) {
      byte[] catalog = CatalogTestSupport.canonicalCatalog(
          sequence,
          "cat_" + sequence + "_abcd",
          issuedAt,
          expiresAt
      );
      byte[] envelope = CatalogTestSupport.utf8(CatalogTestSupport.signatureEnvelope("catalog-key-1", keyPair, catalog));
      return verifier().verify(catalog, envelope);
    }

    SignedProjectTemplateCatalogVerifier verifier() {
      return new SignedProjectTemplateCatalogVerifier(Map.of("catalog-key-1", keyPair.getPublic()), clock);
    }
  }
}
