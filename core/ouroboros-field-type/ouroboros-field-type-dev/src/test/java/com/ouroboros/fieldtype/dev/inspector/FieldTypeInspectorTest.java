package com.ouroboros.fieldtype.dev.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;

class FieldTypeInspectorTest {
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
  void inspect_populatesFieldTypeDescriptionFromDevelopService() {
    var model = new LinkedHashMap<String, Object>();
    model.put("name", "customer");
    model.put("label", "客户");
    model.put("description", "客户字段");
    inspectorContext.getBeanFactory().registerSingleton("FieldTypeInspector", new FieldTypeInspector(new StubDevelopService(Collections.singletonMap("customer", model))));
    var description = new FieldTypeInspector.Proxy().inspect(vcsFile("field-types/customer.field-type.json"))
        .orElseThrow(() -> new AssertionError("field type description should exist"));

    assertEquals("customer", description.getName());
    assertEquals("客户", description.getTitle());
    assertEquals("客户字段", description.getDescription());
    assertEquals("field-type", description.getType());
    assertEquals("fas fa-coins", description.getIcon());
  }

  @Test
  void inspectDir_marksFieldTypesDirectoryAndSupportFiltersJsonFiles() {
    inspectorContext.getBeanFactory().registerSingleton("FieldTypeInspector", new FieldTypeInspector(new StubDevelopService(Collections.emptyMap())));
    var proxy = new FieldTypeInspector.Proxy();

    assertTrue(proxy.support(vcsFile("field-types/group/customer.field-type.json")));
    assertFalse(proxy.support(vcsFile("field-types/group/customer.json")));
    assertFalse(proxy.support(vcsFile("models/customer.json")));
    assertFalse(proxy.support(vcsFile("field-types/customer.txt")));

    Set<DirDescription> dirs = proxy.inspectDir(vcsFile("field-types/group/customer.field-type.json"));

    var rootDir = dirs.stream()
        .filter(dir -> "field-types".equals(dir.getPath()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("field-types root dir should exist"));
    assertEquals("字段类型", rootDir.getTitle());
  }

  @Test
  void inspect_doesNotSupportLegacyFieldRepositoryFiles() {
    inspectorContext.getBeanFactory().registerSingleton("FieldTypeInspector", new FieldTypeInspector(new StubDevelopService(Collections.emptyMap())));
    var proxy = new FieldTypeInspector.Proxy();

    assertFalse(proxy.support(vcsFile("field-repository/legacyCustomer.json")));
    assertTrue(proxy.inspect(vcsFile("field-repository/legacyCustomer.json")).isEmpty());
  }

  private static VCSFile vcsFile(String path) {
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
              return "";
            case "getInputStream":
              return null;
            case "getBytesArray":
              return new byte[0];
            case "getSize":
              return 1L;
            case "getCreationTime":
            case "getLastCommittedTime":
            case "getLastModifiedTime":
              return LocalDateTime.of(2026, 3, 14, 0, 0);
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
        }
    );
  }

  private static class StubDevelopService extends BaseDevelopService<Map<String, Object>> {
    private final Map<String, Map<String, Object>> models;

    private StubDevelopService(Map<String, Map<String, Object>> models) {
      this.models = models;
    }

    @Override
    protected String getModelFileDirectory() {
      return "field-types";
    }

    @Override
    protected String getModelDisplayName() {
      return "字段类型";
    }

    @Override
    protected String getFullName(Map<String, Object> model) {
      return (String) model.get("name");
    }

    @Override
    public Optional<Map<String, Object>> getOne(String fullName) {
      return Optional.ofNullable(models.get(fullName));
    }
  }
}
