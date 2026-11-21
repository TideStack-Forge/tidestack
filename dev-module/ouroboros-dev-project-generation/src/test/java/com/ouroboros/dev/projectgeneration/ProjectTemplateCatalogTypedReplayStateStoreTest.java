package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.PluginDescriptor;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogReplayStateModel;
import com.ouroboros.dev.projectgeneration.catalog.TypedDataModelProjectTemplateCatalogReplayStateStore;

class ProjectTemplateCatalogTypedReplayStateStoreTest {

  @Test
  void shouldUseVersionPredicateForSharedCompareAndAdvance() {
    var dataModel = new ReplayStateDataModel();
    var store = new TypedDataModelProjectTemplateCatalogReplayStateStore(
        dataModel,
        Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC)
    );

    store.accept("default", 10, "a".repeat(64));
    var newer = store.accept("default", 11, "b".repeat(64));

    assertEquals(11, newer.sequence());
    assertEquals(Map.of("catalogStateId", "default", "version", 1L), dataModel.lastUpdateWhere);
    assertEquals(11L, dataModel.lastUpdateData.get("sequence"));
    assertEquals("b".repeat(64), dataModel.lastUpdateData.get("digest"));
    assertEquals(2L, dataModel.lastUpdateData.get("version"));
  }

  @Test
  void shouldRejectWhenConcurrentCasAdvanceFindsDifferentDigestAtSameSequence() {
    var dataModel = new ReplayStateDataModel();
    var store = new TypedDataModelProjectTemplateCatalogReplayStateStore(dataModel);

    store.accept("default", 10, "a".repeat(64));
    dataModel.beforeNextUpdate = () -> dataModel.force("default", 11, "c".repeat(64), 2);

    assertThrows(IllegalArgumentException.class, () -> store.accept("default", 11, "b".repeat(64)));
    assertEquals("c".repeat(64), dataModel.get("default").get().getDigest());
  }

  private static final class ReplayStateDataModel implements TypedDataModel<String, ProjectTemplateCatalogReplayStateModel> {

    private final Map<String, ProjectTemplateCatalogReplayStateModel> rows = new ConcurrentHashMap<>();
    private Map<String, Object> lastUpdateWhere = Map.of();
    private Map<String, Object> lastUpdateData = Map.of();
    private Runnable beforeNextUpdate;

    @Override
    public DataModel getDataModel() {
      return null;
    }

    @Override
    public Try<ProjectTemplateCatalogReplayStateModel> insert(ProjectTemplateCatalogReplayStateModel data) {
      if (rows.putIfAbsent(data.getCatalogStateId(), copy(data)) != null) {
        return Try.failure(new IllegalStateException("duplicate catalog state"));
      }
      return Try.success(copy(data));
    }

    @Override
    public Try<Long> update(Map<String, Object> where, Map<String, Object> data) {
      lastUpdateWhere = new LinkedHashMap<>(where);
      lastUpdateData = new LinkedHashMap<>(data);
      Runnable hook = beforeNextUpdate;
      beforeNextUpdate = null;
      if (hook != null) {
        hook.run();
      }
      String id = String.valueOf(where.get("catalogStateId"));
      Long version = (Long) where.get("version");
      ProjectTemplateCatalogReplayStateModel current = rows.get(id);
      if (current == null || !version.equals(current.getVersion())) {
        return Try.success(0L);
      }
      current.setSequence((Long) data.get("sequence"));
      current.setDigest((String) data.get("digest"));
      current.setVersion((Long) data.get("version"));
      return Try.success(1L);
    }

    @Override
    public Try<ProjectTemplateCatalogReplayStateModel> get(String id) {
      return Try.success(copy(rows.get(id)));
    }

    void force(String id, long sequence, String digest, long version) {
      ProjectTemplateCatalogReplayStateModel model = new ProjectTemplateCatalogReplayStateModel();
      model.setCatalogStateId(id);
      model.setSequence(sequence);
      model.setDigest(digest);
      model.setVersion(version);
      rows.put(id, model);
    }

    private ProjectTemplateCatalogReplayStateModel copy(ProjectTemplateCatalogReplayStateModel source) {
      if (source == null) {
        return null;
      }
      ProjectTemplateCatalogReplayStateModel copy = new ProjectTemplateCatalogReplayStateModel();
      copy.setCatalogStateId(source.getCatalogStateId());
      copy.setSequence(source.getSequence());
      copy.setDigest(source.getDigest());
      copy.setVersion(source.getVersion());
      copy.setUpdatedAt(source.getUpdatedAt());
      return copy;
    }

    @Override
    public Try<List<ProjectTemplateCatalogReplayStateModel>> batchInsert(Collection<ProjectTemplateCatalogReplayStateModel> dataList) {
      return unsupported();
    }

    @Override
    public Try<Long> delete(String id) {
      return unsupported();
    }

    @Override
    public Try<Long> delete(Collection<String> ids) {
      return unsupported();
    }

    @Override
    public Try<Long> delete(Map<String, Object> where) {
      return unsupported();
    }

    @Override
    public Try<Long> update(String id, ProjectTemplateCatalogReplayStateModel data) {
      return unsupported();
    }

    @Override
    public Try<Long> update(String id, Map<String, Object> data) {
      return unsupported();
    }

    @Override
    public Try<Long> update(Collection<String> ids, Map<String, Object> data) {
      return unsupported();
    }

    @Override
    public Try<Long> count(Map<String, Object> where) {
      return unsupported();
    }

    @Override
    public Try<ProjectTemplateCatalogReplayStateModel> get(String id, Map<String, Object> statement) {
      return unsupported();
    }

    @Override
    public Try<List<ProjectTemplateCatalogReplayStateModel>> query(Collection<String> ids) {
      return unsupported();
    }

    @Override
    public Try<List<ProjectTemplateCatalogReplayStateModel>> query(Map<String, Object> statement) {
      return unsupported();
    }

    @Override
    public Try<List<ProjectTemplateCatalogReplayStateModel>> query(Collection<String> select, Map<String, Object> where) {
      return unsupported();
    }

    @Override
    public Try<List<ProjectTemplateCatalogReplayStateModel>> query(
        Collection<String> select,
        Map<String, Object> where,
        String orderBy) {
      return unsupported();
    }

    @Override
    public Try<List<ProjectTemplateCatalogReplayStateModel>> query(
        Collection<String> select,
        Map<String, Object> where,
        String orderBy,
        Integer offset,
        Integer limit) {
      return unsupported();
    }

    @Override
    public TypedDataModel<String, ProjectTemplateCatalogReplayStateModel> withPlugins(List<PluginDescriptor> pluginDescriptors) {
      return this;
    }

    @Override
    public TypedDataModel<String, ProjectTemplateCatalogReplayStateModel> withoutPlugins(List<String> pluginNames) {
      return this;
    }

    @Override
    public TypedDataModel<String, ProjectTemplateCatalogReplayStateModel> withoutPlugins() {
      return this;
    }

    @Override
    public boolean hasPlugin(String name) {
      return false;
    }

    private static <T> Try<T> unsupported() {
      return Try.failure(new UnsupportedOperationException("Unused in typed replay state store tests"));
    }
  }
}
