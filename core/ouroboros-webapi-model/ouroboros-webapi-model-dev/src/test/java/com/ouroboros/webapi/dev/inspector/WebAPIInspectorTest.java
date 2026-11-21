package com.ouroboros.webapi.dev.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;
import com.ouroboros.webapi.dev.model.LowcodeWebAPIModel;

class WebAPIInspectorTest {
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
  void inspect_populatesTitleFromModelSummaryAndRootDirectoryTitle() {
    inspectorContext.getBeanFactory().registerSingleton("WebAPIInspector", new WebAPIInspector(new StubWebAPIDevService(model("GET", "/customers", "客户接口"))));
    var proxy = new WebAPIInspector.Proxy();

    var description = proxy.inspect(vcsFile("webapi/customers.webapi.json"))
        .orElseThrow(() -> new AssertionError("webapi description should exist"));
    assertEquals("customers", description.getName());
    assertEquals("客户接口", description.getTitle());
    assertEquals("webapi", description.getType());
    assertEquals("webapi", description.getIcon());

    Set<DirDescription> dirs = proxy.inspectDir(vcsFile("webapi/customers.webapi.json"));
    var rootDir = dirs.stream()
        .filter(dir -> "webapi".equals(dir.getPath()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("webapi root dir should exist"));
    assertEquals("WebAPI模型", rootDir.getTitle());
  }

  @Test
  void support_isBoundedToWebapiJsonFiles() {
    inspectorContext.getBeanFactory().registerSingleton("WebAPIInspector", new WebAPIInspector(new StubWebAPIDevService(model("GET", "/customers", "客户接口"))));
    var proxy = new WebAPIInspector.Proxy();

    assertTrue(proxy.support(vcsFile("webapi/customers.webapi.json")));
    Arrays.asList("webapiX/customers.webapi.json", "webapi-custom/customers.webapi.json")
        .forEach(path -> assertFalse(proxy.support(vcsFile(path))));
    assertFalse(proxy.support(vcsFile("models/customers.webapi.json")));
    assertFalse(proxy.support(vcsFile("webapi/customers.txt")));
  }

  private static LowcodeWebAPIModel model(String method, String path, String summary) {
    var model = new LowcodeWebAPIModel();
    model.setMethod(method);
    model.setPath(path);
    model.setSummary(summary);
    return model;
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
              return LocalDateTime.of(2026, 3, 23, 0, 0);
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

  private static class StubWebAPIDevService extends BaseDevelopService<LowcodeWebAPIModel> {
    private final LowcodeWebAPIModel model;

    private StubWebAPIDevService(LowcodeWebAPIModel model) {
      this.model = model;
    }

    @Override
    protected String getModelFileDirectory() {
      return "webapi";
    }

    @Override
    protected String getModelDisplayName() {
      return "WebAPI模型";
    }

    @Override
    protected String getFullName(LowcodeWebAPIModel model) {
      return model.getFullName();
    }

    @Override
    public Optional<LowcodeWebAPIModel> getOne(String fullName) {
      return Optional.ofNullable(model).filter(current -> "customers".equals(fullName));
    }
  }
}
