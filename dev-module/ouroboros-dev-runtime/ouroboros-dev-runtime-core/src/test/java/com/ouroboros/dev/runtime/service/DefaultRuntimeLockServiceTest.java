package com.ouroboros.dev.runtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.ProfileArtifactOverlay;
import com.ouroboros.dev.runtime.model.ProfileArtifactOverlayAction;
import com.ouroboros.dev.runtime.model.RepositoryRef;
import com.ouroboros.dev.runtime.model.RepositoryType;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeLockStatus;

class DefaultRuntimeLockServiceTest {

  @Test
  void shouldResolveAndPersistLatestSuccessfulLockWithOverlayMergingAndRepositoryPrecedence() {
    InMemoryRuntimeLockStore lockStore = new InMemoryRuntimeLockStore();
    RuntimeLockService lockService = new DefaultRuntimeLockService(lockStore, () -> Instant.parse("2026-03-24T08:00:00Z"));
    RepositoryRef artifactRepo = repository("artifact-repo", "https://artifact.repo");
    RepositoryRef profileDefaultRepo = repository("profile-default", "https://profile.repo");

    AppArtifactBinding appArtifactWithRepo = appArtifact("crm", "com.example", "core-app", "1.0.0", artifactRepo);
    AppArtifactBinding appArtifactWithoutRepo = appArtifact("crm", "com.example", "addon-app", "1.0.0", null);
    AppArtifactBinding replacementWithoutRepo = appArtifact("crm", "com.example", "addon-app", "2.0.0", null);
    AppArtifactBinding addedWithoutRepo = appArtifact("crm", "com.example", "extra-app", "3.0.0", null);

    ProfileArtifactOverlay replaceOverlay = ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.REPLACE)
        .targetArtifactKey("com.example:addon-app")
        .replacementArtifact(replacementWithoutRepo)
        .defaultRepositoryRef(profileDefaultRepo)
        .build();
    ProfileArtifactOverlay addOverlay = ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.ADD)
        .replacementArtifact(addedWithoutRepo)
        .defaultRepositoryRef(profileDefaultRepo)
        .build();

    Try<ResolvedRuntimeLock> result = lockService.resolveAndPersistLatest(
        "crm",
        "alice",
        RuntimeKind.TEST_APP,
        "2026.03",
        Arrays.asList(appArtifactWithRepo, appArtifactWithoutRepo),
        Arrays.asList(replaceOverlay, addOverlay)
    );

    assertTrue(result.isSuccess());
    ResolvedRuntimeLock lock = result.get();
    assertEquals(RuntimeLockStatus.READY, lock.getStatus());
    assertEquals(3, lock.getTopLevelArtifacts().size());
    assertEquals("artifact-repo", lock.getTopLevelArtifacts().get(0).getRepositoryRef().getRepositoryId());
    assertEquals("profile-default", lock.getTopLevelArtifacts().get(1).getRepositoryRef().getRepositoryId());
    assertEquals("profile-default", lock.getTopLevelArtifacts().get(2).getRepositoryRef().getRepositoryId());
    assertEquals(1, lock.getSuppressedArtifacts().size());
    assertEquals("addon-app", lock.getSuppressedArtifacts().get(0).getArtifactId());
    assertTrue(lock.getDiagnostics().contains("base BOM first: 2026.03"));
    assertTrue(lockStore.findLatestSuccessful(RuntimeLockKey.of("crm", "alice", RuntimeKind.TEST_APP)).isPresent());
  }

  @Test
  void shouldOverwriteLatestSuccessfulLockOnNextSuccess() {
    InMemoryRuntimeLockStore lockStore = new InMemoryRuntimeLockStore();
    RuntimeLockService lockService = new DefaultRuntimeLockService(lockStore, () -> Instant.parse("2026-03-24T09:00:00Z"));

    lockService.resolveAndPersistLatest(
        "crm",
        "alice",
        RuntimeKind.TEST_APP,
        "2026.03",
        Arrays.asList(appArtifact("crm", "com.example", "core-app", "1.0.0", null)),
        Collections.emptyList()
    );
    lockService.resolveAndPersistLatest(
        "crm",
        "alice",
        RuntimeKind.TEST_APP,
        "2026.04",
        Arrays.asList(appArtifact("crm", "com.example", "core-app", "1.1.0", null)),
        Collections.emptyList()
    );

    ResolvedRuntimeLock latest = lockStore.findLatestSuccessful(RuntimeLockKey.of("crm", "alice", RuntimeKind.TEST_APP)).get().getLock();
    assertEquals("2026.04", latest.getBaseBomVersion());
    assertEquals("1.1.0", latest.getTopLevelArtifacts().get(0).getVersion());
  }

  @Test
  void shouldPreservePreviousLatestWhenNewResolutionFails() {
    InMemoryRuntimeLockStore lockStore = new InMemoryRuntimeLockStore();
    RuntimeLockService lockService = new DefaultRuntimeLockService(lockStore, () -> Instant.parse("2026-03-24T10:00:00Z"));

    lockService.resolveAndPersistLatest(
        "crm",
        "alice",
        RuntimeKind.TEST_APP,
        "2026.03",
        Arrays.asList(appArtifact("crm", "com.example", "core-app", "1.0.0", null)),
        Collections.emptyList()
    );

    ProfileArtifactOverlay badReplace = ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.REPLACE)
        .targetArtifactKey("com.example:not-found")
        .replacementArtifact(appArtifact("crm", "com.example", "new-app", "2.0.0", null))
        .build();

    Try<ResolvedRuntimeLock> failed = lockService.resolveAndPersistLatest(
        "crm",
        "alice",
        RuntimeKind.TEST_APP,
        "2026.04",
        Arrays.asList(appArtifact("crm", "com.example", "core-app", "1.0.0", null)),
        Arrays.asList(badReplace)
    );

    assertTrue(failed.isFailure());
    ResolvedRuntimeLock latest = lockStore.findLatestSuccessful(RuntimeLockKey.of("crm", "alice", RuntimeKind.TEST_APP)).get().getLock();
    assertEquals("2026.03", latest.getBaseBomVersion());
    assertEquals("1.0.0", latest.getTopLevelArtifacts().get(0).getVersion());
  }

  @Test
  void shouldLeaveLatestEmptyWhenFirstResolutionFails() {
    InMemoryRuntimeLockStore lockStore = new InMemoryRuntimeLockStore();
    RuntimeLockService lockService = new DefaultRuntimeLockService(lockStore, () -> Instant.parse("2026-03-24T11:00:00Z"));

    ProfileArtifactOverlay badDisable = ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.DISABLE)
        .targetArtifactKey("com.example:not-found")
        .build();

    Try<ResolvedRuntimeLock> failed = lockService.resolveAndPersistLatest(
        "crm",
        "alice",
        RuntimeKind.TEST_APP,
        "2026.03",
        Arrays.asList(appArtifact("crm", "com.example", "core-app", "1.0.0", null)),
        Arrays.asList(badDisable)
    );

    assertTrue(failed.isFailure());
    assertFalse(lockStore.findLatestSuccessful(RuntimeLockKey.of("crm", "alice", RuntimeKind.TEST_APP)).isPresent());
  }

  private RepositoryRef repository(String id, String url) {
    return RepositoryRef.builder()
        .repositoryId(id)
        .repositoryType(RepositoryType.MAVEN)
        .repositoryUrl(url)
        .defaultEnabled(true)
        .build();
  }

  private AppArtifactBinding appArtifact(String appId,
                                         String groupId,
                                         String artifactId,
                                         String version,
                                         RepositoryRef repositoryRef) {
    return AppArtifactBinding.builder()
        .appId(appId)
        .groupId(groupId)
        .artifactId(artifactId)
        .version(version)
        .packaging("jar")
        .enabled(true)
        .usage("runtime")
        .repositoryRef(repositoryRef)
        .build();
  }
}
