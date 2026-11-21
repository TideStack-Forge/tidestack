package com.ouroboros.dev.runtime.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeLockModelSurfaceTest {

  @Test
  void shouldExposeRuntimeLockStoredShapesRequiredByResolutionLayer() {
    RepositoryRef repositoryRef = RepositoryRef.builder()
        .repositoryId("central")
        .repositoryType(RepositoryType.MAVEN)
        .repositoryUrl("https://repo1.maven.org/maven2")
        .credentialRef("cred-central")
        .defaultEnabled(true)
        .build();

    AppArtifactBinding appArtifact = AppArtifactBinding.builder()
        .appId("crm")
        .groupId("com.example")
        .artifactId("crm-runtime")
        .version("1.0.0")
        .packaging("jar")
        .classifier("exec")
        .enabled(true)
        .usage("runtime")
        .repositoryRef(repositoryRef)
        .build();

    ProfileArtifactOverlay disableOverlay = ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.DISABLE)
        .targetArtifactKey("com.example:crm-runtime")
        .defaultRepositoryRef(repositoryRef)
        .build();
    ProfileArtifactOverlay replaceOverlay = ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.REPLACE)
        .targetArtifactKey("com.example:crm-runtime")
        .replacementArtifact(appArtifact)
        .defaultRepositoryRef(repositoryRef)
        .build();
    ProfileArtifactOverlay addOverlay = ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.ADD)
        .replacementArtifact(appArtifact)
        .defaultRepositoryRef(repositoryRef)
        .build();

    ResolvedRuntimeArtifact baseArtifact = ResolvedRuntimeArtifact.builder()
        .groupId("com.example")
        .artifactId("crm-runtime")
        .version("1.0.0")
        .packaging("jar")
        .classifier("exec")
        .checksum("sha256:runtime")
        .repositoryRef(repositoryRef)
        .effectiveScope("runtime")
        .sourceType(ResolvedArtifactSourceType.TOP_LEVEL_BINDING)
        .build();

    ResolvedRuntimeArtifact overlayArtifact = ResolvedRuntimeArtifact.builder()
        .groupId("com.example")
        .artifactId("crm-runtime-overlay")
        .version("1.0.1")
        .packaging("jar")
        .classifier("exec")
        .checksum("sha256:overlay")
        .repositoryRef(repositoryRef)
        .effectiveScope("runtime")
        .sourceType(ResolvedArtifactSourceType.TRANSITIVE_DEPENDENCY)
        .build();

    ResolvedRuntimeLock runtimeLock = ResolvedRuntimeLock.builder()
        .appId("crm")
        .profileId("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .status(RuntimeLockStatus.READY)
        .resolvedAt(Instant.parse("2026-03-23T10:15:30Z"))
        .baseBomVersion("2026.03")
        .topLevelArtifacts(Arrays.asList(appArtifact))
        .resolvedArtifacts(Arrays.asList(baseArtifact))
        .suppressedArtifacts(Arrays.asList(overlayArtifact))
        .diagnostics(Arrays.asList("resolved from base bom"))
        .build();

    assertEquals("central", repositoryRef.getRepositoryId());
    assertEquals(RepositoryType.MAVEN, repositoryRef.getRepositoryType());
    assertEquals("https://repo1.maven.org/maven2", repositoryRef.getRepositoryUrl());
    assertEquals("cred-central", repositoryRef.getCredentialRef());
    assertEquals(true, repositoryRef.isDefaultEnabled());

    assertEquals("crm", appArtifact.getAppId());
    assertEquals("com.example", appArtifact.getGroupId());
    assertEquals("crm-runtime", appArtifact.getArtifactId());
    assertEquals("1.0.0", appArtifact.getVersion());
    assertEquals("jar", appArtifact.getPackaging());
    assertEquals("exec", appArtifact.getClassifier());
    assertEquals(true, appArtifact.isEnabled());
    assertEquals("runtime", appArtifact.getUsage());
    assertEquals(repositoryRef, appArtifact.getRepositoryRef());

    assertEquals("crm", disableOverlay.getAppId());
    assertEquals("alice", disableOverlay.getProfileId());
    assertEquals(ProfileArtifactOverlayAction.DISABLE, disableOverlay.getAction());
    assertEquals("com.example:crm-runtime", disableOverlay.getTargetArtifactKey());
    assertEquals(null, disableOverlay.getReplacementArtifact());
    assertEquals(repositoryRef, disableOverlay.getDefaultRepositoryRef());
    assertEquals(ProfileArtifactOverlayAction.REPLACE, replaceOverlay.getAction());
    assertEquals("com.example:crm-runtime", replaceOverlay.getTargetArtifactKey());
    assertEquals(appArtifact, replaceOverlay.getReplacementArtifact());
    assertEquals(ProfileArtifactOverlayAction.ADD, addOverlay.getAction());
    assertEquals(null, addOverlay.getTargetArtifactKey());
    assertEquals(appArtifact, addOverlay.getReplacementArtifact());

    assertEquals("sha256:runtime", baseArtifact.getChecksum());
    assertEquals(repositoryRef, baseArtifact.getRepositoryRef());
    assertEquals("runtime", baseArtifact.getEffectiveScope());
    assertEquals(ResolvedArtifactSourceType.TOP_LEVEL_BINDING, baseArtifact.getSourceType());
    assertEquals("sha256:overlay", overlayArtifact.getChecksum());
    assertEquals(repositoryRef, overlayArtifact.getRepositoryRef());
    assertEquals("runtime", overlayArtifact.getEffectiveScope());
    assertEquals(ResolvedArtifactSourceType.TRANSITIVE_DEPENDENCY, overlayArtifact.getSourceType());

    assertEquals("crm", runtimeLock.getAppId());
    assertEquals("alice", runtimeLock.getProfileId());
    assertEquals(RuntimeKind.TEST_APP, runtimeLock.getRuntimeKind());
    assertEquals(RuntimeLockStatus.READY, runtimeLock.getStatus());
    assertEquals(Instant.parse("2026-03-23T10:15:30Z"), runtimeLock.getResolvedAt());
    assertEquals("2026.03", runtimeLock.getBaseBomVersion());
    assertEquals(appArtifact, runtimeLock.getTopLevelArtifacts().get(0));
    assertEquals(baseArtifact, runtimeLock.getResolvedArtifacts().get(0));
    assertEquals(overlayArtifact, runtimeLock.getSuppressedArtifacts().get(0));
    assertEquals("resolved from base bom", runtimeLock.getDiagnostics().get(0));
    assertEquals(RuntimeLockStatus.RESOLVING, RuntimeLockStatus.valueOf("RESOLVING"));
    assertEquals(RuntimeLockStatus.FAILED, RuntimeLockStatus.valueOf("FAILED"));
  }

  @Test
  void shouldDefensivelyCopyRuntimeLockCollectionsAndExposeImmutableViews() {
    RepositoryRef repositoryRef = RepositoryRef.builder()
        .repositoryId("snapshot")
        .repositoryType(RepositoryType.MAVEN)
        .repositoryUrl("https://repo.example.com/snapshot")
        .defaultEnabled(false)
        .build();

    AppArtifactBinding topLevelArtifact = AppArtifactBinding.builder()
        .appId("crm")
        .groupId("com.example")
        .artifactId("crm-runtime")
        .version("1.0.0")
        .packaging("jar")
        .enabled(true)
        .usage("runtime")
        .repositoryRef(repositoryRef)
        .build();
    ResolvedRuntimeArtifact resolvedArtifact = ResolvedRuntimeArtifact.builder()
        .groupId("com.example")
        .artifactId("crm-runtime")
        .version("1.0.0")
        .packaging("jar")
        .checksum("sha256:ready")
        .repositoryRef(repositoryRef)
        .effectiveScope("runtime")
        .sourceType(ResolvedArtifactSourceType.TOP_LEVEL_BINDING)
        .build();
    ResolvedRuntimeArtifact suppressedArtifact = ResolvedRuntimeArtifact.builder()
        .groupId("com.example")
        .artifactId("crm-runtime-old")
        .version("0.9.0")
        .packaging("jar")
        .checksum("sha256:old")
        .repositoryRef(repositoryRef)
        .effectiveScope("runtime")
        .sourceType(ResolvedArtifactSourceType.TRANSITIVE_DEPENDENCY)
        .build();

    List<AppArtifactBinding> topLevelArtifacts = new ArrayList<AppArtifactBinding>();
    topLevelArtifacts.add(topLevelArtifact);
    List<ResolvedRuntimeArtifact> resolvedArtifacts = new ArrayList<ResolvedRuntimeArtifact>();
    resolvedArtifacts.add(resolvedArtifact);
    List<ResolvedRuntimeArtifact> suppressedArtifacts = new ArrayList<ResolvedRuntimeArtifact>();
    suppressedArtifacts.add(suppressedArtifact);
    List<String> diagnostics = new ArrayList<String>();
    diagnostics.add("resolved");

    ResolvedRuntimeLock runtimeLock = ResolvedRuntimeLock.builder()
        .appId("crm")
        .profileId("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .status(RuntimeLockStatus.READY)
        .resolvedAt(Instant.parse("2026-03-23T10:15:30Z"))
        .baseBomVersion("2026.03")
        .topLevelArtifacts(topLevelArtifacts)
        .resolvedArtifacts(resolvedArtifacts)
        .suppressedArtifacts(suppressedArtifacts)
        .diagnostics(diagnostics)
        .build();

    topLevelArtifacts.clear();
    resolvedArtifacts.clear();
    suppressedArtifacts.clear();
    diagnostics.clear();

    assertEquals(1, runtimeLock.getTopLevelArtifacts().size());
    assertEquals(1, runtimeLock.getResolvedArtifacts().size());
    assertEquals(1, runtimeLock.getSuppressedArtifacts().size());
    assertEquals(1, runtimeLock.getDiagnostics().size());
    assertThrows(UnsupportedOperationException.class, () -> runtimeLock.getTopLevelArtifacts().add(topLevelArtifact));
    assertThrows(UnsupportedOperationException.class, () -> runtimeLock.getResolvedArtifacts().add(resolvedArtifact));
    assertThrows(UnsupportedOperationException.class, () -> runtimeLock.getSuppressedArtifacts().add(suppressedArtifact));
    assertThrows(UnsupportedOperationException.class, () -> runtimeLock.getDiagnostics().add("mutated"));
  }

  @Test
  void shouldRejectContradictoryProfileArtifactOverlayStates() {
    RepositoryRef repositoryRef = RepositoryRef.builder()
        .repositoryId("central")
        .repositoryType(RepositoryType.MAVEN)
        .repositoryUrl("https://repo1.maven.org/maven2")
        .defaultEnabled(true)
        .build();
    AppArtifactBinding appArtifact = AppArtifactBinding.builder()
        .appId("crm")
        .groupId("com.example")
        .artifactId("crm-runtime")
        .version("1.0.0")
        .packaging("jar")
        .enabled(true)
        .usage("runtime")
        .repositoryRef(repositoryRef)
        .build();

    assertThrows(IllegalArgumentException.class, () -> ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.DISABLE)
        .replacementArtifact(appArtifact)
        .build());
    assertThrows(IllegalArgumentException.class, () -> ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.DISABLE)
        .build());
    assertThrows(IllegalArgumentException.class, () -> ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.REPLACE)
        .targetArtifactKey("com.example:crm-runtime")
        .build());
    assertThrows(IllegalArgumentException.class, () -> ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.REPLACE)
        .replacementArtifact(appArtifact)
        .build());
    assertThrows(IllegalArgumentException.class, () -> ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.ADD)
        .build());
    assertThrows(IllegalArgumentException.class, () -> ProfileArtifactOverlay.builder()
        .appId("crm")
        .profileId("alice")
        .action(ProfileArtifactOverlayAction.ADD)
        .targetArtifactKey("com.example:crm-runtime")
        .replacementArtifact(appArtifact)
        .build());
  }
}
