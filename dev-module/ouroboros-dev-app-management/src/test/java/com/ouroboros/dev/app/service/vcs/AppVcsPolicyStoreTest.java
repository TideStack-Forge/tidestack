package com.ouroboros.dev.app.service.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vavr.control.Try;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.app.model.AppVcsPolicy;
import com.ouroboros.dev.app.model.AppVcsRemoteDefinition;
import com.ouroboros.util.Json;

class AppVcsPolicyStoreTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldRejectUnsafeRemoteTargetsWhenSavingPolicy() throws Exception {
    Path appHome = Files.createDirectories(tempDir.resolve("dev").resolve("crm"));
    AppHomeResolver appHomeResolver = appName -> appHome.toString();
    AppVcsPolicyStore store = new AppVcsPolicyStore(appHomeResolver);

    assertTrue(store.save("crm", policyWithLegacyRemote("https://deploy:secret@example.com/team/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("http://example.com/team/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("git://example.com/team/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("https://127.0.0.1/team/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("https://127.1/team/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("https://2130706433/team/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("https://[0:0:0:0:0:0:0:1]/team/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("https://[::ffff:127.0.0.1]/team/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("ssh://git@localhost/team/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("ssh://git@git.internal/team/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("git@192.168.1.10:team/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("https://example.com/team/crm.git?token=secret")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("https://example.com/team/crm.git#secret")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("git:token@example.com:team/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("file:///tmp/crm.git")).isFailure());
    assertTrue(store.save("crm", policyWithLegacyRemote("/tmp/crm.git")).isFailure());
  }

  @Test
  void shouldRedactUserInfoBearingRemoteUrisWhenLoadingPolicyForApi() throws Exception {
    Path appHome = Files.createDirectories(tempDir.resolve("dev").resolve("crm-redacted"));
    AppHomeResolver appHomeResolver = appName -> appHome.toString();
    AppVcsPolicyStore store = new AppVcsPolicyStore(appHomeResolver);

    AppVcsPolicy rawPolicy = new AppVcsPolicy();
    rawPolicy.setDefaultRemote("origin");
    rawPolicy.setRemotes(java.util.Arrays.asList(
        remote("origin", "https://deploy:secret@example.com/team/crm.git", "main"),
        remote("upstream", "git@example.com:team/crm.git", "release/main"),
        remote("http", "http://example.com/team/crm.git", "main"),
        remote("git", "git://example.com/team/crm.git", "main"),
        remote("loopback", "https://127.0.0.1/team/crm.git", "main"),
        remote("shortLoopback", "https://127.1/team/crm.git", "main"),
        remote("numericLoopback", "https://2130706433/team/crm.git", "main"),
        remote("ipv6Loopback", "https://[0:0:0:0:0:0:0:1]/team/crm.git", "main"),
        remote("mappedLoopback", "https://[::ffff:127.0.0.1]/team/crm.git", "main"),
        remote("localhost", "ssh://git@localhost/team/crm.git", "main"),
        remote("internalDns", "ssh://git@git.internal/team/crm.git", "main"),
        remote("privateScp", "git@192.168.1.10:team/crm.git", "main"),
        remote("query", "https://example.com/team/crm.git?token=secret", "main"),
        remote("fragment", "ssh://git@example.com/team/crm.git#secret", "main"),
        remote("unsafeScp", "git:token@example.com:team/crm.git", "main"),
        remote("unsafe", "file:///tmp/crm.git", "main")
    ));

    Path policyPath = appHome.resolve(AppVcsPolicyStore.POLICY_RELATIVE_PATH);
    Files.createDirectories(policyPath.getParent());
    Files.write(policyPath, Json.toPrettyJsonString(rawPolicy).getBytes(StandardCharsets.UTF_8));

    AppVcsPolicy sanitized = store.loadSanitized("crm");

    assertEquals("https://example.com/team/crm.git", sanitized.getRemoteUri());
    assertNull(sanitized.getTrunkBranch());
    assertNull(sanitized.getDefaultBranch());
    assertNull(sanitized.getRemotes().get(0).getDefaultBranch());
    assertEquals("https://example.com/team/crm.git", sanitized.getRemotes().get(0).getUrl());
    assertEquals("ssh://example.com/team/crm.git", sanitized.getRemotes().get(1).getUrl());
    assertNull(sanitized.getRemotes().get(2).getUrl());
    assertNull(sanitized.getRemotes().get(3).getUrl());
    assertNull(sanitized.getRemotes().get(4).getUrl());
    assertNull(sanitized.getRemotes().get(5).getUrl());
    assertNull(sanitized.getRemotes().get(6).getUrl());
    assertNull(sanitized.getRemotes().get(7).getUrl());
    assertNull(sanitized.getRemotes().get(8).getUrl());
    assertNull(sanitized.getRemotes().get(9).getUrl());
    assertNull(sanitized.getRemotes().get(10).getUrl());
    assertNull(sanitized.getRemotes().get(11).getUrl());
    assertNull(sanitized.getRemotes().get(12).getUrl());
    assertNull(sanitized.getRemotes().get(13).getUrl());
    assertNull(sanitized.getRemotes().get(14).getUrl());
    assertNull(sanitized.getRemotes().get(15).getUrl());
  }

  private static AppVcsPolicy policyWithLegacyRemote(String remoteUri) {
    AppVcsPolicy policy = new AppVcsPolicy();
    policy.setRemoteName("origin");
    policy.setRemoteUri(remoteUri);
    policy.setDefaultBranch("main");
    return policy;
  }

  private static AppVcsRemoteDefinition remote(String name, String url, String defaultBranch) {
    AppVcsRemoteDefinition remote = new AppVcsRemoteDefinition();
    remote.setName(name);
    remote.setUrl(url);
    remote.setDefaultBranch(defaultBranch);
    return remote;
  }
}
