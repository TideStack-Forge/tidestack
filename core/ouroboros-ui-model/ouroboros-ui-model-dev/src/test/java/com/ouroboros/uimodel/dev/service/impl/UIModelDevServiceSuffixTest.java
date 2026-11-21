package com.ouroboros.uimodel.dev.service.impl;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.uimodel.dev.inspector.UIModelInspector;
import com.ouroboros.uimodel.dev.model.LowcodeUIModel;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.FileDescription;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UIModelDevServiceSuffixTest {
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

    assertEquals("ui-model/admin.home.ui-model.json", service.exposeModelFilePath("admin.home"));
  }

  @Test
  void getOneReadsLegacyJsonWhenTypedFileIsMissing() {
    var service = service(List.of(file("ui-model/admin.home.json", modelJson("/admin/home", "Admin Home"))));

    Optional<LowcodeUIModel> model = service.getOne("admin.home");

    assertTrue(model.isPresent());
    assertEquals("Admin Home", model.get().getTitle());
  }

  @Test
  void updatePreservesExistingLegacyJsonSuffix() {
    var recorder = new BatchRecorder();
    var service = service(List.of(file("ui-model/admin.home.json", modelJson("/admin/home", "Admin Home"))), recorder);

    var updatedModel = new LowcodeUIModel();
    updatedModel.setPath("/admin/home");
    updatedModel.setTitle("Updated Admin Home");
    updatedModel.setType("amis");

    var result = service.update("admin.home", updatedModel);

    assertTrue(result.isSuccess());
    assertEquals(List.of("ui-model/admin.home.json"), recorder.updatedPaths);
    assertTrue(recorder.createdPaths.isEmpty());
    assertEquals(1, recorder.commits);
  }

  @Test
  void renameLegacyJsonMovesToTypedMetadataSuffix() {
    var recorder = new BatchRecorder();
    var service = service(List.of(file("ui-model/admin.home.json", modelJson("/admin/home", "Admin Home"))), recorder);

    var updatedModel = new LowcodeUIModel();
    updatedModel.setPath("/admin/dashboard");
    updatedModel.setTitle("Admin Dashboard");
    updatedModel.setType("amis");

    var result = service.update("admin.home", updatedModel);

    assertTrue(result.isSuccess());
    assertEquals(List.of("ui-model/admin.home.json"), recorder.movedFromPaths);
    assertEquals(List.of("ui-model/admin.dashboard.ui-model.json"), recorder.movedToPaths);
    assertTrue(recorder.updatedPaths.isEmpty());
    assertEquals(1, recorder.commits);
  }

  @Test
  void commitDoesNotAddPrimaryPathWhenExistingFileUsesLegacySuffix() {
    var recorder = new BatchRecorder();
    var service = service(List.of(file("ui-model/admin.home.json", modelJson("/admin/home", "Admin Home"))), recorder);

    var result = service.exposeDoCommitWithPrimaryPath("admin.home");

    assertTrue(result.isSuccess());
    assertEquals(List.of("ui-model/admin.home.json"), recorder.addedPaths);
  }

  @Test
  void searchUsesTypedModelWhenTypedAndLegacyFilesShareFullName() {
    var service = service(List.of(
        file("ui-model/admin.home.ui-model.json", modelJson("/admin/home", "Typed Admin Home")),
        file("ui-model/admin.home.json", modelJson("/admin/home", "Legacy Admin Home"))
    ));

    var result = service.search(Collections.emptyMap());

    assertEquals(1, result.size());
    var model = (LowcodeUIModel) result.get(0);
    assertEquals("Typed Admin Home", model.getTitle());
  }

  @Test
  void deleteRemovesTypedAndLegacyFilesWhenBothShareFullName() {
    var recorder = new BatchRecorder();
    var service = service(List.of(
        file("ui-model/admin.home.ui-model.json", modelJson("/admin/home", "Typed Admin Home")),
        file("ui-model/admin.home.json", modelJson("/admin/home", "Legacy Admin Home"))
    ), recorder);

    var result = service.delete("admin.home", null);

    assertTrue(result.isSuccess());
    assertEquals(List.of("ui-model/admin.home.ui-model.json", "ui-model/admin.home.json"), recorder.removedPaths);
  }

  @Test
  void inspectorRecognizesTypedAndLegacySuffixes() {
    var typedFile = file("ui-model/admin.home.ui-model.json", modelJson("/admin/home", "Admin Home"));
    var legacyFile = file("ui-model/legacy.home.json", modelJson("/legacy/home", "Legacy Home"));
    var service = service(List.of(typedFile, legacyFile));
    inspectorContext.getBeanFactory().registerSingleton("UIModelInspector", new UIModelInspector(service));
    FileDescription typed = new UIModelInspector.Proxy().inspect(typedFile).orElseThrow();
    FileDescription legacy = new UIModelInspector.Proxy().inspect(legacyFile).orElseThrow();

    assertEquals("admin.home", typed.getName());
    assertEquals("Admin Home", typed.getTitle());
    assertEquals("legacy.home", legacy.getName());
    assertEquals("Legacy Home", legacy.getTitle());
  }

  private static TestableUIModelDevService service(List<VCSFile> files) {
    return service(files, new BatchRecorder());
  }

  private static TestableUIModelDevService service(List<VCSFile> files, BatchRecorder recorder) {
    DevAppHolder.setAppName("test-app");
    return new TestableUIModelDevService()
        .withVcsManagerCenter(new TestVCSManagerCenter(vcsManager(files, recorder)));
  }

  private static String modelJson(String path, String title) {
    return "{\"path\":\"" + path + "\",\"title\":\"" + title + "\",\"type\":\"amis\"}";
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

  private static class TestableUIModelDevService extends UIModelDevService {
    private TestableUIModelDevService withVcsManagerCenter(VCSManagerCenter vcsManagerCenter) {
      this.vcsManagerCenter = vcsManagerCenter;
      return this;
    }

    private String exposeModelFilePath(String fullName) {
      return buildModelFilePath(fullName);
    }

    private Try<Boolean> exposeDoCommitWithPrimaryPath(String fullName) {
      return doCommit(fullName, Collections.singleton(buildModelFilePath(fullName)), null);
    }

    @Override
    protected void publishEvent(java.util.EventObject event) {
      // no-op for service tests
    }
  }

  private static class BatchRecorder {
    private final List<String> addedPaths = new ArrayList<>();
    private final List<String> createdPaths = new ArrayList<>();
    private final List<String> movedFromPaths = new ArrayList<>();
    private final List<String> movedToPaths = new ArrayList<>();
    private final List<String> removedPaths = new ArrayList<>();
    private final List<String> updatedPaths = new ArrayList<>();
    private int commits;

    private VCSAction.BatchAction batchAction() {
      return (VCSAction.BatchAction) Proxy.newProxyInstance(
          VCSAction.BatchAction.class.getClassLoader(),
          new Class<?>[] {VCSAction.BatchAction.class},
          (proxy, method, args) -> switch (method.getName()) {
            case "add" -> {
              recordPaths(addedPaths, args[0]);
              yield proxy;
            }
            case "create" -> {
              createdPaths.add((String) args[0]);
              yield proxy;
            }
            case "remove" -> {
              recordPaths(removedPaths, args[0]);
              yield proxy;
            }
            case "update" -> {
              updatedPaths.add((String) args[0]);
              yield proxy;
            }
            case "move" -> {
              movedFromPaths.add((String) args[0]);
              movedToPaths.add((String) args[1]);
              yield proxy;
            }
            case "autoLock", "unlockWhenCommit", "committer", "committerEmail" -> proxy;
            case "commit" -> {
              commits++;
              yield Try.success(true);
            }
            case "write" -> Try.success(true);
            default -> throw new UnsupportedOperationException(method.getName());
          });
    }

    private static void recordPaths(List<String> destination, Object paths) {
      if (paths instanceof String path) {
        destination.add(path);
        return;
      }
      ((Collection<?>) paths).forEach(path -> destination.add((String) path));
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
