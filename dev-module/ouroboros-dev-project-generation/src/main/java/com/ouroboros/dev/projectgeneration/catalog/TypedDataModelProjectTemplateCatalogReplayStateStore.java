package com.ouroboros.dev.projectgeneration.catalog;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.data.model.TypedDataModel;

public class TypedDataModelProjectTemplateCatalogReplayStateStore implements ProjectTemplateCatalogReplayStateStore {

  private static final int MAX_CAS_ATTEMPTS = 4;

  private final TypedDataModel<String, ProjectTemplateCatalogReplayStateModel> dataModel;
  private final Clock clock;

  public TypedDataModelProjectTemplateCatalogReplayStateStore(
      TypedDataModel<String, ProjectTemplateCatalogReplayStateModel> dataModel) {
    this(dataModel, Clock.systemUTC());
  }

  public TypedDataModelProjectTemplateCatalogReplayStateStore(
      TypedDataModel<String, ProjectTemplateCatalogReplayStateModel> dataModel,
      Clock clock) {
    if (dataModel == null) {
      throw new IllegalArgumentException("Replay state data model is required");
    }
    this.dataModel = dataModel;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  @Override
  public ProjectTemplateCatalogReplayState accept(String catalogStateId, long sequence, String digest) {
    requireStateId(catalogStateId);
    ProjectTemplateCatalogReplayState requested = new ProjectTemplateCatalogReplayState(sequence, digest, 1);
    Throwable lastFailure = null;
    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      Optional<ProjectTemplateCatalogReplayStateModel> current = currentModel(catalogStateId);
      if (current.isEmpty()) {
        Try<ProjectTemplateCatalogReplayStateModel> inserted = dataModel.insert(newModel(catalogStateId, requested));
        if (inserted.isSuccess()) {
          return toState(inserted.get());
        }
        lastFailure = inserted.getCause();
        Optional<ProjectTemplateCatalogReplayStateModel> afterInsertRace = currentModel(catalogStateId);
        if (afterInsertRace.isEmpty()) {
          throw new IllegalStateException("Failed to insert catalog replay state", lastFailure);
        }
        continue;
      }

      ProjectTemplateCatalogReplayStateModel existing = current.get();
      ProjectTemplateCatalogReplayState existingState = toState(existing);
      if (sequence == existingState.sequence() && digest.equals(existingState.digest())) {
        return existingState;
      }
      if (sequence <= existingState.sequence()) {
        throw new IllegalArgumentException("Catalog replay sequence was not newer than shared high-water mark");
      }

      long nextVersion = existingState.version() + 1;
      Try<Long> updated = dataModel.update(
          casWhere(catalogStateId, existingState.version()),
          updateData(sequence, digest, nextVersion)
      );
      if (updated.isSuccess()) {
        if (updated.get() == 1) {
          return new ProjectTemplateCatalogReplayState(sequence, digest, nextVersion);
        }
      } else {
        lastFailure = updated.getCause();
        throw new IllegalStateException("Failed to update catalog replay state", lastFailure);
      }
    }
    throw new IllegalStateException("Catalog replay state CAS did not converge", lastFailure);
  }

  @Override
  public Optional<ProjectTemplateCatalogReplayState> current(String catalogStateId) {
    requireStateId(catalogStateId);
    return currentModel(catalogStateId).map(this::toState);
  }

  private Optional<ProjectTemplateCatalogReplayStateModel> currentModel(String catalogStateId) {
    Try<ProjectTemplateCatalogReplayStateModel> result = dataModel.get(catalogStateId);
    if (result.isFailure()) {
      throw new IllegalStateException("Failed to read catalog replay state", result.getCause());
    }
    return Optional.ofNullable(result.get());
  }

  private ProjectTemplateCatalogReplayStateModel newModel(
      String catalogStateId,
      ProjectTemplateCatalogReplayState state) {
    ProjectTemplateCatalogReplayStateModel model = new ProjectTemplateCatalogReplayStateModel();
    model.setCatalogStateId(catalogStateId);
    model.setSequence(state.sequence());
    model.setDigest(state.digest());
    model.setVersion(state.version());
    model.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    return model;
  }

  private ProjectTemplateCatalogReplayState toState(ProjectTemplateCatalogReplayStateModel model) {
    if (model.getSequence() == null || model.getDigest() == null || model.getVersion() == null) {
      throw new IllegalStateException("Catalog replay state row is incomplete");
    }
    return new ProjectTemplateCatalogReplayState(model.getSequence(), model.getDigest(), model.getVersion());
  }

  private Map<String, Object> casWhere(String catalogStateId, long version) {
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("catalogStateId", catalogStateId);
    where.put("version", version);
    return where;
  }

  private Map<String, Object> updateData(long sequence, String digest, long version) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("sequence", sequence);
    data.put("digest", digest);
    data.put("version", version);
    data.put("updatedAt", LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    return data;
  }

  private static void requireStateId(String catalogStateId) {
    if (catalogStateId == null || catalogStateId.isBlank()) {
      throw new IllegalArgumentException("Catalog state id is required");
    }
  }
}
