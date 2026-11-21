package com.ouroboros.dev.projectgeneration.catalog;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class ProjectTemplateCatalogSnapshotManager {

  private final String catalogStateId;
  private final SignedProjectTemplateCatalogVerifier verifier;
  private final ProjectTemplateCatalogReplayStateStore replayStateStore;
  private final ProjectTemplateCatalogRemoteSource remoteSource;
  private final ProjectTemplateCatalogLkgStore lkgStore;
  private final Clock clock;
  private final Object refreshLock = new Object();
  private final AtomicReference<ProjectTemplateCatalogSnapshot> currentSnapshot = new AtomicReference<>();
  private volatile LastKnownGoodCatalog lastKnownGoodCatalog;

  public ProjectTemplateCatalogSnapshotManager(
      String catalogStateId,
      SignedProjectTemplateCatalogVerifier verifier,
      ProjectTemplateCatalogReplayStateStore replayStateStore,
      ProjectTemplateCatalogRemoteSource remoteSource,
      Clock clock) {
    this(catalogStateId, verifier, replayStateStore, remoteSource, null, clock);
  }

  public ProjectTemplateCatalogSnapshotManager(
      String catalogStateId,
      SignedProjectTemplateCatalogVerifier verifier,
      ProjectTemplateCatalogReplayStateStore replayStateStore,
      ProjectTemplateCatalogRemoteSource remoteSource,
      ProjectTemplateCatalogLkgStore lkgStore,
      Clock clock) {
    if (catalogStateId == null || catalogStateId.isBlank()) {
      throw new IllegalArgumentException("Catalog state id is required");
    }
    if (verifier == null || replayStateStore == null || remoteSource == null) {
      throw new IllegalArgumentException("Catalog snapshot dependencies are required");
    }
    this.catalogStateId = catalogStateId;
    this.verifier = verifier;
    this.replayStateStore = replayStateStore;
    this.remoteSource = remoteSource;
    this.lkgStore = lkgStore;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public Optional<ProjectTemplateCatalogSnapshot> current() {
    ProjectTemplateCatalogSnapshot snapshot = currentSnapshot.get();
    return matchesSharedReplayPair(snapshot) ? Optional.of(snapshot) : Optional.empty();
  }

  public ProjectTemplateCatalogSnapshot currentOrRefresh() {
    ProjectTemplateCatalogSnapshot snapshot = currentSnapshot.get();
    if (matchesSharedReplayPair(snapshot)) {
      return snapshot;
    }
    synchronized (refreshLock) {
      snapshot = currentSnapshot.get();
      if (matchesSharedReplayPair(snapshot)) {
        return snapshot;
      }
      return refreshRemoteOrLkg();
    }
  }

  public void installLastKnownGood(LastKnownGoodCatalog lastKnownGoodCatalog) {
    this.lastKnownGoodCatalog = lastKnownGoodCatalog;
  }

  private ProjectTemplateCatalogSnapshot refreshRemoteOrLkg() {
    try {
      ProjectTemplateCatalogFetchResult fetched = remoteSource.fetch();
      if (fetched == null) {
        throw new ProjectTemplateCatalogRemoteException("Remote catalog response is empty");
      }
      ProjectTemplateCatalogSnapshot snapshot = verifier.verify(
          fetched.catalogBytes(),
          fetched.signatureEnvelopeBytes()
      );
      replayStateStore.accept(catalogStateId, snapshot.sequence(), snapshot.digest());
      LastKnownGoodCatalog lkg = toLastKnownGood(snapshot);
      if (lkgStore != null) {
        lkgStore.save(lkg);
      }
      lastKnownGoodCatalog = lkg;
      currentSnapshot.set(snapshot);
      return snapshot;
    } catch (ProjectTemplateCatalogRemoteException e) {
      return loadLastKnownGoodOrUnavailable(e);
    }
  }

  private ProjectTemplateCatalogSnapshot loadLastKnownGoodOrUnavailable(ProjectTemplateCatalogRemoteException cause) {
    LastKnownGoodCatalog lkg;
    try {
      lkg = loadLastKnownGood();
    } catch (RuntimeException lkgFailure) {
      throw new ProjectTemplateCatalogUnavailableException(
          "Last-known-good project template catalog cannot be loaded",
          lkgFailure
      );
    }
    ProjectTemplateCatalogReplayState replayState = replayStateStore.current(catalogStateId).orElse(null);
    if (lkg == null || !lkg.isUsable(replayState, clock.instant())) {
      throw new ProjectTemplateCatalogUnavailableException("Project template catalog is unavailable", cause);
    }
    ProjectTemplateCatalogSnapshot snapshot;
    try {
      snapshot = verifier.verify(lkg.catalogBytes(), lkg.signatureEnvelopeBytes());
    } catch (RuntimeException verificationFailure) {
      throw new ProjectTemplateCatalogUnavailableException(
          "Last-known-good project template catalog is no longer trusted",
          verificationFailure
      );
    }
    if (!matchesReplayState(snapshot, replayState)) {
      throw new ProjectTemplateCatalogUnavailableException("Last-known-good catalog does not match shared replay state");
    }
    currentSnapshot.set(snapshot);
    return snapshot;
  }

  private LastKnownGoodCatalog loadLastKnownGood() {
    LastKnownGoodCatalog lkg = lastKnownGoodCatalog;
    if (lkg != null || lkgStore == null) {
      return lkg;
    }
    lkg = lkgStore.load().orElse(null);
    if (lkg != null) {
      lastKnownGoodCatalog = lkg;
    }
    return lkg;
  }

  private boolean matchesSharedReplayPair(ProjectTemplateCatalogSnapshot snapshot) {
    if (snapshot == null) {
      return false;
    }
    Optional<ProjectTemplateCatalogReplayState> replayState = replayStateStore.current(catalogStateId);
    return replayState.isPresent() && matchesReplayState(snapshot, replayState.get());
  }

  private boolean matchesReplayState(
      ProjectTemplateCatalogSnapshot snapshot,
      ProjectTemplateCatalogReplayState replayState) {
    return replayState != null
        && snapshot.sequence() == replayState.sequence()
        && snapshot.digest().equals(replayState.digest());
  }

  private LastKnownGoodCatalog toLastKnownGood(ProjectTemplateCatalogSnapshot snapshot) {
    return new LastKnownGoodCatalog(
        snapshot.catalogBytes(),
        snapshot.signatureEnvelopeBytes(),
        clock.instant(),
        snapshot.expiresAt(),
        snapshot.sequence(),
        snapshot.digest(),
        snapshot.signatureKeyId()
    );
  }
}
