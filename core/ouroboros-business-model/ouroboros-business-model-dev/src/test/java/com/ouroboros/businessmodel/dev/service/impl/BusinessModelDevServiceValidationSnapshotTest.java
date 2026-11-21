package com.ouroboros.businessmodel.dev.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import io.vavr.control.Try;

import com.ouroboros.businessmodel.dev.vo.BusinessModelReadEnvelope;
import com.ouroboros.businessmodel.dev.vo.BusinessModelValidationState;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction;
import com.ouroboros.vcs.file.FileDiff;
import com.ouroboros.vcs.file.FileOperation;
import com.ouroboros.vcs.file.FileRevision;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.versioning.Tag;
import com.ouroboros.vcs.versioning.VersionState;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

class BusinessModelDevServiceValidationSnapshotTest {

  @Test
  void defaultMethodsShouldKeepLegacyReturnShape() {
    DevAppHolder.setAppName("business-model-validation-legacy");
    try {
      var vcsManager = new FakeVCSManager();
      var service = new TestBusinessModelDevService(vcsManager);
      var model = model("legacy");

      assertSame(model, service.create(model, "", false).get());
      assertSame(model, service.update("demo.legacy", model, false).get());
      assertTrue(service.getOne("demo.legacy", false).get() instanceof BusinessModelMeta);
      assertTrue(service.search(Collections.emptyMap(), false).get(0) instanceof ModelFileMetaWrapper);
      assertFalse(service.search(Collections.emptyMap(), false).get(0).toString().contains("BusinessModelReadEnvelope"));
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  void createUpdateGetAndInitShouldReturnEnvelopeWhenSnapshotRequested() {
    DevAppHolder.setAppName("business-model-validation-envelope");
    try {
      var vcsManager = new FakeVCSManager();
      var service = new TestBusinessModelDevService(vcsManager);
      var model = modelWithMissingRelation("order");

      var created = (BusinessModelReadEnvelope<?>) service.create(model, "", true).get();
      assertTrue(created.getBusinessModel() instanceof BusinessModelMeta);
      assertEquals("business-model-validation-envelope", created.getAppName());
      assertEquals(BusinessModelValidationState.INVALID, created.getValidationSnapshot().getValidationState());
      assertFalse(created.getValidationSnapshot().getValidationDiagnostics().isEmpty());
      assertFormalJson(vcsManager.read("business-model/demo.order.business.json"));

      var updated = (BusinessModelReadEnvelope<?>) service.update("demo.order", modelWithMissingRelation("order"), true).get();
      assertTrue(updated.getBusinessModel() instanceof BusinessModelMeta);
      assertEquals("business-model-validation-envelope", updated.getAppName());
      assertEquals(BusinessModelValidationState.INVALID, updated.getValidationSnapshot().getValidationState());
      assertFormalJson(vcsManager.read("business-model/demo.order.business.json"));

      var getOne = (BusinessModelReadEnvelope<?>) service.getOne("demo.order", true).get();
      assertTrue(getOne.getBusinessModel() instanceof BusinessModelMeta);
      assertEquals("business-model-validation-envelope", getOne.getAppName());
      assertNotNull(getOne.getValidationSnapshot());

      var initForm = (BusinessModelReadEnvelope<?>) service.getInitFormData("demo.order", true).get();
      assertTrue(initForm.getBusinessModel() instanceof BusinessModelMeta);
      assertEquals("business-model-validation-envelope", initForm.getAppName());
      assertNotNull(initForm.getValidationSnapshot());
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  void moveShouldPreserveBusinessModelTypedSuffix() {
    DevAppHolder.setAppName("business-model-validation-move");
    try {
      var vcsManager = new FakeVCSManager();
      vcsManager.files.put("business-model/demo.order.business.json", Json.toPrettyJsonString(model("order")));
      var service = new TestBusinessModelDevService(vcsManager);

      var result = service.move("demo.order", "demo.order-renamed", "rename business model");

      assertTrue(result.isSuccess());
      assertFalse(vcsManager.files.containsKey("business-model/demo.order.business.json"));
      assertTrue(vcsManager.files.containsKey("business-model/demo.order-renamed.business.json"));
      assertEquals("order-renamed", service.getOne("demo.order-renamed").orElseThrow().getName());
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  void searchShouldReturnWrapperEnvelopeAndPreserveFileMetadata() {
    DevAppHolder.setAppName("business-model-validation-search");
    try {
      var vcsManager = new FakeVCSManager();
      var service = new TestBusinessModelDevService(vcsManager);
      service.create(model("searchable"), "", false).get();

      var result = service.search(Collections.singletonMap("keyword", "search"), true);

      assertEquals(1, result.size());
      var wrapper = (ModelFileMetaWrapper<?>) result.get(0);
      assertTrue(wrapper.getModel() instanceof BusinessModelReadEnvelope);
      var envelope = (BusinessModelReadEnvelope<?>) wrapper.getModel();
      assertTrue(envelope.getBusinessModel() instanceof BusinessModelDevService.BusinessModelVO);
      assertEquals("business-model-validation-search", envelope.getAppName());
      assertNotNull(envelope.getValidationSnapshot());
      assertEquals(VersionState.UNKNOWN, wrapper.getVersionState());
      assertEquals(LockState.UNLOCKED, wrapper.getLockState());
      assertNotNull(wrapper.getCreationTime());
      assertNotNull(wrapper.getLastModifiedTime());
      assertTrue(wrapper.getSize() > 0);
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  void searchSnapshotShouldReuseSearchModelsForRelationValidation() {
    DevAppHolder.setAppName("business-model-validation-search-cache");
    try {
      var vcsManager = new FakeVCSManager();
      var service = new TestBusinessModelDevService(vcsManager);
      service.create(model("customer"), "", false).get();
      service.create(modelWithRelation("order", "demo.customer"), "", false).get();
      service.create(modelWithRelation("invoice", "demo.customer"), "", false).get();
      vcsManager.resetReadCounts();

      var result = service.search(Collections.emptyMap(), true);

      assertEquals(3, result.size());
      assertEquals(1, vcsManager.readCount("business-model/demo.customer.business.json"));
      assertEquals(1, vcsManager.readCount("business-model/demo.order.business.json"));
      assertEquals(1, vcsManager.readCount("business-model/demo.invoice.business.json"));
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  void formalSerializationShouldStripReadFaceValidationFields() {
    var service = new TestBusinessModelDevService(new FakeVCSManager());
    var model = service.parseForTest("""
        {
          "modelType": "default",
          "namespace": "demo",
          "name": "polluted",
          "title": "Polluted",
          "validationSnapshot": {"validationState": "INVALID"},
          "validationState": "INVALID",
          "validationSummary": "bad",
          "validationErrors": ["bad"],
          "validationDiagnostics": [{"diagnosticKind": "x", "message": "bad", "scope": "model"}],
          "lastValidatedTime": "2026-07-16T10:30:00"
        }
        """).get();

    assertFalse(model.getExtraProps().containsKey("validationSnapshot"));
    assertFalse(model.getExtraProps().containsKey("validationState"));
    assertFalse(model.getExtraProps().containsKey("validationErrors"));

    var json = service.permanentJsonForTest(model).get();
    assertFormalJson(json);
  }

  @Test
  void blankNameShouldStillBeRejectedAsSubmitError() {
    var service = new TestBusinessModelDevService(new FakeVCSManager());

    assertTrue(service.validateForTest(model(" ")).isFailure());
  }

  private static DefaultBusinessModelMeta model(String name) {
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("demo");
    meta.setName(name);
    meta.setTitle(name);
    meta.setIsEnabled(true);
    return meta;
  }

  private static DefaultBusinessModelMeta modelWithMissingRelation(String name) {
    return modelWithRelation(name, "demo.missingCustomer");
  }

  private static DefaultBusinessModelMeta modelWithRelation(String name, String relationModel) {
    var meta = model(name);
    var relation = new BusinessModelRelationMeta();
    relation.setName("customer");
    relation.setTitle("Customer");
    relation.setType("reference");
    relation.setModel(relationModel);
    meta.setRelations(List.of(relation));
    return meta;
  }

  private static void assertFormalJson(String json) {
    assertNotNull(json);
    assertFalse(json.contains("\"validationSnapshot\""));
    assertFalse(json.contains("\"validationState\""));
    assertFalse(json.contains("\"validationSummary\""));
    assertFalse(json.contains("\"validationErrors\""));
    assertFalse(json.contains("\"validationDiagnostics\""));
    assertFalse(json.contains("\"lastValidatedTime\""));
  }

  private static class TestBusinessModelDevService extends BusinessModelDevService {
    private TestBusinessModelDevService(FakeVCSManager vcsManager) {
      this.vcsManagerCenter = new VCSManagerCenter() {
        @Override
        public VCSManager getVcsManager(String appName) {
          return vcsManager;
        }
      };
      this.publisher = new ApplicationEventPublisher() {
        @Override
        public void publishEvent(Object event) {
        }
      };
    }

    private Try<Void> validateForTest(BusinessModelMeta model) {
      return super.validateModel(model);
    }

    private Try<BusinessModelMeta> parseForTest(String requestBody) {
      return super.buildModelFromRequestBody(requestBody);
    }

    private Try<String> permanentJsonForTest(BusinessModelMeta model) {
      return super.getPermanentJson(model);
    }
  }

  private static class FakeVCSManager implements VCSManager {
    private final Map<String, String> files = new HashMap<>();
    private final Map<String, Integer> readCounts = new HashMap<>();

    private String read(String path) {
      return files.get(path);
    }

    private void resetReadCounts() {
      readCounts.clear();
    }

    private int readCount(String path) {
      return readCounts.getOrDefault(path, 0);
    }

    private String readFile(String path) {
      readCounts.merge(path, 1, Integer::sum);
      return files.get(path);
    }

    @Override
    public void destroy() {
    }

    @Override
    public VCSFile getFile(String relativePath) {
      return new FakeVCSFile(relativePath, this);
    }

    @Override
    public Try<Void> lock(String relativePath, boolean force) {
      return Try.success(null);
    }

    @Override
    public Try<Void> lock(Collection<String> relativePaths, boolean force) {
      return Try.success(null);
    }

    @Override
    public Try<Void> unlock(String relativePath, boolean force) {
      return Try.success(null);
    }

    @Override
    public Try<Void> unlock(Collection<String> relativePaths, boolean force) {
      return Try.success(null);
    }

    @Override
    public VCSAction.CreateFileAction createFile(String relativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.RemoveFileAction removeFile(String relativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.UpdateFileAction updateFile(String relativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.MoveFileAction moveFile(String relativePath, String newRelativePath) {
      return new FakeMoveFileAction(relativePath, newRelativePath);
    }

    @Override
    public VCSAction.BatchAction batchOperation() {
      return new FakeBatchAction(files);
    }

    private class FakeMoveFileAction implements VCSAction.MoveFileAction {
      private String fromPath;
      private String toPath;
      private Function<String, String> contentUpdater;

      private FakeMoveFileAction(String fromPath, String toPath) {
        this.fromPath = fromPath;
        this.toPath = toPath;
      }

      @Override
      public VCSAction.MoveFileAction committer(String user) {
        return this;
      }

      @Override
      public VCSAction.MoveFileAction committerEmail(String email) {
        return this;
      }

      @Override
      public VCSAction.MoveFileAction autoLock() {
        return this;
      }

      @Override
      public VCSAction.MoveFileAction unlockWhenCommit() {
        return this;
      }

      @Override
      public Try<Boolean> write(boolean forceWrite) {
        return Try.success(true);
      }

      @Override
      public Try<Boolean> commit(String message) {
        var content = files.remove(fromPath);
        if (content == null) {
          return Try.success(false);
        }
        files.put(toPath, contentUpdater == null ? content : contentUpdater.apply(content));
        return Try.success(true);
      }

      @Override
      public VCSAction.MoveFileAction from(String relativePath) {
        this.fromPath = relativePath;
        return this;
      }

      @Override
      public VCSAction.MoveFileAction to(String newRelativePath) {
        this.toPath = newRelativePath;
        return this;
      }

      @Override
      public VCSAction.MoveFileAction updateContent(Function<String, String> updateContent) {
        this.contentUpdater = updateContent;
        return this;
      }
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String fileExtension) {
      return files.keySet().stream()
          .filter(path -> path.startsWith(relativeParentPath + "/"))
          .filter(path -> path.endsWith(fileExtension))
          .map(path -> new FakeVCSFile(path, this));
    }

    @Override
    public List<ChangeLog> getChangeLogs(String relativePath) {
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(String relativePath, int skip, int limit) {
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(Collection<String> relativePaths) {
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(Collection<String> relativePaths, int skip, int limit) {
      return Collections.emptyList();
    }

    @Override
    public List<VCSFile> getUncommittedFiles(String relativePath) {
      return Collections.emptyList();
    }

    @Override
    public VCSFile getFile(String relativePath, String revision) {
      return getFile(relativePath);
    }

    @Override
    public VCSFile getFileFromBranch(String relativePath, String branchName) {
      return getFile(relativePath);
    }

    @Override
    public VCSFile getFileFromTag(String relativePath, String tagName) {
      return getFile(relativePath);
    }

    @Override
    public List<VCSFile> getFileList(String relativeParentPath, String revision) {
      return Collections.emptyList();
    }

    @Override
    public List<VCSFile> getFileListFromBranch(String relativeParentPath, String branchName) {
      return Collections.emptyList();
    }

    @Override
    public List<VCSFile> getFileListFromTag(String relativeParentPath, String tagName) {
      return Collections.emptyList();
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String revision, String fileExtension) {
      return walkDirectory(relativeParentPath, fileExtension);
    }

    @Override
    public Try<String> createTag(String tagName, String message) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<String> createTag(String tagName, String revision, String message) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<Boolean> deleteTag(String tagName) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<List<Tag>> getTagList() {
      return Try.success(Collections.emptyList());
    }

    @Override
    public Try<String> getCurrentBranch() {
      return Try.success("main");
    }

    @Override
    public Try<List<String>> getBranchList() {
      return Try.success(Collections.singletonList("main"));
    }

    @Override
    public Try<String> createBranch(String branchName) {
      return Try.success(branchName);
    }

    @Override
    public Try<String> createBranch(String branchName, String fromRevision) {
      return Try.success(branchName);
    }

    @Override
    public Try<Boolean> switchBranch(String branchName) {
      return Try.success(true);
    }

    @Override
    public Try<VCSConfig.RemoteConfig> getRemoteConfig() {
      return Try.success(null);
    }

    @Override
    public Try<Boolean> setRemoteConfig(VCSConfig.RemoteConfig remoteConfig) {
      return Try.success(true);
    }

    @Override
    public Try<Boolean> pull() {
      return Try.success(true);
    }

    @Override
    public Try<Boolean> push() {
      return Try.success(true);
    }

    @Override
    public Try<Boolean> isWorkspaceClean() {
      return Try.success(true);
    }
  }

  private static class FakeBatchAction implements VCSAction.BatchAction {
    private final Map<String, String> files;
    private final List<Runnable> validations = new java.util.ArrayList<>();
    private final Map<String, String> creates = new LinkedHashMap<>();
    private final Map<String, String> updates = new LinkedHashMap<>();
    private final List<Runnable> operations = new java.util.ArrayList<>();

    private FakeBatchAction(Map<String, String> files) {
      this.files = files;
    }

    @Override
    public VCSAction.BatchAction committer(String user) {
      return this;
    }

    @Override
    public VCSAction.BatchAction committerEmail(String email) {
      return this;
    }

    @Override
    public VCSAction.BatchAction autoLock() {
      return this;
    }

    @Override
    public VCSAction.BatchAction unlockWhenCommit() {
      return this;
    }

    @Override
    public VCSAction.BatchAction beforeWrite(Runnable validation) {
      validations.add(validation);
      return this;
    }

    @Override
    public Try<Boolean> write(boolean forceWrite) {
      return Try.of(() -> {
        validations.forEach(Runnable::run);
        apply();
        return true;
      });
    }

    @Override
    public Try<Boolean> commit(String message) {
      return Try.of(() -> {
        validations.forEach(Runnable::run);
        apply();
        return true;
      });
    }

    @Override
    public VCSAction.BatchAction add(String relativePath) {
      return this;
    }

    @Override
    public VCSAction.BatchAction add(Collection<String> relativePaths) {
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, String content) {
      creates.put(relativePath, content);
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, byte[] content) {
      return create(relativePath, new String(content));
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, InputStream content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.BatchAction remove(String relativePath) {
      files.remove(relativePath);
      return this;
    }

    @Override
    public VCSAction.BatchAction remove(Collection<String> relativePaths) {
      relativePaths.forEach(files::remove);
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, String content) {
      updates.put(relativePath, content);
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, byte[] content) {
      return update(relativePath, new String(content));
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, InputStream content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath) {
      return move(relativePath, newRelativePath, null);
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath, java.util.function.Function<String, String> updateContent) {
      operations.add(() -> {
        var content = files.remove(relativePath);
        if (content == null) {
          return;
        }
        files.put(newRelativePath, updateContent == null ? content : updateContent.apply(content));
      });
      return this;
    }

    private void apply() {
      operations.forEach(Runnable::run);
      files.putAll(creates);
      files.putAll(updates);
      operations.clear();
      creates.clear();
      updates.clear();
    }
  }

  private static class FakeVCSFile implements VCSFile {
    private final String path;
    private final FakeVCSManager vcsManager;

    private FakeVCSFile(String path, FakeVCSManager vcsManager) {
      this.path = path;
      this.vcsManager = vcsManager;
    }

    @Override
    public boolean exists() {
      return vcsManager.files.containsKey(path);
    }

    @Override
    public String getPath() {
      return path;
    }

    @Override
    public String getFileName() {
      return path;
    }

    @Override
    public String getContent() {
      return vcsManager.readFile(path);
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(getBytesArray());
    }

    @Override
    public byte[] getBytesArray() {
      var content = vcsManager.read(path);
      return content == null ? new byte[0] : content.getBytes();
    }

    @Override
    public Long getSize() {
      return (long) getBytesArray().length;
    }

    @Override
    public LocalDateTime getCreationTime() {
      return LocalDateTime.of(2026, 7, 16, 10, 0);
    }

    @Override
    public LocalDateTime getLastCommittedTime() {
      return LocalDateTime.of(2026, 7, 16, 10, 5);
    }

    @Override
    public LocalDateTime getLastModifiedTime() {
      return LocalDateTime.of(2026, 7, 16, 10, 10);
    }

    @Override
    public VersionState getVersionState() {
      return VersionState.UNKNOWN;
    }

    @Override
    public LockState getLockState() {
      return LockState.UNLOCKED;
    }

    @Override
    public String getLockOwner() {
      return null;
    }

    @Override
    public String getLockOwnerName() {
      return null;
    }

    @Override
    public Try<Void> lock(boolean forceLock) {
      return Try.success(null);
    }

    @Override
    public Try<Void> unlock(boolean forceUnlock) {
      return Try.success(null);
    }

    @Override
    public Try<Boolean> write(String content) {
      vcsManager.files.put(path, content);
      return Try.success(true);
    }

    @Override
    public Try<Boolean> write(byte[] content) {
      vcsManager.files.put(path, new String(content));
      return Try.success(true);
    }

    @Override
    public Try<Boolean> write(InputStream content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<VCSFile> move(String newPath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<VCSFile> remove() {
      vcsManager.files.remove(path);
      return Try.success(this);
    }

    @Override
    public Try<Boolean> commit(String message) {
      return Try.success(true);
    }

    @Override
    public Try<Boolean> rollback(boolean forceRollback) {
      return Try.success(true);
    }

    @Override
    public String getRevision() {
      return null;
    }

    @Override
    public boolean isHistorical() {
      return false;
    }

    @Override
    public VCSFile atRevision(String revision) {
      return this;
    }

    @Override
    public VCSFile atBranch(String branchName) {
      return this;
    }

    @Override
    public VCSFile atTag(String tagName) {
      return this;
    }

    @Override
    public Try<FileDiff> diffWith(String otherRevision) {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public Try<FileDiff> diffWithWorkingCopy() {
      return Try.failure(new UnsupportedOperationException());
    }

    @Override
    public List<FileRevision> getRevisions() {
      return Collections.emptyList();
    }

    @Override
    public List<FileRevision> getRevisions(int skip, int limit) {
      return Collections.emptyList();
    }

    @Override
    public boolean isOperationAllowed(FileOperation operation) {
      return VCSFile.super.isOperationAllowed(operation);
    }
  }
}
