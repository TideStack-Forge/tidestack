package com.ouroboros.i18n.dev.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataProvider;
import com.ouroboros.i18n.dev.inspector.I18nLocaleInspector;
import com.ouroboros.i18n.dev.dto.I18nLocaleExportResult;
import com.ouroboros.i18n.dev.dto.I18nLocaleSyncResult;
import com.ouroboros.i18n.dev.model.I18nLocaleModel;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.util.Json;
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
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nLocaleDevServiceTest {
  private org.springframework.context.support.GenericApplicationContext inspectorContext;

  @org.junit.jupiter.api.BeforeEach
  void openInspectorContext() {
    inspectorContext = new org.springframework.context.support.GenericApplicationContext();
    inspectorContext.registerBean(com.ouroboros.common.app.util.SpringContextUtil.class);
    inspectorContext.refresh();
  }

  @org.junit.jupiter.api.AfterEach
  void closeInspectorContext() {
    inspectorContext.close();
  }


  @AfterEach
  void cleanup() {
    DevAppHolder.removeAppName();
  }

  @Test
  void buildModelFilePath_usesI18nDirectory() {
    var service = service(Collections.emptyList());

    assertEquals("i18n/zh-CN.i18n.json", service.exposeModelFilePath("zh-CN"));
  }

  @Test
  void validateModel_rejectsBlankLocaleAndName() {
    var service = service(Collections.emptyList());

    assertTrue(service.validateForTest(model("", "简体中文", "1.0.0")).isFailure());
    assertTrue(service.validateForTest(model("zh-CN", "", "1.0.0")).isFailure());
  }

  @Test
  void search_matchesLocaleNameAndVersion() {
    var service = service(Arrays.asList(
        file("i18n/zh-CN.i18n.json", modelJson("zh-CN", "简体中文", "1.0.0", 2)),
        file("i18n/en-US.i18n.json", modelJson("en-US", "English", "2.0.0", 1))
    ));

    var localeMatches = service.search(Collections.singletonMap("keyword", "zh-CN"));
    var versionMatches = service.search(Collections.singletonMap("keyword", "2.0.0"));

    assertEquals(1, localeMatches.size());
    assertEquals("zh-CN", ((ModelFileMetaWrapper<I18nLocaleModel>) localeMatches.get(0)).getModel().getLocale());
    assertEquals(1, versionMatches.size());
    assertEquals("en-US", ((ModelFileMetaWrapper<I18nLocaleModel>) versionMatches.get(0)).getModel().getLocale());
  }

  @Test
  void inspector_exposesLocaleTitleAndMessageCount() {
    var service = service(Collections.singletonList(file(
        "i18n/zh-CN.i18n.json",
        modelJson("zh-CN", "简体中文", "1.0.0", 2)
    )));
    inspectorContext.getBeanFactory().registerSingleton("I18nLocaleInspector", new I18nLocaleInspector(service));
    FileDescription description = new I18nLocaleInspector.Proxy()
        .inspect(file("i18n/zh-CN.i18n.json", modelJson("zh-CN", "简体中文", "1.0.0", 2)))
        .orElseThrow(() -> new AssertionError("description missing"));

    assertEquals("zh-CN", description.getName());
    assertEquals("简体中文", description.getTitle());
    assertEquals("i18n-locale", description.getType());
    assertTrue(description.getDescription().contains("2"));
  }

  @Test
  void exportTemplate_collectsWorkspaceTextsFromMetadata() {
    var service = service(
        Collections.emptyList(),
        Arrays.asList(
            metadataProvider("ui-model", metadata("/demo", "演示页面", map(
                "path", "/demo",
                "title", "演示页面",
                "description", "页面说明"
            ))),
            metadataProvider("ui-schema", metadata("demo.page", "演示页面", map(
                "type", "page",
                "title", "演示页标题",
                "body", Collections.singletonList(map(
                    "type", "input-text",
                    "name", "username",
                    "label", "用户名",
                    "placeholder", "请输入用户名"
                ))
            )))
        )
    );

    I18nLocaleExportResult exportResult = service.exportTemplateForTest().get();
    I18nLocaleModel template = service.parseForTest(exportResult.getContent()).get();

    assertEquals("translation-template.json", exportResult.getFilename());
    assertEquals("template", template.getLocale());
    assertEquals("演示页面", template.getMessages().get("/demo").get("title"));
    assertEquals("页面说明", template.getMessages().get("/demo").get("description"));
    assertEquals("演示页标题", template.getMessages().get("demo.page").get("page.title"));
    assertEquals("用户名", template.getMessages().get("demo.page").get("page.body.username.label"));
    assertEquals("请输入用户名", template.getMessages().get("demo.page").get("page.body.username.placeholder"));
  }

  @Test
  void exportTemplate_usesExplicitLocaleKeysWithoutChangingExistingKeys() {
    var service = service(
        Collections.emptyList(),
        Collections.singletonList(
            metadataProvider("ui-schema", metadata("demo.change-password", "修改密码", map(
                "type", "form",
                "body", Collections.singletonList(map(
                    "type", "input-password",
                    "name", "credentials",
                    "label", "旧密码",
                    "localeKeys", map(
                        "label", "changePassword.oldPassword",
                        "validationErrors.minLength", "changePassword.validation.oldPasswordRequired"
                    ),
                    "validationErrors", map(
                        "minLength", "请输入旧密码"
                    )
                ))
            )))
        )
    );

    I18nLocaleExportResult exportResult = service.exportTemplateForTest().get();
    I18nLocaleModel template = service.parseForTest(exportResult.getContent()).get();
    Map<String, String> messages = template.getMessages().get("demo.change-password");

    assertEquals("旧密码", messages.get("changePassword.oldPassword"));
    assertEquals("请输入旧密码", messages.get("changePassword.validation.oldPasswordRequired"));
    assertFalse(messages.containsKey("page.body.credentials.label"));
    assertFalse(messages.containsKey("page.body.credentials.validationErrors.minLength"));
  }

  @Test
  void exportLocale_returnsStoredFileContent() {
    var service = service(Collections.singletonList(file(
        "i18n/en-US.i18n.json",
        modelJson("en-US", "English", "2.0.0", 1)
    )));

    I18nLocaleExportResult exportResult = service.exportLocaleForTest("en-US").get();
    I18nLocaleModel exported = service.parseForTest(exportResult.getContent()).get();

    assertEquals("en-US.i18n.json", exportResult.getFilename());
    assertEquals("en-US", exported.getLocale());
    assertEquals("English", exported.getName());
  }

  @Test
  void importLocale_preservesExistingFlagsButReplacesMessages() {
    var service = service(Collections.singletonList(file(
        "i18n/en-US.i18n.json",
        localeJson("en-US", "English", "2.0.0", false, true, map(
            "common", map("greeting", "Hello")
        ))
    )));

    I18nLocaleModel imported = new I18nLocaleModel()
        .setLocale("en-US")
        .setName("English Updated")
        .setVersion("3.0.0")
        .setEnabled(true)
        .setIsDefault(false)
        .setMessages(map("common", map("greeting", "Hi")));

    I18nLocaleModel result = service.importLocaleForTest(imported).get();

    assertEquals("English Updated", result.getName());
    assertFalse(result.getEnabled());
    assertTrue(result.getIsDefault());
    assertEquals("Hi", result.getMessages().get("common").get("greeting"));
  }

  @Test
  void syncFromMetadata_addsMissingKeysWithoutOverwritingExistingTranslations() {
    var service = service(
        Collections.singletonList(file(
            "i18n/zh-CN.i18n.json",
            localeJson("zh-CN", "简体中文", "1.0.0", true, false, map(
                "demo.page", map(
                    "page.title", "已有标题"
                ),
                "manual", map(
                    "custom.key", "手工文案"
                )
            ))
        )),
        Arrays.asList(
            metadataProvider("ui-schema", metadata("demo.page", "演示页", map(
                "type", "page",
                "title", "模板标题",
                "body", Collections.singletonList(map(
                    "type", "input-text",
                    "name", "username",
                    "label", "用户名"
                ))
            )))
        )
    );

    I18nLocaleSyncResult result = service.syncFromMetadataForTest("zh-CN", false).get();
    I18nLocaleModel locale = service.getOne("zh-CN")
        .orElseThrow(() -> new AssertionError("zh-CN locale should exist"));

    assertEquals(1, result.getAdded());
    assertEquals(0, result.getRemoved());
    assertEquals("已有标题", locale.getMessages().get("demo.page").get("page.title"));
    assertEquals("用户名", locale.getMessages().get("demo.page").get("page.body.username.label"));
    assertEquals("手工文案", locale.getMessages().get("manual").get("custom.key"));
  }

  @Test
  void syncFromMetadata_removeOrphanedRemovesStaleKeysButKeepsManualNamespaces() {
    var service = service(
        Collections.singletonList(file(
            "i18n/zh-CN.i18n.json",
            localeJson("zh-CN", "简体中文", "1.0.0", true, false, map(
                "demo.page", map(
                    "page.title", "已有标题",
                    "page.legacy", "旧字段"
                ),
                "manual", map(
                    "custom.key", "手工文案"
                )
            ))
        )),
        Arrays.asList(
            metadataProvider("ui-schema", metadata("demo.page", "演示页", map(
                "type", "page",
                "title", "模板标题"
            )))
        )
    );

    I18nLocaleSyncResult result = service.syncFromMetadataForTest("zh-CN", true).get();
    I18nLocaleModel locale = service.getOne("zh-CN")
        .orElseThrow(() -> new AssertionError("zh-CN locale should exist"));

    assertEquals(0, result.getAdded());
    assertEquals(1, result.getRemoved());
    assertNotNull(locale.getMessages().get("demo.page"));
    assertFalse(locale.getMessages().get("demo.page").containsKey("page.legacy"));
    assertEquals("手工文案", locale.getMessages().get("manual").get("custom.key"));
  }

  private static TestableI18nLocaleDevService service(List<VCSFile> files) {
    return service(files, Collections.emptyList());
  }

  private static TestableI18nLocaleDevService service(List<VCSFile> files, List<MetadataProvider> metadataProviders) {
    DevAppHolder.setAppName("test-app");
    return new TestableI18nLocaleDevService()
        .withVcsManagerCenter(new TestVCSManagerCenter(new TestVCSManager(files)))
        .withNoopPublisher()
        .withMetadataProviders(metadataProviders);
  }

  private static I18nLocaleModel model(String locale, String name, String version) {
    return new I18nLocaleModel()
        .setLocale(locale)
        .setName(name)
        .setVersion(version)
        .setEnabled(true)
        .setIsDefault(false)
        .setTimestamp(1710384000000L);
  }

  private static String modelJson(String locale, String name, String version, int messageCount) {
    return localeJson(locale, name, version, true, false, buildMessages(messageCount));
  }

  private static String localeJson(String locale, String name, String version, boolean enabled, boolean isDefault,
                                   Map<String, Map<String, String>> messages) {
    StringBuilder builder = new StringBuilder();
    builder.append("{\n")
        .append("  \"locale\": \"").append(locale).append("\",\n")
        .append("  \"name\": \"").append(name).append("\",\n")
        .append("  \"version\": \"").append(version).append("\",\n")
        .append("  \"enabled\": ").append(enabled).append(",\n")
        .append("  \"isDefault\": ").append(isDefault).append(",\n")
        .append("  \"timestamp\": 1710384000000,\n")
        .append("  \"direction\": \"LTR\",\n")
        .append("  \"messages\": ")
        .append(Json.toPrettyJsonString(messages))
        .append("\n")
        .append("}");
    return builder.toString();
  }

  private static Map<String, Map<String, String>> buildMessages(int messageCount) {
    Map<String, Map<String, String>> messages = new LinkedHashMap<>();
    Map<String, String> menu = new LinkedHashMap<>();
    for (int i = 0; i < messageCount; i++) {
      menu.put("system.key" + i, "值" + i);
    }
    messages.put("menu", menu);
    return messages;
  }

  private static TestFile file(String path, String content) {
    return new TestFile(path, content);
  }

  private static Metadata metadata(String name, String label, Map<String, Object> details) {
    Metadata metadata = new Metadata();
    metadata.setName(name);
    metadata.setLabel(label);
    metadata.setDetails(details);
    return metadata;
  }

  private static MetadataProvider metadataProvider(String moduleName, Metadata... metadata) {
    List<Metadata> metadataList = Arrays.asList(metadata);
    return (appName, queryModuleName) -> moduleName.equals(queryModuleName) ? metadataList : Collections.emptyList();
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Map<K, V> map(Object... values) {
    Map<K, V> map = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i += 2) {
      map.put((K) values[i], (V) values[i + 1]);
    }
    return map;
  }

  private static class TestableI18nLocaleDevService extends I18nLocaleDevService {
    private TestableI18nLocaleDevService withVcsManagerCenter(VCSManagerCenter vcsManagerCenter) {
      this.vcsManagerCenter = vcsManagerCenter;
      return this;
    }

    private TestableI18nLocaleDevService withNoopPublisher() {
      this.publisher = event -> {
      };
      return this;
    }

    private TestableI18nLocaleDevService withMetadataProviders(List<MetadataProvider> metadataProviders) {
      this.setMetadataProvidersForTest(metadataProviders);
      return this;
    }

    private String exposeModelFilePath(String fullName) {
      return buildModelFilePath(fullName);
    }

    private Try<Void> validateForTest(I18nLocaleModel model) {
      return validateModel(model);
    }

    private Try<I18nLocaleExportResult> exportTemplateForTest() {
      return exportTemplate();
    }

    private Try<I18nLocaleExportResult> exportLocaleForTest(String locale) {
      return exportLocale(locale);
    }

    private Try<I18nLocaleModel> importLocaleForTest(I18nLocaleModel model) {
      return importLocale(model);
    }

    private Try<I18nLocaleSyncResult> syncFromMetadataForTest(String locale, boolean removeOrphaned) {
      return syncFromMetadata(locale, removeOrphaned);
    }

    private Try<I18nLocaleModel> parseForTest(String content) {
      return buildModelFromFileContent(content);
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
    private final Map<String, String> files = new LinkedHashMap<>();

    private TestVCSManager(List<VCSFile> files) {
      files.forEach(file -> this.files.put(file.getPath(), file.getContent()));
    }

    @Override
    public void destroy() {
    }

    @Override
    public VCSFile getFile(String relativePath) {
      return file(relativePath, files.getOrDefault(relativePath, ""));
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String fileExtension) {
      return files.entrySet().stream()
          .map(entry -> (VCSFile) file(entry.getKey(), entry.getValue()))
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
      return new TestBatchAction(files);
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

  private static class TestBatchAction implements VCSAction.BatchAction {
    private final Map<String, String> files;
    private final List<Runnable> operations = new ArrayList<>();

    private TestBatchAction(Map<String, String> files) {
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
    public Try<Boolean> write(boolean forceWrite) {
      operations.forEach(Runnable::run);
      return Try.success(true);
    }

    @Override
    public Try<Boolean> commit(String message) {
      operations.forEach(Runnable::run);
      return Try.success(true);
    }

    @Override
    public VCSAction.BatchAction add(String relativePath) {
      return this;
    }

    @Override
    public VCSAction.BatchAction add(java.util.Collection<String> relativePaths) {
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, String content) {
      operations.add(() -> files.put(relativePath, content));
      return this;
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, byte[] content) {
      return create(relativePath, new String(content, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public VCSAction.BatchAction create(String relativePath, InputStream content) {
      return create(relativePath, read(content));
    }

    @Override
    public VCSAction.BatchAction remove(String relativePath) {
      operations.add(() -> files.remove(relativePath));
      return this;
    }

    @Override
    public VCSAction.BatchAction remove(java.util.Collection<String> relativePaths) {
      operations.add(() -> relativePaths.forEach(files::remove));
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, String content) {
      operations.add(() -> files.put(relativePath, content));
      return this;
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, byte[] content) {
      return update(relativePath, new String(content, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public VCSAction.BatchAction update(String relativePath, InputStream content) {
      return update(relativePath, read(content));
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath) {
      operations.add(() -> {
        String content = files.remove(relativePath);
        if (content != null) {
          files.put(newRelativePath, content);
        }
      });
      return this;
    }

    @Override
    public VCSAction.BatchAction move(String relativePath, String newRelativePath,
                                      java.util.function.Function<String, String> updateContent) {
      operations.add(() -> {
        String content = files.remove(relativePath);
        if (content != null) {
          files.put(newRelativePath, Optional.ofNullable(updateContent.apply(content)).orElse(content));
        }
      });
      return this;
    }

    private static String read(InputStream content) {
      try {
        byte[] buffer = new byte[1024];
        int read;
        StringBuilder builder = new StringBuilder();
        while ((read = content.read(buffer)) >= 0) {
          builder.append(new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8));
        }
        return builder.toString();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
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
