package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.vcs.versioning.ChangeLog;

class FieldTypeDefinitionMetadataAdapterTest {

  @Test
  void getAppAllMetadataByModule_convertsFieldTypeModels() {
    var model = new LinkedHashMap<String, Object>();
    model.put("name", "builtinAlpha");
    model.put("label", "Builtin Alpha");
    model.put("description", "alpha");
    var adapter = new FieldTypeDefinitionMetadataAdapter(
        new StubFieldTypeDevelopService(Arrays.asList(model, "ignored"))
    );

    List<Metadata> metadata = adapter.getAppAllMetadataByModule("crm", "field");

    assertEquals(1, metadata.size());
    assertEquals("builtinAlpha", metadata.get(0).getName());
    assertEquals("Builtin Alpha", metadata.get(0).getLabel());
    assertSame(model, metadata.get(0).getDetails());
    assertEquals(Integer.MAX_VALUE, metadata.get(0).getPriority());
  }

  @Test
  void getAppAllMetadataByModule_ignoresOtherModules() {
    var adapter = new FieldTypeDefinitionMetadataAdapter(
        new StubFieldTypeDevelopService(Collections.emptyList())
    );

    assertTrue(adapter.getAppAllMetadataByModule("crm", "business-model").isEmpty());
  }

  @DevelopModule("field")
  private static class StubFieldTypeDevelopService implements DevelopService<Map<String, Object>> {
    private final List<?> models;

    private StubFieldTypeDevelopService(List<?> models) {
      this.models = models;
    }

    @Override
    public Try<Void> lock(String fullName, boolean force) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Void> lock(String fullName, boolean force, Map<String, Object> oldModel, Map<String, Object> newModel) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Void> unlock(String fullName, boolean force) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Void> unlock(String fullNames, boolean force, Map<String, Object> oldModel, Map<String, Object> newModel) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Map<String, Object>> create(Map<String, Object> model, String commitMsg) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Map<String, Object>> delete(String fullName, String commitMsg) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Map<String, Object>> update(String fullName, Map<String, Object> model) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Boolean> rollback(String fullName) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Boolean> commit(String fullName, String commitMsg) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Optional<Map<String, Object>> getOne(String fullName) {
      return Optional.empty();
    }

    @Override
    public Try<?> getInitFormData(String fullName) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<?> getStatus(String fullName) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public List<?> search(Map<String, Object> searchParam) {
      return models;
    }

    @Override
    public List<ChangeLog> getAllChangeLogs(String fullName) {
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(String fullName, int page, int size) {
      return Collections.emptyList();
    }

    @Override
    public Try<Map<String, Object>> buildModelFromFileContent(String fileContent) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Map<String, Object>> buildModelFromRequestBody(String requestBody) {
      return Try.failure(new UnsupportedOperationException());
    }
  }
}
