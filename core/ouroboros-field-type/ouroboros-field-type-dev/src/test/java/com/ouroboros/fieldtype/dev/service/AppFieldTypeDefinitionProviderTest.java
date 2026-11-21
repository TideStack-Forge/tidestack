package com.ouroboros.fieldtype.dev.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeCatalogScene;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;
import com.ouroboros.fieldtype.dev.service.AppFieldTypeDefinitionProvider;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.versioning.Tag;
import com.ouroboros.vcs.versioning.VersionState;

class AppFieldTypeDefinitionProviderTest {

  @Test
  void supports_requiresDevAppContextWithNonBlankAppName() {
    var provider = new AppFieldTypeDefinitionProvider(new StubVCSManagerCenter(null), new FieldTypeDefinitionReader());

    assertFalse(provider.supports(null));
    assertFalse(provider.supports(FieldTypeCatalogContext.runtime()));

    var blankApp = new FieldTypeCatalogContext();
    blankApp.setScene(FieldTypeCatalogScene.DEV_APP);
    blankApp.setAppName("  ");
    assertFalse(provider.supports(blankApp));

    assertTrue(provider.supports(FieldTypeCatalogContext.devApp("crm")));
  }

  @Test
  void load_readsAppDefinitionsAndDeduplicatesByNameIgnoringCase() {
    var files = Arrays.asList(
        vcsFile("field-types/Alpha.field-type.json", "{\"name\":\"Alpha\",\"label\":\"First\"}"),
        vcsFile("field-types/alpha.field-type.json", "{\"name\":\"alpha\",\"label\":\"Typed\",\"paramsUiSchema\":[{\"name\":\"size\",\"value\":64}]}")
    );
    var provider = new AppFieldTypeDefinitionProvider(
        new StubVCSManagerCenter(vcsManager(files)),
        new FieldTypeDefinitionReader()
    );

    List<FieldTypeDefinition> definitions = provider.load(FieldTypeCatalogContext.devApp("crm"));

    assertEquals(1, definitions.size());
    assertEquals("alpha", definitions.get(0).getName());
    assertEquals("Typed", definitions.get(0).getLabel());
    assertEquals(FieldTypeDefinitionSource.APP, definitions.get(0).getSource());
    assertEquals("field-types/alpha.field-type.json", definitions.get(0).getSourceLocation());
    assertEquals(64, definitions.get(0).getDefaultParams().get("size"));
  }

  @Test
  void load_returnsEmptyWhenAppHasNoVcsManager() {
    var provider = new AppFieldTypeDefinitionProvider(new StubVCSManagerCenter(null), new FieldTypeDefinitionReader());

    assertTrue(provider.load(FieldTypeCatalogContext.devApp("crm")).isEmpty());
  }

  @Test
  void load_ignoresLegacyJsonFiles() {
    var files = Arrays.asList(
        vcsFile("field-repository/Legacy.json", "{\"name\":\"legacyAppField\",\"label\":\"Legacy App Field\",\"paramsUiSchema\":[{\"name\":\"size\",\"value\":12}]}"),
        vcsFile("field-types/Legacy.json", "{\"name\":\"legacyTypeField\",\"label\":\"Legacy Type Field\"}")
    );
    var provider = new AppFieldTypeDefinitionProvider(
        new StubVCSManagerCenter(vcsManager(files)),
        new FieldTypeDefinitionReader()
    );

    List<FieldTypeDefinition> definitions = provider.load(FieldTypeCatalogContext.devApp("crm"));

    assertTrue(definitions.isEmpty());
  }

  private static VCSManager vcsManager(List<VCSFile> files) {
    return (VCSManager) Proxy.newProxyInstance(
        VCSManager.class.getClassLoader(),
        new Class<?>[]{VCSManager.class},
        (proxy, method, args) -> {
          if ("walkDirectory".equals(method.getName())) {
            String directory = String.valueOf(args[0]);
            String suffix = String.valueOf(args[1]);
            return files.stream()
                .filter(file -> file.getPath().startsWith(directory + "/"))
                .filter(file -> file.getPath().endsWith(suffix));
          }
          if ("destroy".equals(method.getName())) {
            return null;
          }
          throw new UnsupportedOperationException(method.getName());
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
              return content.getBytes();
            case "getSize":
              return Long.valueOf(content.length());
            case "getCreationTime":
            case "getLastCommittedTime":
            case "getLastModifiedTime":
              return java.time.LocalDateTime.of(2026, 3, 14, 0, 0);
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
