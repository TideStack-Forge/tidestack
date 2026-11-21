package com.ouroboros.workflow.approval.dev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.ouroboros.workflow.approval.dev.model.ApprovalWorkflowDefinitionAsset;

import io.vavr.control.Try;

class ApprovalWorkflowDefinitionDevelopServiceTest {
  @Test
  void createWritesSingleWorkflowMetadataAsset() {
    DevAppHolder.setAppName("approval-asset-test");
    try {
      FakeVCSManager vcsManager = new FakeVCSManager();
      ApprovalWorkflowDefinitionDevelopService service = service(vcsManager);

      Try<ApprovalWorkflowDefinitionAsset> result = service.create(
          service.buildModelFromRequestBody(definition("purchase-approval", "采购审批", "finance")).get(),
          "create approval asset");

      assertTrue(result.isSuccess());
      assertTrue(vcsManager.exists("workflow/purchase-approval.workflow.json"));
      assertFalse(vcsManager.exists("workflow/purchase-approval.json"));
      assertFalse(vcsManager.exists("workflow/purchase-approval.meta.json"));

      JsonNode definitionAsset = Json.tryToBean(JsonNode.class,
          vcsManager.read("workflow/purchase-approval.workflow.json")).get();
      assertEquals(1, definitionAsset.path("formatVersion").asInt());
      assertEquals("purchase-approval", definitionAsset.path("name").asText());
      assertEquals("采购审批", definitionAsset.path("title").asText());
      assertEquals(1, definitionAsset.path("version").asInt());
      assertTrue(definitionAsset.path("enabled").asBoolean());
      assertEquals("businessModel", definitionAsset.path("form").path("formType").asText());
      assertEquals("purchaseRequest", definitionAsset.path("form").path("formId").asText());
      assertEquals("StartNode", definitionAsset.path("nodes").get(0).path("type").asText());
      assertFalse(definitionAsset.has("flowDefinition"));
      assertFalse(definitionAsset.has("id"));
      assertFalse(definitionAsset.has("formType"));
      assertFalse(definitionAsset.has("formKey"));
      assertFalse(definitionAsset.has("definitionId"));
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  void updateIncrementsStaticVersionEvenWhenDefinitionContentIsSame() {
    DevAppHolder.setAppName("approval-asset-version-test");
    try {
      FakeVCSManager vcsManager = new FakeVCSManager();
      ApprovalWorkflowDefinitionDevelopService service = service(vcsManager);
      ApprovalWorkflowDefinitionAsset model =
          service.buildModelFromRequestBody(definition("purchase-approval", "采购审批", "")).get();

      assertTrue(service.create(model, "create approval asset").isSuccess());
      assertTrue(service.update("purchase-approval", model).isSuccess());
      assertTrue(service.update("purchase-approval", model).isSuccess());

      JsonNode metadata = Json.tryToBean(JsonNode.class,
          vcsManager.read("workflow/purchase-approval.workflow.json")).get();
      assertEquals(3, metadata.path("version").asInt());
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  void searchAndReadExposeMetadataWithoutWrappingDefinitionAssetFile() {
    DevAppHolder.setAppName("approval-asset-read-test");
    try {
      FakeVCSManager vcsManager = new FakeVCSManager();
      ApprovalWorkflowDefinitionDevelopService service = service(vcsManager);

      assertTrue(service.create(
          service.buildModelFromRequestBody(definition("purchase-approval", "采购审批", "")).get(),
          "create approval asset").isSuccess());

      ApprovalWorkflowDefinitionAsset one = service.getOne("purchase-approval").get();
      assertEquals("purchase-approval", one.getId());
      assertEquals("purchase-approval", one.getName());
      assertEquals("采购审批", one.getTitle());
      assertEquals(Integer.valueOf(1), one.getVersion());
      assertEquals("purchase-approval", one.getFlowKey());
      assertEquals(1, service.search(Collections.singletonMap("keyword", "purchase")).size());
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  void initFormDataReturnsStaticMetadataForWorkflowEditorSource() {
    DevAppHolder.setAppName("approval-asset-init-form-test");
    try {
      FakeVCSManager vcsManager = new FakeVCSManager();
      ApprovalWorkflowDefinitionDevelopService service = service(vcsManager);

      assertTrue(service.create(
          service.buildModelFromRequestBody(definition("purchase-approval", "采购审批", "")).get(),
          "create approval asset").isSuccess());

      JsonNode initFormData = (JsonNode) service.getInitFormData("purchase-approval").get();
      assertEquals(1, initFormData.path("formatVersion").asInt());
      assertEquals("purchase-approval", initFormData.path("name").asText());
      assertEquals("采购审批", initFormData.path("title").asText());
      assertEquals("businessModel", initFormData.path("form").path("formType").asText());
      assertEquals("purchaseRequest", initFormData.path("form").path("formId").asText());
      assertEquals("StartNode", initFormData.path("nodes").get(0).path("type").asText());
      assertFalse(initFormData.has("flowDefinition"));
      assertFalse(initFormData.has("id"));
      assertFalse(initFormData.has("formKey"));
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  void updateRejectsDefinitionIdDifferentFromFullName() {
    DevAppHolder.setAppName("approval-asset-rename-test");
    try {
      FakeVCSManager vcsManager = new FakeVCSManager();
      ApprovalWorkflowDefinitionDevelopService service = service(vcsManager);

      assertTrue(service.create(
          service.buildModelFromRequestBody(definition("purchase-approval", "采购审批", "")).get(),
          "create approval asset").isSuccess());

      ApprovalWorkflowDefinitionAsset renamed =
          service.buildModelFromRequestBody(definition("renamed-approval", "改名审批", "")).get();
      assertTrue(service.update("purchase-approval", renamed).isFailure());

      JsonNode definitionAsset = Json.tryToBean(JsonNode.class,
          vcsManager.read("workflow/purchase-approval.workflow.json")).get();
      assertEquals("purchase-approval", definitionAsset.path("name").asText());
      assertFalse(vcsManager.exists("workflow/renamed-approval.workflow.json"));
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  void createRejectsUnsafeDefinitionIdPathSegments() {
    DevAppHolder.setAppName("approval-asset-safe-id-test");
    try {
      FakeVCSManager vcsManager = new FakeVCSManager();
      ApprovalWorkflowDefinitionDevelopService service = service(vcsManager);

      assertTrue(service.create(
          service.buildModelFromRequestBody(definition("../escape", "逃逸审批", "")).get(),
          "create approval asset").isFailure());
      assertTrue(service.create(
          service.buildModelFromRequestBody(definition("approval/nested", "嵌套审批", "")).get(),
          "create approval asset").isFailure());
      assertTrue(service.create(
          service.buildModelFromRequestBody(definition(" purchase-approval ", "空白审批", "")).get(),
          "create approval asset").isFailure());
      assertFalse(vcsManager.exists("workflow/../escape.workflow.json"));
      assertFalse(vcsManager.exists("workflow/nested.workflow.json"));
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  void deleteRemovesSingleWorkflowMetadataAsset() {
    DevAppHolder.setAppName("approval-asset-delete-test");
    try {
      FakeVCSManager vcsManager = new FakeVCSManager();
      ApprovalWorkflowDefinitionDevelopService service = service(vcsManager);

      assertTrue(service.create(
          service.buildModelFromRequestBody(definition("purchase-approval", "采购审批", "")).get(),
          "create approval asset").isSuccess());
      assertTrue(service.delete("purchase-approval", "delete approval asset").isSuccess());

      assertFalse(vcsManager.exists("workflow/purchase-approval.workflow.json"));
      assertFalse(vcsManager.exists("workflow/purchase-approval.meta.json"));
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  @Test
  void rollbackRestoresWorkflowMetadataAsset() {
    DevAppHolder.setAppName("approval-asset-rollback-test");
    try {
      FakeVCSManager vcsManager = new FakeVCSManager();
      ApprovalWorkflowDefinitionDevelopService service = service(vcsManager);
      ApprovalWorkflowDefinitionAsset model =
          service.buildModelFromRequestBody(definition("purchase-approval", "采购审批", "")).get();

      assertTrue(service.create(model, "create approval asset").isSuccess());
      vcsManager.snapshot();

      ApprovalWorkflowDefinitionAsset updated =
          service.buildModelFromRequestBody(definition("purchase-approval", "采购审批更新", "finance")).get();
      assertTrue(service.update("purchase-approval", updated).isSuccess());
      assertEquals("采购审批更新", Json.tryToBean(JsonNode.class,
          vcsManager.read("workflow/purchase-approval.workflow.json")).get().path("title").asText());

      assertTrue(service.rollback("purchase-approval").isSuccess());

      ApprovalWorkflowDefinitionAsset one = service.getOne("purchase-approval").get();
      JsonNode metadata = Json.tryToBean(JsonNode.class,
          vcsManager.read("workflow/purchase-approval.workflow.json")).get();
      assertEquals("purchase-approval", one.getName());
      assertEquals("采购审批", one.getTitle());
      assertEquals("", one.getCategory());
      assertEquals(Integer.valueOf(1), one.getVersion());
      assertEquals("采购审批", metadata.path("title").asText());
      assertEquals(1, metadata.path("version").asInt());
    } finally {
      DevAppHolder.removeAppName();
    }
  }

  private static ApprovalWorkflowDefinitionDevelopService service(FakeVCSManager vcsManager) {
    return new TestableApprovalWorkflowDefinitionDevelopService(vcsManager);
  }

  private static String definition(String id, String name, String category) {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 1,",
        "  \"name\": \"" + id + "\",",
        "  \"title\": \"" + name + "\",",
        "  \"description\": \"\",",
        "  \"version\": 1,",
        "  \"enabled\": true,",
        "  \"form\": {",
        "    \"formType\": \"businessModel\",",
        "    \"formId\": \"purchaseRequest\"",
        "  },",
        "  \"nodes\": [",
        "    {\"type\": \"StartNode\", \"name\": \"发起节点\", \"id\": \"" + id + "-start\"},",
        "    {\"type\": \"EndNode\", \"name\": \"结束\", \"id\": \"" + id + "-end\"}",
        "  ],",
        "  \"edges\": [",
        "    {\"id\": \"" + id + "-edge\", \"type\": \"SEQUENCE\", \"sourceId\": \"" + id + "-start\", \"targetId\": \"" + id + "-end\"}",
        "  ]",
        "}");
  }

  private static class TestableApprovalWorkflowDefinitionDevelopService
      extends ApprovalWorkflowDefinitionDevelopService {
    private TestableApprovalWorkflowDefinitionDevelopService(FakeVCSManager vcsManager) {
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
  }

  private static class FakeVCSManager implements VCSManager {
    private final Map<String, String> files = new HashMap<>();
    private Map<String, String> snapshot = new HashMap<>();

    private String read(String path) {
      return files.get(path);
    }

    private boolean exists(String path) {
      return files.containsKey(path);
    }

    private void snapshot() {
      snapshot = new HashMap<>(files);
    }

    private void rollback(String path) {
      if (snapshot.containsKey(path)) {
        files.put(path, snapshot.get(path));
      } else {
        files.remove(path);
      }
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
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSAction.BatchAction batchOperation() {
      return new FakeBatchAction(files);
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String fileExtension) {
      return files.keySet().stream()
          .filter(path -> path.startsWith(relativeParentPath + "/"))
          .filter(path -> path.endsWith(fileExtension))
          .sorted()
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
      return Try.success(tagName);
    }

    @Override
    public Try<String> createTag(String tagName, String revision, String message) {
      return Try.success(tagName);
    }

    @Override
    public Try<Boolean> deleteTag(String tagName) {
      return Try.success(true);
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
      return Try.success(Collections.emptyList());
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
      return path.substring(path.lastIndexOf('/') + 1);
    }

    @Override
    public String getContent() {
      return vcsManager.files.get(path);
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(getBytesArray());
    }

    @Override
    public byte[] getBytesArray() {
      return getContent() == null ? new byte[0] : getContent().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Long getSize() {
      return Long.valueOf(getBytesArray().length);
    }

    @Override
    public LocalDateTime getCreationTime() {
      return LocalDateTime.now();
    }

    @Override
    public LocalDateTime getLastCommittedTime() {
      return LocalDateTime.now();
    }

    @Override
    public LocalDateTime getLastModifiedTime() {
      return LocalDateTime.now();
    }

    @Override
    public VersionState getVersionState() {
      return VersionState.NOTCHANGED;
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
      return write(new String(content, StandardCharsets.UTF_8));
    }

    @Override
    public Try<Boolean> write(InputStream content) {
      return Try.of(() -> {
        byte[] bytes = new byte[content.available()];
        int ignored = content.read(bytes);
        vcsManager.files.put(path, new String(bytes, StandardCharsets.UTF_8));
        return true;
      });
    }

    @Override
    public Try<VCSFile> move(String newPath) {
      vcsManager.files.put(newPath, vcsManager.files.remove(path));
      return Try.success(new FakeVCSFile(newPath, vcsManager));
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
      vcsManager.rollback(path);
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
      return Try.success(null);
    }

    @Override
    public Try<FileDiff> diffWithWorkingCopy() {
      return Try.success(null);
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
      return true;
    }
  }

  private static class FakeBatchAction implements VCSAction.BatchAction {
    private final Map<String, String> files;
    private final List<Runnable> validations = new java.util.ArrayList<>();
    private final List<Runnable> operations = new ArrayList<>();

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
        operations.forEach(Runnable::run);
        return true;
      });
    }

    @Override
    public Try<Boolean> commit(String message) {
      return write(false);
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
      operations.add(() -> files.put(relativePath, content));
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, byte[] content) {
      return create(relativePath, new String(content, StandardCharsets.UTF_8));
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, InputStream content) {
      operations.add(() -> files.put(relativePath, read(content)));
      return this;
    }

    @Override
    public VCSAction.BatchAction remove(String relativePath) {
      operations.add(() -> files.remove(relativePath));
      return this;
    }

    @Override
    public VCSAction.BatchAction remove(Collection<String> relativePaths) {
      relativePaths.forEach(this::remove);
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, String content) {
      operations.add(() -> files.put(relativePath, content));
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, byte[] content) {
      return update(relativePath, new String(content, StandardCharsets.UTF_8));
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, InputStream content) {
      operations.add(() -> files.put(relativePath, read(content)));
      return this;
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath) {
      operations.add(() -> files.put(newRelativePath, files.remove(relativePath)));
      return this;
    }

    @Override
    public VCSAction.BatchAction move(
        String relativePath,
        String newRelativePath,
        java.util.function.Function<String, String> updateContent) {
      operations.add(() -> files.put(newRelativePath, updateContent.apply(files.remove(relativePath))));
      return this;
    }

    private String read(InputStream content) {
      return Try.of(() -> {
        byte[] bytes = new byte[content.available()];
        int ignored = content.read(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
      }).get();
    }
  }
}
