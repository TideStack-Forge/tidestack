package com.ouroboros.fieldtype.dev.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.fieldtype.dev.service.FieldTypeManagerServiceImpl;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.Tag;
import com.ouroboros.vcs.versioning.VersionState;

class FieldTypeManagerServiceImplTest {

  @Test
  void modelMetadata_usesFieldTypesJsonFiles() {
    var service = new ExposedFieldTypeManagerServiceImpl();
    var model = new LinkedHashMap<String, Object>();
    model.put("name", "builtinAlpha");

    assertEquals("字段类型", service.displayName());
    assertEquals("field-types", service.directory());
    assertEquals(".field-type.json", service.extension());
    assertEquals("builtinAlpha", service.fullName(model));
  }

  @Test
  void buildModelFromFileContent_parsesJsonMaps() {
    var service = new ExposedFieldTypeManagerServiceImpl();

    var result = service.buildModelFromFileContent("{\"name\":\"builtinAlpha\",\"label\":\"Builtin Alpha\"}");

    assertTrue(result.isSuccess());
    assertEquals("builtinAlpha", result.get().get("name"));
    assertEquals("Builtin Alpha", result.get().get("label"));
  }

  @Test
  void validateModel_requiresNameAndLabel() {
    var service = new ExposedFieldTypeManagerServiceImpl();

    assertEquals("字段类型定义不能为空！", message(service.validate(Collections.<String, Object>emptyMap()).getCause()));

    var missingName = new LinkedHashMap<String, Object>();
    missingName.put("label", "Builtin Alpha");
    assertEquals("名称不能为空！", message(service.validate(missingName).getCause()));

    var missingLabel = new LinkedHashMap<String, Object>();
    missingLabel.put("name", "builtinAlpha");
    assertEquals("标签不能为空！", message(service.validate(missingLabel).getCause()));

    var valid = new LinkedHashMap<String, Object>();
    valid.put("name", "builtinAlpha");
    valid.put("label", "Builtin Alpha");
    assertTrue(service.validate(valid).isSuccess());
  }

  @Test
  void getOne_readsTypedFieldTypeFile() {
    var service = new ExposedFieldTypeManagerServiceImpl();
    service.setVcsManagerCenter(new StubVCSManagerCenter(vcsManager(Collections.singletonMap(
        "field-types/typedField.field-type.json",
        "{\"name\":\"typedField\",\"label\":\"Typed Field\"}"
    ))));

    Optional<Map<String, Object>> model = DevAppHolder.runWithAppName("crm", () -> service.getOne("typedField"));

    assertTrue(model.isPresent());
    assertEquals("Typed Field", model.get().get("label"));
  }

  @Test
  void search_readsOnlyTypedFieldTypeFiles() {
    var service = new ExposedFieldTypeManagerServiceImpl();
    Map<String, String> files = new LinkedHashMap<String, String>();
    files.put("field-repository/legacyField.json", "{\"name\":\"legacyField\",\"label\":\"Legacy Field\"}");
    files.put("field-types/currentField.json", "{\"name\":\"currentField\",\"label\":\"Current Field\"}");
    files.put("field-types/currentField.field-type.json", "{\"name\":\"currentField\",\"label\":\"Current Typed Field\"}");
    files.put("field-types/typedField.field-type.json", "{\"name\":\"typedField\",\"label\":\"Typed Field\"}");
    service.setVcsManagerCenter(new StubVCSManagerCenter(vcsManager(files)));

    List<?> models = DevAppHolder.runWithAppName("crm", () -> service.search(Collections.<String, Object>emptyMap()));

    assertEquals(2, models.size());
    assertTrue(models.stream()
        .map(item -> (Map<String, Object>) item)
        .map(item -> String.valueOf(item.get("name")))
        .anyMatch("currentField"::equals));
    assertTrue(models.stream()
        .map(item -> (Map<String, Object>) item)
        .map(item -> String.valueOf(item.get("name")))
        .anyMatch("typedField"::equals));
    assertTrue(models.stream()
        .map(item -> (Map<String, Object>) item)
        .filter(item -> "currentField".equals(String.valueOf(item.get("name"))))
        .map(item -> String.valueOf(item.get("label")))
        .anyMatch("Current Typed Field"::equals));
  }

  private static String message(Throwable throwable) {
    assertInstanceOf(BusinessException.class, throwable);
    return throwable.getMessage();
  }

  private static class ExposedFieldTypeManagerServiceImpl extends FieldTypeManagerServiceImpl {
    private void setVcsManagerCenter(VCSManagerCenter vcsManagerCenter) {
      this.vcsManagerCenter = vcsManagerCenter;
    }

    private io.vavr.control.Try<Void> validate(Map<String, Object> model) {
      return super.validateModel(model);
    }

    private String displayName() {
      return super.getModelDisplayName();
    }

    private String directory() {
      return super.getModelFileDirectory();
    }

    private String extension() {
      return super.getModelFileExtension();
    }

    private String fullName(Map<String, Object> model) {
      return super.getFullName(model);
    }
  }

  private static VCSManager vcsManager(Map<String, String> files) {
    return (VCSManager) Proxy.newProxyInstance(
        VCSManager.class.getClassLoader(),
        new Class<?>[]{VCSManager.class},
        (proxy, method, args) -> {
          switch (method.getName()) {
            case "getFile":
              return vcsFile(String.valueOf(args[0]), files.get(String.valueOf(args[0])));
            case "walkDirectory":
              String directory = String.valueOf(args[0]);
              String suffix = String.valueOf(args[1]);
              return files.entrySet().stream()
                  .filter(entry -> entry.getKey().startsWith(directory + "/"))
                  .filter(entry -> entry.getKey().endsWith(suffix))
                  .map(entry -> vcsFile(entry.getKey(), entry.getValue()));
            case "destroy":
              return null;
            default:
              throw new UnsupportedOperationException(method.getName());
          }
        }
    );
  }

  private static VCSFile vcsFile(String path, String content) {
    return (VCSFile) Proxy.newProxyInstance(
        VCSFile.class.getClassLoader(),
        new Class<?>[]{VCSFile.class},
        (proxy, method, args) -> {
          switch (method.getName()) {
            case "exists":
              return content != null;
            case "getPath":
              return path;
            case "getFileName":
              return path.substring(path.lastIndexOf('/') + 1);
            case "getContent":
              return content;
            case "getInputStream":
              return (InputStream) null;
            case "getBytesArray":
              return content == null ? new byte[0] : content.getBytes();
            case "getSize":
              return Long.valueOf(content == null ? 0 : content.length());
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

  private static class StubVCSManagerCenter extends VCSManagerCenter {
    private final VCSManager vcsManager;

    private StubVCSManagerCenter(VCSManager vcsManager) {
      this.vcsManager = vcsManager;
    }

    @Override
    public VCSManager getVcsManager(String appName) {
      return vcsManager;
    }
  }
}
