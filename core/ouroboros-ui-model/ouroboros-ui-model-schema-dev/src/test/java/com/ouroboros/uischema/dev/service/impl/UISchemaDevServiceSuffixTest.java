package com.ouroboros.uischema.dev.service.impl;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.uischema.dev.inspector.UISchemaInspector;
import com.ouroboros.uischema.wrapper.SchemaNodeWrapper;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.FileDescription;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UISchemaDevServiceSuffixTest {
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
  void buildModelFilePathUsesTypedMetadataSuffix() {
    var service = service(Collections.emptyList());

    assertEquals("ui-schema/Admin.Home.ui-schema.json", service.exposeModelFilePath("Admin.Home"));
  }

  @Test
  void getOneReadsLegacyJsonWhenTypedFileIsMissing() {
    var service = service(List.of(file("ui-schema/Admin.Home.json", schemaJson("Admin.Home", "Admin Home"))));

    Optional<SchemaNodeWrapper> model = service.getOne("Admin.Home");

    assertTrue(model.isPresent());
    assertEquals("Admin Home", model.get().getTitle());
  }

  @Test
  void updatePreservesExistingLegacyJsonSuffix() {
    var recorder = new BatchRecorder();
    var service = service(List.of(file("ui-schema/Admin.Home.json", schemaJson("Admin.Home", "Admin Home"))), recorder);

    var updatedModel = new SchemaNodeWrapper();
    updatedModel.setName("Admin.Home");
    updatedModel.setTitle("Updated Admin Home");

    var result = service.update("Admin.Home", updatedModel);

    assertTrue(result.isSuccess());
    assertEquals(List.of("ui-schema/Admin.Home.json"), recorder.updatedPaths);
    assertTrue(recorder.createdPaths.isEmpty());
    assertEquals(1, recorder.writes);
    assertEquals(0, recorder.commits);
  }

  @Test
  void searchUsesWrappedNameToDeduplicateTypedAndLegacySchemas() {
    var service = service(List.of(
        file("ui-schema/path-based.ui-schema.json", schemaJson("Admin.Home", "Typed Admin Home")),
        file("ui-schema/Admin.Home.json", schemaJson("Admin.Home", "Legacy Admin Home"))
    ));

    var result = service.search(Collections.emptyMap());

    assertEquals(1, result.size());
    assertEquals("Typed Admin Home", result.get(0).getModel().getTitle());
  }

  @Test
  void inspectorRecognizesTypedAndLegacySuffixes() {
    var typedFile = file("ui-schema/Admin.Home.ui-schema.json", schemaJson("Admin.Home", "Admin Home"));
    var legacyFile = file("ui-schema/Legacy.Home.json", schemaJson("Legacy.Home", "Legacy Home"));
    var service = service(List.of(typedFile, legacyFile));
    inspectorContext.getBeanFactory().registerSingleton("UISchemaInspector", new UISchemaInspector(service));
    FileDescription typed = new UISchemaInspector.Proxy().inspect(typedFile).orElseThrow();
    FileDescription legacy = new UISchemaInspector.Proxy().inspect(legacyFile).orElseThrow();

    assertEquals("Admin.Home", typed.getName());
    assertEquals("Admin Home", typed.getTitle());
    assertEquals("Legacy.Home", legacy.getName());
    assertEquals("Legacy Home", legacy.getTitle());
  }

  private static TestableUISchemaDevService service(List<VCSFile> files) {
    return service(files, new BatchRecorder());
  }

  private static TestableUISchemaDevService service(List<VCSFile> files, BatchRecorder recorder) {
    DevAppHolder.setAppName("test-app");
    return new TestableUISchemaDevService()
        .withVcsManagerCenter(new TestVCSManagerCenter(vcsManager(files, recorder)));
  }

  private static String schemaJson(String name, String title) {
    return "{\"name\":\"" + name + "\",\"title\":\"" + title + "\",\"schema\":{\"type\":\"page\"}}";
  }

  private static VCSManager vcsManager(List<VCSFile> files, BatchRecorder recorder) {
    return (VCSManager) Proxy.newProxyInstance(
        VCSManager.class.getClassLoader(),
        new Class<?>[] {VCSManager.class},
        (proxy, method, args) -> {
          if ("getFile".equals(method.getName())) {
            String path = (String) args[0];
            return files.stream()
                .filter(file -> file.getPath().equals(path))
                .findFirst()
                .orElse(file(path, false, ""));
          }
          if ("walkDirectory".equals(method.getName()) && args.length == 2) {
            String directory = (String) args[0];
            String extension = (String) args[1];
            return files.stream()
                .filter(file -> file.getPath().startsWith(directory))
                .filter(file -> file.getPath().endsWith(extension));
          }
          if ("batchOperation".equals(method.getName())) {
            return recorder.batchAction();
          }
          throw new UnsupportedOperationException(method.getName());
        });
  }

  private static VCSFile file(String path, String content) {
    return file(path, true, content);
  }

  private static VCSFile file(String path, boolean exists, String content) {
    return (VCSFile) Proxy.newProxyInstance(
        VCSFile.class.getClassLoader(),
        new Class<?>[] {VCSFile.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "exists" -> exists;
          case "getPath" -> path;
          case "getFileName" -> path.substring(path.lastIndexOf('/') + 1);
          case "getContent" -> content;
          case "getSize" -> Long.valueOf(content.length());
          case "getCreationTime", "getLastModifiedTime" -> LocalDateTime.now();
          case "getLockOwnerName" -> null;
          case "getLockState" -> LockState.UNLOCKED;
          case "getVersionState" -> VersionState.NOTCHANGED;
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }

  private static class TestableUISchemaDevService extends UISchemaDevService {
    private TestableUISchemaDevService withVcsManagerCenter(VCSManagerCenter vcsManagerCenter) {
      this.vcsManagerCenter = vcsManagerCenter;
      return this;
    }

    private String exposeModelFilePath(String fullName) {
      return buildModelFilePath(fullName);
    }

    @Override
    protected void publishEvent(java.util.EventObject event) {
      // no-op for service tests
    }
  }

  private static class BatchRecorder {
    private final List<String> createdPaths = new ArrayList<>();
    private final List<String> updatedPaths = new ArrayList<>();
    private int commits;
    private int writes;

    private VCSAction.BatchAction batchAction() {
      return (VCSAction.BatchAction) Proxy.newProxyInstance(
          VCSAction.BatchAction.class.getClassLoader(),
          new Class<?>[] {VCSAction.BatchAction.class},
          (proxy, method, args) -> switch (method.getName()) {
            case "add" -> proxy;
            case "create" -> {
              createdPaths.add((String) args[0]);
              yield proxy;
            }
            case "update" -> {
              updatedPaths.add((String) args[0]);
              yield proxy;
            }
            case "remove", "autoLock", "unlockWhenCommit", "committer", "committerEmail" -> proxy;
            case "commit" -> {
              commits++;
              yield Try.success(true);
            }
            case "write" -> {
              writes++;
              yield Try.success(true);
            }
            default -> throw new UnsupportedOperationException(method.getName());
          });
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
}
