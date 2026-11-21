package com.ouroboros.resource.dev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.resource.dev.model.ResourceModel;
import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.util.Json;
import com.ouroboros.web.TreeSelectOption;
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

import io.vavr.control.Try;

class ResourceDevServiceTest {

  @AfterEach
  void cleanup() {
    DevAppHolder.removeAppName();
  }

  @Test
  void createWritesMetaAlongsideRealFileInFrontendDirectory() {
    TestVCSManager vcsManager = new TestVCSManager(Collections.emptyMap());
    TestableResourceDevService service = service(vcsManager);

    ResourceModel model = new ResourceModel()
        .setScope(ResourceScope.FRONTEND)
        .setKey("templates/invoice.docx");

    Try<ResourceModel> result = service.create(model, "init resource");

    assertTrue(result.isSuccess(), result::toString);
    assertTrue(vcsManager.getFile("resources/frontend/templates/invoice.docx").exists());
    assertTrue(vcsManager.getFile("resources/frontend/templates/invoice.docx.resource.json").exists());
    ResourceModel persisted = service.getOne("frontend/templates/invoice.docx").orElseThrow(AssertionError::new);
    assertEquals(ResourceScope.FRONTEND, persisted.getScope());
    assertEquals("templates/invoice.docx", persisted.getKey());
  }

  @Test
  void searchSortsByScopeAndKey() {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("resources/backend/sql/init.sql.resource.json",
        "{\"scope\":\"backend\",\"key\":\"sql/init.sql\"}");
    files.put("resources/frontend/images/logo.svg.resource.json",
        "{\"scope\":\"frontend\",\"key\":\"images/logo.svg\"}");
    files.put("resources/backend/config/app.yml.resource.json",
        "{\"scope\":\"backend\",\"key\":\"config/app.yml\"}");

    TestableResourceDevService service = service(new TestVCSManager(files));

    @SuppressWarnings("unchecked")
    List<ModelFileMetaWrapper<ResourceModel>> result =
        (List<ModelFileMetaWrapper<ResourceModel>>) (List<?>) service.search(Collections.emptyMap());

    List<String> scopedPaths = result.stream()
        .map(wrapper -> wrapper.getModel().getScopedPath())
        .collect(Collectors.toList());

    assertEquals(Arrays.asList(
        "backend/config/app.yml",
        "backend/sql/init.sql",
        "frontend/images/logo.svg"
    ), scopedPaths);
  }

  @Test
  void searchSupportsDirectoryFilterAndDirectoryTree() {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("resources/frontend/images/logo.svg.resource.json",
        "{\"scope\":\"frontend\",\"key\":\"images/logo.svg\"}");
    files.put("resources/frontend/templates/mail/welcome.html.resource.json",
        "{\"scope\":\"frontend\",\"key\":\"templates/mail/welcome.html\"}");
    files.put("resources/frontend/templates/mail/reset.html.resource.json",
        "{\"scope\":\"frontend\",\"key\":\"templates/mail/reset.html\"}");
    files.put("resources/frontend/templates/invoice.docx.resource.json",
        "{\"scope\":\"frontend\",\"key\":\"templates/invoice.docx\"}");

    TestableResourceDevService service = service(new TestVCSManager(files));

    Map<String, Object> searchParam = new LinkedHashMap<>();
    searchParam.put("scope", "frontend");
    searchParam.put("directory", "templates/mail");
    @SuppressWarnings("unchecked")
    List<ModelFileMetaWrapper<ResourceModel>> filtered =
        (List<ModelFileMetaWrapper<ResourceModel>>) (List<?>) service.search(searchParam);

    assertEquals(Arrays.asList(
        "frontend/templates/mail/reset.html",
        "frontend/templates/mail/welcome.html"
    ), filtered.stream()
        .map(wrapper -> wrapper.getModel().getScopedPath())
        .collect(Collectors.toList()));

    List<TreeSelectOption> directories = service.listDirectories(ResourceScope.FRONTEND);

    assertEquals(Arrays.asList("images", "templates"),
        directories.stream().map(TreeSelectOption::getValue).collect(Collectors.toList()));
    assertEquals(Arrays.asList("templates/mail"),
        directories.get(1).getChildren().stream()
            .map(TreeSelectOption::getValue)
            .collect(Collectors.toList()));
  }

  @Test
  void createDirectoryAddsEmptyDirectoryToTree() {
    TestVCSManager vcsManager = new TestVCSManager(Collections.emptyMap());
    TestableResourceDevService service = service(vcsManager);

    Try<Void> result = service.createDirectory(ResourceScope.FRONTEND, "templates/mail");

    assertTrue(result.isSuccess());
    assertTrue(vcsManager.getFile("resources/frontend/templates/mail/__resource_directory__.json").exists());
    assertEquals(Arrays.asList("templates"),
        service.listDirectories(ResourceScope.FRONTEND).stream()
            .map(TreeSelectOption::getValue)
            .collect(Collectors.toList()));
    assertEquals(Arrays.asList("templates/mail"),
        service.listDirectories(ResourceScope.FRONTEND).get(0).getChildren().stream()
            .map(TreeSelectOption::getValue)
            .collect(Collectors.toList()));
  }

  @Test
  void statusReflectsActualResourceFileChangesInsteadOfOnlyMetadataFile() {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("resources/frontend/templates/invoice.docx.resource.json",
        "{\"scope\":\"frontend\",\"key\":\"templates/invoice.docx\"}");
    files.put("resources/frontend/templates/invoice.docx", "old-content");

    TestVCSManager vcsManager = new TestVCSManager(files);
    TestableResourceDevService service = service(vcsManager);

    vcsManager.writeWorkingFile("resources/frontend/templates/invoice.docx", "new-content");

    String json = Json.toJsonString(service.getStatus("frontend/templates/invoice.docx").get());

    assertTrue(json.contains("\"versionState\":\"CHANGED\""));
  }

  @Test
  void commitIncludesActualResourceFileAlongsideMetadata() {
    TestVCSManager vcsManager = new TestVCSManager(Collections.emptyMap());
    TestableResourceDevService service = service(vcsManager);

    service.create(new ResourceModel()
        .setScope(ResourceScope.FRONTEND)
        .setKey("templates/invoice.docx"), "init resource");
    service.uploadResourceFile("frontend/templates/invoice.docx",
        new ByteArrayInputStream("invoice-content".getBytes(StandardCharsets.UTF_8)));
    vcsManager.clearLastCommittedPaths();

    Try<Boolean> result = service.commit("frontend/templates/invoice.docx", "commit resource");

    assertTrue(result.isSuccess());
    assertIterableEquals(Arrays.asList(
        "resources/frontend/templates/invoice.docx",
        "resources/frontend/templates/invoice.docx.resource.json"
    ), vcsManager.getLastCommittedPaths());
  }

  @Test
  void rollbackRestoresActualResourceFileAlongsideMetadata() {
    TestVCSManager vcsManager = new TestVCSManager(Collections.emptyMap());
    TestableResourceDevService service = service(vcsManager);

    service.create(new ResourceModel()
        .setScope(ResourceScope.FRONTEND)
        .setKey("templates/invoice.docx"), "init resource");
    service.uploadResourceFile("frontend/templates/invoice.docx",
        new ByteArrayInputStream("invoice-content".getBytes(StandardCharsets.UTF_8)),
        "text/plain",
        "invoice.docx");

    Try<Boolean> result = service.rollback("frontend/templates/invoice.docx");

    assertTrue(result.isSuccess());
    assertEquals("",
        new String(vcsManager.getFile("resources/frontend/templates/invoice.docx").getBytesArray(),
            StandardCharsets.UTF_8));
  }

  @Test
  void changeLogQueriesActualResourceFileAlongsideMetadata() {
    TestVCSManager vcsManager = new TestVCSManager(Collections.emptyMap());
    TestableResourceDevService service = service(vcsManager);

    service.create(new ResourceModel()
        .setScope(ResourceScope.FRONTEND)
        .setKey("templates/invoice.docx"), "init resource");

    service.getAllChangeLogs("frontend/templates/invoice.docx");

    assertIterableEquals(Arrays.asList(
        "resources/frontend/templates/invoice.docx",
        "resources/frontend/templates/invoice.docx.resource.json"
    ), vcsManager.getLastChangeLogRequestPaths());
  }

  private static TestableResourceDevService service(TestVCSManager vcsManager) {
    DevAppHolder.setAppName("test-app");
    return new TestableResourceDevService()
        .withVcsManagerCenter(new TestVCSManagerCenter(vcsManager))
        .withPublisher(event -> {
        });
  }

  private static class TestableResourceDevService extends ResourceDevService {
    private TestableResourceDevService withVcsManagerCenter(VCSManagerCenter vcsManagerCenter) {
      this.vcsManagerCenter = vcsManagerCenter;
      return this;
    }

    private TestableResourceDevService withPublisher(ApplicationEventPublisher publisher) {
      this.publisher = publisher;
      return this;
    }
  }

  private static class TestVCSManagerCenter extends VCSManagerCenter {
    private final VCSManager vcsManager;

    private TestVCSManagerCenter(VCSManager vcsManager) {
      this.vcsManager = vcsManager;
    }

    @Override
    public VCSManager getVcsManager(String appName) {
      return vcsManager;
    }
  }

  private static class TestVCSManager implements VCSManager {
    private final Map<String, byte[]> files;
    private final Map<String, byte[]> committedFiles;
    private List<String> lastCommittedPaths = new ArrayList<>();
    private List<String> lastChangeLogRequestPaths = new ArrayList<>();

    private TestVCSManager(Map<String, String> initialFiles) {
      this.files = new LinkedHashMap<>();
      this.committedFiles = new LinkedHashMap<>();
      initialFiles.forEach((path, content) ->
          this.files.put(path, content.getBytes(StandardCharsets.UTF_8)));
      initialFiles.forEach((path, content) ->
          this.committedFiles.put(path, content.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void destroy() {
    }

    @Override
    public VCSFile getFile(String relativePath) {
      return new TestFile(relativePath, files, committedFiles);
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
      return new TestBatchAction(files, committedFiles, this);
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String fileExtension) {
      return files.keySet().stream()
          .filter(path -> path.startsWith(relativeParentPath))
          .filter(path -> fileExtension == null || path.endsWith(fileExtension))
          .sorted(Comparator.naturalOrder())
          .map(path -> new TestFile(path, files, committedFiles));
    }

    @Override
    public List<ChangeLog> getChangeLogs(String relativePath) {
      this.lastChangeLogRequestPaths = Collections.singletonList(relativePath);
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(String relativePath, int skip, int limit) {
      this.lastChangeLogRequestPaths = Collections.singletonList(relativePath);
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(Collection<String> relativePaths) {
      this.lastChangeLogRequestPaths = relativePaths.stream().sorted().collect(Collectors.toList());
      return Collections.emptyList();
    }

    @Override
    public List<ChangeLog> getChangeLogs(Collection<String> relativePaths, int skip, int limit) {
      this.lastChangeLogRequestPaths = relativePaths.stream().sorted().collect(Collectors.toList());
      return Collections.emptyList();
    }

    @Override
    public List<VCSFile> getUncommittedFiles(String relativePath) {
      return new ArrayList<>();
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
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<String> createTag(String tagName, String revision, String message) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> deleteTag(String tagName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<List<Tag>> getTagList() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<String> getCurrentBranch() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<List<String>> getBranchList() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<String> createBranch(String branchName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<String> createBranch(String branchName, String fromRevision) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> switchBranch(String branchName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<com.ouroboros.vcs.VCSConfig.RemoteConfig> getRemoteConfig() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> setRemoteConfig(com.ouroboros.vcs.VCSConfig.RemoteConfig remoteConfig) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> pull() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> push() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> isWorkspaceClean() {
      throw new UnsupportedOperationException();
    }

    private void writeWorkingFile(String path, String content) {
      this.files.put(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private void clearLastCommittedPaths() {
      this.lastCommittedPaths = new ArrayList<>();
    }

    private List<String> getLastCommittedPaths() {
      return lastCommittedPaths;
    }

    private List<String> getLastChangeLogRequestPaths() {
      return lastChangeLogRequestPaths;
    }
  }

  private static class TestBatchAction implements VCSAction.BatchAction {
    private final Map<String, byte[]> files;
    private final Map<String, byte[]> committedFiles;
    private final TestVCSManager vcsManager;
    private final List<Runnable> validations = new ArrayList<>();
    private final List<Runnable> operations = new ArrayList<>();
    private final Set<String> trackedPaths = new LinkedHashSet<>();

    private TestBatchAction(Map<String, byte[]> files,
                            Map<String, byte[]> committedFiles,
                            TestVCSManager vcsManager) {
      this.files = files;
      this.committedFiles = committedFiles;
      this.vcsManager = vcsManager;
    }

    @Override
    public VCSAction.BatchAction beforeWrite(Runnable validation) {
      validations.add(validation);
      return this;
    }

    @Override
    public VCSAction.BatchAction add(String relativePath) {
      trackedPaths.add(relativePath);
      return this;
    }

    @Override
    public VCSAction.BatchAction add(Collection<String> relativePaths) {
      trackedPaths.addAll(relativePaths);
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, String content) {
      trackedPaths.add(relativePath);
      operations.add(() -> files.put(relativePath, content.getBytes(StandardCharsets.UTF_8)));
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, byte[] content) {
      trackedPaths.add(relativePath);
      operations.add(() -> files.put(relativePath, content));
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, InputStream content) {
      trackedPaths.add(relativePath);
      operations.add(() -> files.put(relativePath, readBytes(content)));
      return this;
    }

    @Override
    public VCSAction.BatchAction remove(String relativePath) {
      trackedPaths.add(relativePath);
      operations.add(() -> files.remove(relativePath));
      return this;
    }

    @Override
    public VCSAction.BatchAction remove(Collection<String> relativePaths) {
      trackedPaths.addAll(relativePaths);
      operations.add(() -> relativePaths.forEach(files::remove));
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, String content) {
      trackedPaths.add(relativePath);
      operations.add(() -> files.put(relativePath, content.getBytes(StandardCharsets.UTF_8)));
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, byte[] content) {
      trackedPaths.add(relativePath);
      operations.add(() -> files.put(relativePath, content));
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, InputStream content) {
      trackedPaths.add(relativePath);
      operations.add(() -> files.put(relativePath, readBytes(content)));
      return this;
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath) {
      trackedPaths.add(relativePath);
      trackedPaths.add(newRelativePath);
      operations.add(() -> {
        byte[] content = files.remove(relativePath);
        if (content != null) {
          files.put(newRelativePath, content);
        }
      });
      return this;
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath,
                                      java.util.function.Function<String, String> updateContent) {
      trackedPaths.add(relativePath);
      trackedPaths.add(newRelativePath);
      operations.add(() -> {
        byte[] content = files.remove(relativePath);
        if (content != null) {
          String updated = updateContent.apply(new String(content, StandardCharsets.UTF_8));
          files.put(newRelativePath, updated.getBytes(StandardCharsets.UTF_8));
        }
      });
      return this;
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
    public Try<Boolean> write(boolean forceWrite) {
      validations.forEach(Runnable::run);
      validations.clear();
      operations.forEach(Runnable::run);
      operations.clear();
      return Try.success(true);
    }

    @Override
    public Try<Boolean> commit(String message) {
      write(false);
      committedFiles.clear();
      files.forEach((path, content) -> committedFiles.put(path, Arrays.copyOf(content, content.length)));
      vcsManager.lastCommittedPaths = trackedPaths.stream().sorted().collect(Collectors.toList());
      trackedPaths.clear();
      return Try.success(true);
    }

    private static byte[] readBytes(InputStream inputStream) {
      try {
        byte[] buffer = new byte[1024];
        int length;
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        while ((length = inputStream.read(buffer)) >= 0) {
          outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class TestFile implements VCSFile {
    private final String path;
    private final Map<String, byte[]> files;
    private final Map<String, byte[]> committedFiles;

    private TestFile(String path, Map<String, byte[]> files) {
      this(path, files, Collections.emptyMap());
    }

    private TestFile(String path, Map<String, byte[]> files, Map<String, byte[]> committedFiles) {
      this.path = path;
      this.files = files;
      this.committedFiles = committedFiles;
    }

    @Override
    public boolean exists() {
      return files.containsKey(path);
    }

    @Override
    public String getPath() {
      return path;
    }

    @Override
    public String getFileName() {
      int slashIndex = path.lastIndexOf('/');
      return slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
    }

    @Override
    public String getContent() {
      return new String(files.getOrDefault(path, new byte[0]), StandardCharsets.UTF_8);
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(getBytesArray());
    }

    @Override
    public byte[] getBytesArray() {
      return files.getOrDefault(path, new byte[0]);
    }

    @Override
    public Long getSize() {
      return (long) getBytesArray().length;
    }

    @Override
    public LocalDateTime getCreationTime() {
      return LocalDateTime.of(2026, 3, 14, 0, 0);
    }

    @Override
    public LocalDateTime getLastCommittedTime() {
      return getCreationTime();
    }

    @Override
    public LocalDateTime getLastModifiedTime() {
      return getCreationTime();
    }

    @Override
    public VersionState getVersionState() {
      boolean existsInWorkingTree = files.containsKey(path);
      boolean existsInCommittedTree = committedFiles.containsKey(path);
      if (existsInWorkingTree != existsInCommittedTree) {
        return VersionState.CHANGED;
      }
      if (!existsInWorkingTree) {
        return VersionState.NOTCHANGED;
      }
      return Arrays.equals(files.get(path), committedFiles.get(path))
          ? VersionState.NOTCHANGED
          : VersionState.CHANGED;
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
      files.put(path, content.getBytes(StandardCharsets.UTF_8));
      return Try.success(true);
    }

    @Override
    public Try<Boolean> write(byte[] content) {
      files.put(path, content);
      return Try.success(true);
    }

    @Override
    public Try<Boolean> write(InputStream content) {
      files.put(path, TestBatchAction.readBytes(content));
      return Try.success(true);
    }

    @Override
    public Try<VCSFile> move(String newPath) {
      byte[] content = files.remove(path);
      if (content != null) {
        files.put(newPath, content);
      }
      return Try.success(new TestFile(newPath, files, committedFiles));
    }

    @Override
    public Try<VCSFile> remove() {
      files.remove(path);
      return Try.success(this);
    }

    @Override
    public Try<Boolean> commit(String message) {
      if (files.containsKey(path)) {
        committedFiles.put(path, Arrays.copyOf(files.get(path), files.get(path).length));
      } else {
        committedFiles.remove(path);
      }
      return Try.success(true);
    }

    @Override
    public Try<Boolean> rollback(boolean forceRollback) {
      if (committedFiles.containsKey(path)) {
        files.put(path, Arrays.copyOf(committedFiles.get(path), committedFiles.get(path).length));
      } else {
        files.remove(path);
      }
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
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<FileDiff> diffWithWorkingCopy() {
      throw new UnsupportedOperationException();
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
}
