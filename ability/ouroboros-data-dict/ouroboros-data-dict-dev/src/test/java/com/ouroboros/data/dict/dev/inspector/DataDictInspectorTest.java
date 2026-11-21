package com.ouroboros.data.dict.dev.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.data.dict.DataDict;
import com.ouroboros.data.dict.DataDictItem;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;

class DataDictInspectorTest {
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


  @Test
  void inspect_populatesTitleFromDevelopServiceAndMarksRootDirectory() {
    inspectorContext.getBeanFactory().registerSingleton("DataDictInspector", new DataDictInspector(new StubDevelopService(new StubDataDict("customer", "客户字典"))));
    var proxy = new DataDictInspector.Proxy();

    var description = proxy.inspect(vcsFile("data-dict/customer.dict.json"))
        .orElseThrow(() -> new AssertionError("data dict description should exist"));
    assertEquals("customer", description.getName());
    assertEquals("客户字典", description.getTitle());
    assertEquals("data-dict", description.getType());
    assertEquals("fas fa-th-list", description.getIcon());

    var legacyDescription = proxy.inspect(vcsFile("data-dict/customer.json"))
        .orElseThrow(() -> new AssertionError("legacy data dict description should exist"));
    assertEquals("customer", legacyDescription.getName());
    assertEquals("客户字典", legacyDescription.getTitle());

    Set<DirDescription> dirs = proxy.inspectDir(vcsFile("data-dict/group/customer.dict.json"));
    var rootDir = dirs.stream()
        .filter(dir -> "data-dict".equals(dir.getPath()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("data-dict root dir should exist"));
    assertEquals("数据字典", rootDir.getTitle());
  }

  @Test
  void inspect_usesMetadataNameForLegacyFileInSubdirectory() {
    inspectorContext.getBeanFactory().registerSingleton("DataDictInspector", new DataDictInspector(new StubDevelopService(new StubDataDict("admin.customer", "管理客户字典"))));
    var proxy = new DataDictInspector.Proxy();

    var description = proxy.inspect(vcsFile("data-dict/admin/customer.json",
            "{\"name\":\"admin.customer\",\"title\":\"管理客户字典\"}"))
        .orElseThrow(() -> new AssertionError("legacy nested data dict description should exist"));

    assertEquals("admin.customer", description.getName());
    assertEquals("管理客户字典", description.getTitle());
  }

  @Test
  void support_onlyAcceptsJsonFilesUnderDataDictDirectory() {
    inspectorContext.getBeanFactory().registerSingleton("DataDictInspector", new DataDictInspector(new StubDevelopService(new StubDataDict("customer", "客户字典"))));
    var proxy = new DataDictInspector.Proxy();

    assertTrue(proxy.support(vcsFile("data-dict/customer.dict.json")));
    assertTrue(proxy.support(vcsFile("data-dict/customer.json")));
    assertFalse(proxy.support(vcsFile("models/customer.dict.json")));
    assertFalse(proxy.support(vcsFile("data-dict/customer.txt")));
  }

  private static VCSFile vcsFile(String path) {
    return vcsFile(path, "");
  }

  private static VCSFile vcsFile(String path, String content) {
    return (VCSFile) Proxy.newProxyInstance(
        VCSFile.class.getClassLoader(),
        new Class<?>[]{VCSFile.class},
        (proxy, method, args) -> {
          switch (method.getName()) {
            case "exists":
              return true;
            case "getPath":
              return path;
            case "getFileName":
              return path.substring(path.lastIndexOf('/') + 1);
            case "getContent":
              return content;
            case "getInputStream":
              return null;
            case "getBytesArray":
              return new byte[0];
            case "getSize":
              return 1L;
            case "getCreationTime":
            case "getLastCommittedTime":
            case "getLastModifiedTime":
              return LocalDateTime.of(2026, 3, 22, 0, 0);
            case "getVersionState":
              return VersionState.NOTCHANGED;
            case "getLockState":
              return LockState.UNLOCKED;
            case "getLockOwner":
            case "getLockOwnerName":
            case "getRevision":
              return null;
            case "isHistorical":
              return false;
            case "lock":
            case "unlock":
            case "write":
            case "move":
            case "remove":
            case "commit":
            case "rollback":
            case "diffWith":
            case "diffWithWorkingCopy":
              return Try.failure(new UnsupportedOperationException(method.getName()));
            case "getRevisions":
              return Collections.emptyList();
            case "atRevision":
            case "atBranch":
            case "atTag":
              return proxy;
            default:
              throw new UnsupportedOperationException(method.getName());
          }
        });
  }

  private static class StubDevelopService extends BaseDevelopService<DataDict> {
    private final DataDict model;

    private StubDevelopService(DataDict model) {
      this.model = model;
    }

    @Override
    protected String getModelFileDirectory() {
      return "data-dict";
    }

    @Override
    protected String getModelDisplayName() {
      return "数据字典";
    }

    @Override
    protected String getFullName(DataDict model) {
      return model.getName();
    }

    @Override
    public Optional<DataDict> getOne(String fullName) {
      return Optional.ofNullable(model).filter(current -> fullName.equals(current.getName()));
    }

    @Override
    public Try<DataDict> buildModelFromFileContent(String fileContent) {
      if (fileContent == null || fileContent.isBlank()) {
        return Try.failure(new IllegalArgumentException("empty data dict metadata"));
      }
      return Try.success(model);
    }
  }

  private static class StubDataDict implements DataDict {
    private final String name;
    private final String title;

    private StubDataDict(String name, String title) {
      this.name = name;
      this.title = title;
    }

    @Override public String getName() { return name; }
    @Override public String getTitle() { return title; }
    @Override public String getType() { return "plain"; }
    @Override public String getGroup() { return null; }
    @Override public boolean isEditable() { return true; }
    @Override public boolean isTreeMode() { return false; }
    @Override public List<? extends DataDictItem> getItems() { return Collections.emptyList(); }
    @Override public void setItems(List<? extends DataDictItem> items) { }
  }
}
