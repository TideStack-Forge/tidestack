package com.ouroboros.dev.app.service.vcs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.app.model.AppVersionDTO;
import com.ouroboros.util.ZipReader;
import com.ouroboros.vcs.file.VCSFile;

class AppVersionExportCredentialLeakRegressionTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldKeepCredentialSupportArtifactsOutOfGitFileListsAndExportedVersionZips() throws Exception {
    try (AppVcsIntegrationTestSupport.Scenario scenario = AppVcsIntegrationTestSupport.create(tempDir)) {
      scenario.saveRemotePolicy();
      scenario.initializeRepositoryWithTrackedPolicy();
      scenario.saveHttpBinding();
      Path supportKeyPath = scenario.createSupportKeyFile("deploy.key", "secret-key-material");

      assertTrue(Files.exists(scenario.localBindingStore.credentialsPath(AppVcsIntegrationTestSupport.APP_NAME)));
      assertTrue(Files.exists(scenario.localBindingStore.bindingsPath(AppVcsIntegrationTestSupport.APP_NAME)));
      assertTrue(Files.exists(supportKeyPath));

      List<String> headFiles = scenario.vcsManager().getFileList("", "HEAD")
          .stream()
          .map(VCSFile::getPath)
          .collect(Collectors.toList());
      assertTrue(headFiles.contains("README.md"));
      assertTrue(headFiles.contains(AppVcsIntegrationTestSupport.POLICY_FILE));
      assertNoCredentialArtifacts(headFiles);

      assertTrue(scenario.versionService.create(AppVcsIntegrationTestSupport.APP_NAME,
          new AppVersionDTO().setVersion("2.0.0").setDescription("Regression release")).isSuccess());

      List<String> zipEntries;
      try (InputStream exported = scenario.versionService.exportVersion(AppVcsIntegrationTestSupport.APP_NAME, "v2.0.0").get()) {
        zipEntries = new ZipReader().getFileList(exported);
      }

      assertTrue(zipEntries.contains("README.md"));
      assertTrue(zipEntries.contains(AppVcsIntegrationTestSupport.POLICY_FILE));
      assertNoCredentialArtifacts(zipEntries);
    }
  }

  private static void assertNoCredentialArtifacts(List<String> entries) {
    assertTrue(entries.stream().noneMatch(entry -> entry.startsWith("develop-support/vcs/") || entry.contains("/develop-support/vcs/")),
        "develop-support/vcs artifacts leaked into app output: " + entries);
    assertTrue(entries.stream().noneMatch(entry -> entry.equals("credentials.json") || entry.endsWith("/credentials.json")),
        "credentials.json leaked into app output: " + entries);
    assertTrue(entries.stream().noneMatch(entry -> entry.equals("bindings.json") || entry.endsWith("/bindings.json")),
        "bindings.json leaked into app output: " + entries);
    assertTrue(entries.stream().noneMatch(entry -> entry.startsWith("keys/") || entry.contains("/keys/")),
        "keys artifacts leaked into app output: " + entries);
  }
}
