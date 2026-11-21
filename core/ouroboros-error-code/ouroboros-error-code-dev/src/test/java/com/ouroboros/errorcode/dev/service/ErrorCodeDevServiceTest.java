package com.ouroboros.errorcode.dev.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.errorcode.dev.inspector.ErrorCodeInspector;
import com.ouroboros.errorcode.dev.model.ErrorCodeDefinitionModel;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction;
import com.ouroboros.vcs.file.FileDiff;
import com.ouroboros.vcs.file.FileOperation;
import com.ouroboros.vcs.file.FileRevision;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.FileDescription;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.versioning.Tag;
import com.ouroboros.vcs.versioning.VersionState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCodeDevServiceTest {

  @AfterEach
  void cleanup() {
    DevAppHolder.removeAppName();
  }

  @Test
  void buildModelFilePath_usesErrorCodeDirectory() {
    var service = service(Collections.emptyList());

    assertEquals("error-codes.error.json", service.exposeModelFilePath("40301"));
  }

  @Test
  void validateModel_rejectsBlankTemplateAndDuplicateBizCode() {
    var service = service(Collections.singletonList(file(
        "error-codes.error.json",
        modelsJson(modelJson(40101, "未认证", "请重新登录", "AUTH-001"))
    )));

    var blankTemplateModel = model(40301, "登录过期", "", "AUTH-002");
    var duplicateBizCodeModel = model(40302, "登录失效", "请重新登录", "AUTH-001");

    assertTrue(service.validateForTest(blankTemplateModel).isFailure());
    assertTrue(service.validateForTest(duplicateBizCodeModel).isFailure());
  }

  @Test
  void inspector_exposesCodeAndTitleInWorkspaceListing() {
    var service = service(Collections.singletonList(file(
        "error-codes.error.json",
        modelsJson(modelJson(40301, "登录过期", "请重新登录", "AUTH-001"))
    )));

    FileDescription description = new ErrorCodeInspector.Proxy()
        .inspect(file("error-codes.error.json", modelsJson(modelJson(40301, "登录过期", "请重新登录", "AUTH-001"))))
        .orElseThrow(() -> new AssertionError("description missing"));

    assertEquals("error-codes", description.getName());
    assertEquals("错误码", description.getTitle());
    assertEquals("error-code", description.getType());
  }

  private static TestableErrorCodeDevService service(List<VCSFile> files) {
    DevAppHolder.setAppName("test-app");
    return new TestableErrorCodeDevService()
        .withVcsManagerCenter(new TestVCSManagerCenter(new TestVCSManager(files)));
  }

  private static ErrorCodeDefinitionModel model(int code, String title, String template, String bizCode) {
    return new ErrorCodeDefinitionModel()
        .setCode(code)
        .setTitle(title)
        .setDefaultMessageTemplate(template)
        .setBizCode(bizCode)
        .setEnabled(true);
  }

  private static String modelJson(int code, String title, String template, String bizCode) {
    return "{\n"
        + "  \"code\": " + code + ",\n"
        + "  \"title\": \"" + title + "\",\n"
        + "  \"defaultMessageTemplate\": \"" + template + "\",\n"
        + "  \"bizCode\": \"" + bizCode + "\",\n"
        + "  \"enabled\": true\n"
        + "}";
  }

  private static String modelsJson(String... models) {
    return "[" + String.join(",", models) + "]";
  }

  private static TestFile file(String path, String content) {
    return new TestFile(path, content);
  }

  private static class TestableErrorCodeDevService extends ErrorCodeDevService {
    private TestableErrorCodeDevService withVcsManagerCenter(VCSManagerCenter vcsManagerCenter) {
      this.vcsManagerCenter = vcsManagerCenter;
      return this;
    }

    private String exposeModelFilePath(String fullName) {
      return buildModelFilePath(fullName);
    }

    private Try<Void> validateForTest(ErrorCodeDefinitionModel model) {
      return validateModel(model);
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
    private final List<VCSFile> files;

    private TestVCSManager(List<VCSFile> files) {
      this.files = files;
    }

    @Override
    public void destroy() {
    }

    @Override
    public VCSFile getFile(String relativePath) {
      return files.stream()
          .filter(file -> file.getPath().equals(relativePath))
          .findFirst()
          .orElse(file(relativePath, ""));
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String fileExtension) {
      return files.stream()
          .filter(file -> file.getPath().startsWith(relativeParentPath))
          .filter(file -> file.getPath().endsWith(fileExtension));
    }

    @Override
    public Try<Void> lock(String relativePath, boolean force) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Void> lock(java.util.Collection<String> relativePaths, boolean force) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Void> unlock(String relativePath, boolean force) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Void> unlock(java.util.Collection<String> relativePaths, boolean force) {
      throw new UnsupportedOperationException();
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
      throw new UnsupportedOperationException();
    }

    @Override
    public List<ChangeLog> getChangeLogs(String relativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<ChangeLog> getChangeLogs(String relativePath, int skip, int limit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<ChangeLog> getChangeLogs(java.util.Collection<String> relativePaths) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<ChangeLog> getChangeLogs(java.util.Collection<String> relativePaths, int skip, int limit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<VCSFile> getUncommittedFiles(String relativePath) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSFile getFile(String relativePath, String revision) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSFile getFileFromBranch(String relativePath, String branchName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public VCSFile getFileFromTag(String relativePath, String tagName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<VCSFile> getFileList(String relativeParentPath, String revision) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<VCSFile> getFileListFromBranch(String relativeParentPath, String branchName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<VCSFile> getFileListFromTag(String relativeParentPath, String tagName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String revision, String fileExtension) {
      throw new UnsupportedOperationException();
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
  }

  private static class TestFile implements VCSFile {
    private final String path;
    private final String content;

    private TestFile(String path, String content) {
      this.path = path;
      this.content = content;
    }

    @Override
    public boolean exists() {
      return content != null && !content.isEmpty();
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
      return content;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public byte[] getBytesArray() {
      return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public Long getSize() {
      return (long) content.length();
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
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Void> unlock(boolean forceUnlock) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> write(String content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> write(byte[] content) {
      throw new UnsupportedOperationException();
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
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> commit(String message) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Boolean> rollback(boolean forceRollback) {
      throw new UnsupportedOperationException();
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
