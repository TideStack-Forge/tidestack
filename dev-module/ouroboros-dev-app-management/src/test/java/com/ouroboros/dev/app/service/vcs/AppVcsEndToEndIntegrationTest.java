package com.ouroboros.dev.app.service.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.app.model.AppBranchDTO;
import com.ouroboros.dev.app.model.AppVcsStateDTO;
import com.ouroboros.dev.app.model.AppVersionDTO;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.versioning.Tag;

class AppVcsEndToEndIntegrationTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldCoverRemoteCredentialsManagedBranchesSyncAndBranchAwareReleases() throws Exception {
    try (AppVcsIntegrationTestSupport.Scenario scenario = AppVcsIntegrationTestSupport.create(tempDir)) {
      scenario.saveRemotePolicy();
      scenario.initializeRepositoryWithTrackedPolicy();
      scenario.saveHttpBinding();

      assertTrue(Files.exists(scenario.localBindingStore.credentialsPath(AppVcsIntegrationTestSupport.APP_NAME)));
      assertTrue(Files.exists(scenario.localBindingStore.bindingsPath(AppVcsIntegrationTestSupport.APP_NAME)));

      VCSConfig resolvedConfig = scenario.resolvedConfig();
      assertNotNull(resolvedConfig);
      assertEquals(AppVcsIntegrationTestSupport.TRUNK_BRANCH, resolvedConfig.getDefaultBranch());
      assertEquals(scenario.remoteUri, resolvedConfig.getRemoteUri());
      assertEquals(VCSConfig.AuthType.HTTP_BASIC, resolvedConfig.getAuthType());
      assertEquals("deploy-user", resolvedConfig.getUsername());
      assertEquals("deploy-password", resolvedConfig.getPassword());

      AppVcsStateDTO initialState = scenario.vcsFacade.getState(AppVcsIntegrationTestSupport.APP_NAME).get();
      assertEquals(AppVcsIntegrationTestSupport.TRUNK_BRANCH, initialState.getCurrentBranch());
      assertEquals("master", initialState.getReleaseTrack());
      assertTrue(initialState.isWorkspaceClean());

      scenario.syncCurrentBranchViaTransport(false);
      AppVcsStateDTO pushedTrunk = scenario.vcsFacade.getState(AppVcsIntegrationTestSupport.APP_NAME).get();
      assertEquals(AppVcsIntegrationTestSupport.TRUNK_BRANCH, pushedTrunk.getCurrentBranch());
      assertEquals(scenario.remoteUri, pushedTrunk.getRemoteUri());

      scenario.commitAndPushFromPeer(AppVcsIntegrationTestSupport.TRUNK_BRANCH,
          "main-remote.txt",
          "main-remote-change",
          "peer trunk commit");
      scenario.syncCurrentBranchViaTransport(true);
      AppVcsStateDTO pulledTrunk = scenario.vcsFacade.getState(AppVcsIntegrationTestSupport.APP_NAME).get();
      assertEquals(AppVcsIntegrationTestSupport.TRUNK_BRANCH, pulledTrunk.getCurrentBranch());
      assertEquals("main-remote-change", scenario.vcsManager().getFile("main-remote.txt").getContent());

      assertTrue(scenario.versionService.create(AppVcsIntegrationTestSupport.APP_NAME,
          new AppVersionDTO().setVersion("1.0.0").setDescription("Trunk release")).isSuccess());
      AppVersionTagMetadata trunkRelease = getRequiredTagMetadata(scenario, "v1.0.0");
      assertEquals("master", trunkRelease.getReleaseTrack());
      assertEquals(AppVcsIntegrationTestSupport.TRUNK_BRANCH, trunkRelease.getSourceBranch());
      assertEquals("Trunk release", trunkRelease.getDescription());

      AppBranchDTO customBranch = scenario.vcsFacade.createManagedBranch(AppVcsIntegrationTestSupport.APP_NAME,
          AppVcsIntegrationTestSupport.customBranchRequest()).get();
      assertEquals(AppVcsIntegrationTestSupport.CUSTOM_BRANCH, customBranch.getBranchName());
      assertEquals("custom", customBranch.getReleaseTrack());

      AppVcsStateDTO customState = scenario.vcsFacade.switchBranch(AppVcsIntegrationTestSupport.APP_NAME,
          AppVcsIntegrationTestSupport.switchBranchRequest(AppVcsIntegrationTestSupport.CUSTOM_BRANCH)).get();
      assertEquals(AppVcsIntegrationTestSupport.CUSTOM_BRANCH, customState.getCurrentBranch());
      assertEquals("custom", customState.getReleaseTrack());
      assertEquals("premium", customState.getEditionCode());

      scenario.commitLocalFile("custom.txt", "custom-branch-change", "custom branch commit");
      scenario.syncCurrentBranchViaTransport(false);
      AppVcsStateDTO pushedCustom = scenario.vcsFacade.getState(AppVcsIntegrationTestSupport.APP_NAME).get();
      assertEquals(AppVcsIntegrationTestSupport.CUSTOM_BRANCH, pushedCustom.getCurrentBranch());

      scenario.commitAndPushFromPeer(AppVcsIntegrationTestSupport.CUSTOM_BRANCH,
          "custom-remote.txt",
          "custom-remote-change",
          "peer custom commit");
      scenario.syncCurrentBranchViaTransport(true);
      AppVcsStateDTO pulledCustom = scenario.vcsFacade.getState(AppVcsIntegrationTestSupport.APP_NAME).get();
      assertEquals(AppVcsIntegrationTestSupport.CUSTOM_BRANCH, pulledCustom.getCurrentBranch());
      assertEquals("custom-remote-change", scenario.vcsManager().getFile("custom-remote.txt").getContent());

      assertTrue(scenario.versionService.create(AppVcsIntegrationTestSupport.APP_NAME,
          new AppVersionDTO().setVersion("1.0.0").setDescription("Custom release")).isSuccess());
      AppVersionTagMetadata customRelease = getRequiredTagMetadata(scenario, "v1.0.0-ce.premium");
      assertEquals("custom", customRelease.getReleaseTrack());
      assertEquals(AppVcsIntegrationTestSupport.CUSTOM_BRANCH, customRelease.getSourceBranch());
      assertEquals("premium", customRelease.getEditionCode());
      assertEquals("Custom release", customRelease.getDescription());

      Set<String> managedBranches = scenario.vcsFacade.getManagedBranches(AppVcsIntegrationTestSupport.APP_NAME)
          .get()
          .stream()
          .map(AppBranchDTO::getBranchName)
          .collect(Collectors.toCollection(HashSet::new));
      assertTrue(managedBranches.containsAll(Arrays.asList(
          AppVcsIntegrationTestSupport.TRUNK_BRANCH,
          AppVcsIntegrationTestSupport.CUSTOM_BRANCH)));
    }
  }

  private static AppVersionTagMetadata getRequiredTagMetadata(AppVcsIntegrationTestSupport.Scenario scenario, String gitTag) {
    Tag tag = scenario.vcsManager().getTagList().get()
        .stream()
        .filter(current -> current.getName() != null && current.getName().endsWith(gitTag))
        .findFirst()
        .orElse(null);
    assertNotNull(tag, "Expected git tag to exist: " + gitTag);
    return Json.toBean(AppVersionTagMetadata.class, tag.getMessage());
  }
}
