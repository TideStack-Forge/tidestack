package com.ouroboros.uimodel.dev.microweb.service.impl;

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
import com.ouroboros.uimodel.dev.microweb.inspector.MicroWebModelInspector;
import com.ouroboros.uimodel.dev.microweb.model.LowCodeMicroWebModel;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.FileDescription;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicroWebDevServiceSuffixTest {
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

    assertEquals("micro-web/admin-app.micro-web.json", service.exposeModelFilePath("admin-app"));
  }

  @Test
  void createUsesTypedMetadataSuffix() {
    var recorder = new BatchRecorder();
    var service = service(Collections.emptyList(), recorder);

    var model = model("admin-app", "Admin App");
    var result = service.create(model, null);

    assertTrue(result.isSuccess());
    assertEquals(List.of("micro-web/admin-app.micro-web.json"), recorder.createdPaths);
    assertEquals(1, recorder.commits);
  }

  @Test
  void getOneReadsLegacyJsonWhenTypedFileIsMissing() {
    var service = service(List.of(file("micro-web/admin-app.json", modelJson("admin-app", "Admin App"))));

    Optional<LowCodeMicroWebModel> model = service.getOne("admin-app");

    assertTrue(model.isPresent());
    assertEquals("Admin App", model.get().getDescription());
  }

  @Test
  void updatePreservesExistingLegacyJsonSuffix() {
    var recorder = new BatchRecorder();
    var service = service(List.of(file("micro-web/admin-app.json", modelJson("admin-app", "Admin App"))), recorder);

    var updatedModel = model("admin-app", "Updated Admin App");
    var result = service.update("admin-app", updatedModel);

    assertTrue(result.isSuccess());
    assertEquals(List.of("micro-web/admin-app.json"), recorder.updatedPaths);
    assertTrue(recorder.createdPaths.isEmpty());
    assertEquals(1, recorder.commits);
  }

  @Test
  void commitDoesNotAddPrimaryPathWhenExistingFileUsesLegacySuffix() {
    var recorder = new BatchRecorder();
    var service = service(List.of(file("micro-web/admin-app.json", modelJson("admin-app", "Admin App"))), recorder);

    var result = service.commit("admin-app", null);

    assertTrue(result.isSuccess());
    assertEquals(List.of("micro-web/admin-app.json"), recorder.addedPaths);
    assertFalse(recorder.addedPaths.contains("micro-web/admin-app.micro-web.json"));
  }

  @Test
  void inspectorRecognizesTypedAndLegacySuffixesAsMicroWebModels() {
    var typedFile = file("micro-web/admin-app.micro-web.json", modelJson("admin-app", "Admin App"));
    var untypedFile = file("micro-web/legacy-app.json", modelJson("legacy-app", "Legacy App"));
    var service = service(List.of(typedFile, untypedFile));
    inspectorContext.getBeanFactory().registerSingleton("MicroWebModelInspector", new MicroWebModelInspector(service));
    FileDescription typed = new MicroWebModelInspector.Proxy().inspect(typedFile).orElseThrow();
    FileDescription untyped = new MicroWebModelInspector.Proxy().inspect(untypedFile).orElseThrow();

    assertEquals("admin-app", typed.getName());
    assertEquals("Admin App", typed.getTitle());
    assertEquals("legacy-app", untyped.getName());
    assertEquals("Legacy App", untyped.getTitle());
    assertEquals("micro-web", untyped.getType());
  }

  private static TestableMicroWebDevService service(List<VCSFile> files) {
    return service(files, new BatchRecorder());
  }

  private static TestableMicroWebDevService service(List<VCSFile> files, BatchRecorder recorder) {
    DevAppHolder.setAppName("test-app");
    return new TestableMicroWebDevService()
        .withVcsManagerCenter(new TestVCSManagerCenter(vcsManager(files, recorder)));
  }

  private static LowCodeMicroWebModel model(String name, String description) {
    var model = new LowCodeMicroWebModel();
    model.setName(name);
    model.setDescription(description);
    model.setEntry("/" + name + "/entry.js");
    model.setPublicPath("/" + name + "/");
    return model;
  }

  private static String modelJson(String name, String description) {
    return "{\"name\":\"" + name + "\",\"description\":\"" + description + "\",\"entry\":\"/" + name
        + "/entry.js\",\"publicPath\":\"/" + name + "/\"}";
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

  private static class TestableMicroWebDevService extends MicroWebDevService {
    private TestableMicroWebDevService withVcsManagerCenter(VCSManagerCenter vcsManagerCenter) {
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
    private final List<String> addedPaths = new ArrayList<>();
    private final List<String> createdPaths = new ArrayList<>();
    private final List<String> removedPaths = new ArrayList<>();
    private final List<String> updatedPaths = new ArrayList<>();
    private int commits;
    private final List<Runnable> validations = new ArrayList<>();

    private VCSAction.BatchAction batchAction() {
      return (VCSAction.BatchAction) Proxy.newProxyInstance(
          VCSAction.BatchAction.class.getClassLoader(),
          new Class<?>[] {VCSAction.BatchAction.class},
          (proxy, method, args) -> switch (method.getName()) {
            case "beforeWrite" -> {
              validations.add((Runnable) args[0]);
              yield proxy;
            }
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
            case "autoLock", "unlockWhenCommit", "committer", "committerEmail" -> proxy;
            case "commit" -> {
              validations.forEach(Runnable::run);
              commits++;
              yield Try.success(true);
            }
            case "write" -> Try.of(() -> {
              validations.forEach(Runnable::run);
              return true;
            });
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
