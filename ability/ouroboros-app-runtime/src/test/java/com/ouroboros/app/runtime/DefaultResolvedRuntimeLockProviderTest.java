package com.ouroboros.app.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.AppContext;

class DefaultResolvedRuntimeLockProviderTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldReadLatestResolvedArtifactInputFromMetadataLockFile() throws Exception {
    Path lockFile = latestLockFile("alice/dev", "TEST_APP");
    Files.createDirectories(lockFile.getParent());
    Files.write(lockFile, ("{\n"
        + "  \"slot\" : {\n"
        + "    \"appId\" : \"crm\",\n"
        + "    \"profileId\" : \"alice/dev\",\n"
        + "    \"runtimeKind\" : \"TEST_APP\"\n"
        + "  },\n"
        + "  \"savedAt\" : \"2026-03-24T12:00:00Z\",\n"
        + "  \"lock\" : {\n"
        + "    \"appId\" : \"crm\",\n"
        + "    \"profileId\" : \"alice/dev\",\n"
        + "    \"runtimeKind\" : \"TEST_APP\",\n"
        + "    \"status\" : \"READY\",\n"
        + "    \"resolvedAt\" : \"2026-03-24T12:00:00Z\",\n"
        + "    \"baseBomVersion\" : \"2026.03\",\n"
        + "    \"topLevelArtifacts\" : [ {\n"
        + "      \"groupId\" : \"com.example\",\n"
        + "      \"artifactId\" : \"crm-runtime\",\n"
        + "      \"version\" : \"1.0.0\"\n"
        + "    } ],\n"
        + "    \"resolvedArtifacts\" : [ ],\n"
        + "    \"suppressedArtifacts\" : [ ],\n"
        + "    \"diagnostics\" : [ ]\n"
        + "  }\n"
        + "}").getBytes(StandardCharsets.UTF_8));

    DefaultResolvedRuntimeLockProvider provider = new DefaultResolvedRuntimeLockProvider(appContext(), "crm", "alice/dev", "TEST_APP");

    Optional<Map<String, Object>> found = provider.findLatestResolvedArtifactInput();

    assertTrue(found.isPresent());
    assertEquals("2026.03", found.get().get("baseBomVersion"));
    assertEquals("READY", found.get().get("status"));
    assertEquals(Instant.parse("2026-03-24T12:00:00Z").toString(), found.get().get("resolvedAt"));
    assertEquals(Arrays.asList("com.example:crm-runtime:1.0.0"), found.get().get("artifacts"));
  }

  @Test
  void shouldReturnEmptyWhenLatestLockFileMissing() {
    DefaultResolvedRuntimeLockProvider provider = new DefaultResolvedRuntimeLockProvider(appContext(), "crm", "alice/dev", "TEST_APP");

    Optional<Map<String, Object>> found = provider.findLatestResolvedArtifactInput();

    assertFalse(found.isPresent());
  }

  @Test
  void shouldThrowConsumptionExceptionWhenLatestLockJsonMalformed() throws Exception {
    Path lockFile = latestLockFile("alice/dev", "TEST_APP");
    Files.createDirectories(lockFile.getParent());
    Files.write(lockFile, "not-json".getBytes(StandardCharsets.UTF_8));
    DefaultResolvedRuntimeLockProvider provider = new DefaultResolvedRuntimeLockProvider(appContext(), "crm", "alice/dev", "TEST_APP");

    assertThrows(RuntimeLockConsumptionException.class, provider::findLatestResolvedArtifactInput);
  }

  @Test
  void shouldReturnEmptyWhenLatestLockStatusIsNotReady() throws Exception {
    Path lockFile = latestLockFile("alice/dev", "TEST_APP");
    Files.createDirectories(lockFile.getParent());
    Files.write(lockFile, ("{\n"
        + "  \"lock\" : {\n"
        + "    \"status\" : \"FAILED\",\n"
        + "    \"topLevelArtifacts\" : [ ]\n"
        + "  }\n"
        + "}").getBytes(StandardCharsets.UTF_8));
    DefaultResolvedRuntimeLockProvider provider = new DefaultResolvedRuntimeLockProvider(appContext(), "crm", "alice/dev", "TEST_APP");

    Optional<Map<String, Object>> found = provider.findLatestResolvedArtifactInput();

    assertFalse(found.isPresent());
  }

  @Test
  void shouldReturnEmptyWhenLatestLockBodyMissingOrEmpty() throws Exception {
    Path lockFile = latestLockFile("alice/dev", "TEST_APP");
    Files.createDirectories(lockFile.getParent());
    Files.write(lockFile, ("{\n"
        + "  \"slot\" : {\n"
        + "    \"appId\" : \"crm\"\n"
        + "  },\n"
        + "  \"savedAt\" : \"2026-03-24T12:00:00Z\",\n"
        + "  \"lock\" : { }\n"
        + "}").getBytes(StandardCharsets.UTF_8));
    DefaultResolvedRuntimeLockProvider provider = new DefaultResolvedRuntimeLockProvider(appContext(), "crm", "alice/dev", "TEST_APP");

    Optional<Map<String, Object>> found = provider.findLatestResolvedArtifactInput();

    assertFalse(found.isPresent());
  }

  private AppContext appContext() {
    return () -> tempDir.toString();
  }

  private Path latestLockFile(String profile, String runtimeKind) {
    String profileStorageKey = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(profile.getBytes(StandardCharsets.UTF_8));
    return tempDir.resolve(Paths.get("runtime-lock", "latest", profileStorageKey, runtimeKind + ".json"));
  }
}
