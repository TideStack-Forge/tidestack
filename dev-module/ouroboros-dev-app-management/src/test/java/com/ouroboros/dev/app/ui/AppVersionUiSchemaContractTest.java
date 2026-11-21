package com.ouroboros.dev.app.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AppVersionUiSchemaContractTest {

  @Test
  void shouldKeepVersionsEntrypointAndBindVcsOperations() throws IOException {
    String versions = resourceAsString("META-INF/ouroboros/metadata/dev/{appName}/versions.ui-schema.json");
    String create = resourceAsString("META-INF/ouroboros/metadata/dev/{appName}/versions/create.ui-schema.json");

    assertTrue(versions.contains("\"title\": \"版本管理 - ${appName}\""));
    assertTrue(versions.contains("/dev/${appName}/versions/create"));
    assertTrue(versions.contains("/api/dev/${appName}/version"));

    assertTrue(versions.contains("/api/dev/${appName}/vcs/state"));
    assertTrue(versions.contains("/api/dev/${appName}/vcs/branches"));
    assertTrue(versions.contains("/api/dev/${appName}/vcs/branch/create-managed"));
    assertTrue(versions.contains("/api/dev/${appName}/vcs/branch/switch"));
    assertTrue(versions.contains("/api/dev/${appName}/vcs/pull"));
    assertTrue(versions.contains("/api/dev/${appName}/vcs/push"));

    assertTrue(versions.contains("/api/dev/${appName}/vcs/config/policy"));
    assertTrue(versions.contains("/api/dev/${appName}/vcs/config/local-binding"));

    assertTrue(versions.contains("\"name\": \"defaultRemote\""));
    assertTrue(versions.contains("\"name\": \"remotes\""));
    assertTrue(versions.contains("\"name\": \"url\""));
    assertTrue(versions.contains("\"name\": \"bindings\""));
    assertTrue(versions.contains("\"name\": \"remoteName\""));
    assertTrue(versions.contains("\"name\": \"editionCode\""));
    assertFalse(versions.contains("\"name\": \"fromRevision\""));
    assertFalse(versions.contains("\"name\": \"defaultBranch\""));
    assertTrue(versions.contains("创建定制分支"));
    assertTrue(versions.contains("releaseTrack == 'master' ? '产品主线'"));
    assertTrue(versions.contains("releaseTrack == 'custom' ? '定制线'"));
    assertTrue(versions.contains("releaseTrack == 'unmanaged' ? '非受管'"));

    for (String removedTerm : removedBranchContractTerms()) {
      assertFalse(versions.contains(removedTerm));
    }

    assertTrue(create.contains("/api/dev/${appName}/vcs/state"));
    assertTrue(create.contains("master 分支会生成纯 v&lt;semver&gt; 标签"));
    assertTrue(create.contains("custom/{editionCode} 分支会生成 v&lt;semver&gt;-ce.&lt;editionCode&gt;"));

    for (String removedTerm : removedBranchContractTerms()) {
      assertFalse(create.contains(removedTerm));
    }
  }

  private String[] removedBranchContractTerms() {
    return new String[] {
        "product" + "Line",
        "product" + "LineCode",
        "custom" + "Edition",
        "product" + "/"
    };
  }

  private String resourceAsString(String path) throws IOException {
    InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    if (stream == null) {
      throw new IOException("resource not found: " + path);
    }
    try (InputStream input = stream) {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
