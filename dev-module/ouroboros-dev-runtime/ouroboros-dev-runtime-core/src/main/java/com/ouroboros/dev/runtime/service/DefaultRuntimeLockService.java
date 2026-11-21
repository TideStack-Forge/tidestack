package com.ouroboros.dev.runtime.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.ProfileArtifactOverlay;
import com.ouroboros.dev.runtime.model.ProfileArtifactOverlayAction;
import com.ouroboros.dev.runtime.model.RepositoryRef;
import com.ouroboros.dev.runtime.model.ResolvedArtifactSourceType;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeArtifact;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeLockStatus;

public class DefaultRuntimeLockService implements RuntimeLockService {
  private final RuntimeLockStore lockStore;
  private final Supplier<Instant> nowSupplier;

  public DefaultRuntimeLockService(RuntimeLockStore lockStore) {
    this(lockStore, Instant::now);
  }

  public DefaultRuntimeLockService(RuntimeLockStore lockStore, Supplier<Instant> nowSupplier) {
    this.lockStore = lockStore;
    this.nowSupplier = nowSupplier;
  }

  @Override
  public Try<ResolvedRuntimeLock> resolveAndPersistLatest(String appId,
                                                          String profileId,
                                                          RuntimeKind runtimeKind,
                                                          String baseBomVersion,
                                                          List<AppArtifactBinding> appArtifacts,
                                                          List<ProfileArtifactOverlay> profileOverlays) {
    RuntimeLockKey slot = RuntimeLockKey.of(appId, profileId, runtimeKind);
    return Try.of(() -> resolve(appId, profileId, runtimeKind, baseBomVersion, appArtifacts, profileOverlays))
        .map(lock -> {
          lockStore.saveLatestSuccessful(RuntimeLockRecord.of(slot, lock));
          return lock;
        });
  }

  @Override
  public Optional<ResolvedRuntimeLock> findLatestSuccessful(String appId, String profileId, RuntimeKind runtimeKind) {
    RuntimeLockKey slot = RuntimeLockKey.of(appId, profileId, runtimeKind);
    return lockStore.findLatestSuccessful(slot).map(RuntimeLockRecord::getLock);
  }

  private ResolvedRuntimeLock resolve(String appId,
                                      String profileId,
                                      RuntimeKind runtimeKind,
                                      String baseBomVersion,
                                      List<AppArtifactBinding> appArtifacts,
                                      List<ProfileArtifactOverlay> profileOverlays) {
    List<AppArtifactBinding> baseTopLevel = enabledArtifacts(appArtifacts);
    List<AppArtifactBinding> overlays = normalizeArtifacts(baseTopLevel);
    List<ProfileArtifactOverlay> normalizedOverlays = normalizeOverlays(profileOverlays);
    List<ResolvedRuntimeArtifact> suppressed = new ArrayList<ResolvedRuntimeArtifact>();
    RepositoryRef profileDefaultRepository = resolveProfileDefaultRepository(normalizedOverlays);

    for (ProfileArtifactOverlay overlay : normalizedOverlays) {
      applyOverlay(overlays, overlay, profileDefaultRepository, suppressed);
    }

    List<AppArtifactBinding> topLevelArtifacts = applyProfileDefaultRepository(overlays, profileDefaultRepository);
    List<ResolvedRuntimeArtifact> resolvedArtifacts = new ArrayList<ResolvedRuntimeArtifact>();
    for (AppArtifactBinding binding : topLevelArtifacts) {
      resolvedArtifacts.add(toResolvedArtifact(binding));
    }
    List<String> diagnostics = new ArrayList<String>();
    diagnostics.add("base BOM first: " + String.valueOf(baseBomVersion));
    diagnostics.add("overlay applied count: " + normalizedOverlays.size());

    return ResolvedRuntimeLock.builder()
        .appId(appId)
        .profileId(profileId)
        .runtimeKind(runtimeKind)
        .status(RuntimeLockStatus.READY)
        .resolvedAt(nowSupplier.get())
        .baseBomVersion(baseBomVersion)
        .topLevelArtifacts(topLevelArtifacts)
        .resolvedArtifacts(resolvedArtifacts)
        .suppressedArtifacts(suppressed)
        .diagnostics(diagnostics)
        .build();
  }

  private List<AppArtifactBinding> enabledArtifacts(List<AppArtifactBinding> appArtifacts) {
    if (appArtifacts == null || appArtifacts.isEmpty()) {
      return Collections.emptyList();
    }
    List<AppArtifactBinding> enabled = new ArrayList<AppArtifactBinding>();
    for (AppArtifactBinding appArtifact : appArtifacts) {
      if (appArtifact != null && appArtifact.isEnabled()) {
        enabled.add(appArtifact);
      }
    }
    return enabled;
  }

  private List<AppArtifactBinding> normalizeArtifacts(List<AppArtifactBinding> artifacts) {
    List<AppArtifactBinding> normalized = new ArrayList<AppArtifactBinding>();
    for (AppArtifactBinding artifact : artifacts) {
      if (artifact != null) {
        normalized.add(artifact);
      }
    }
    return normalized;
  }

  private List<ProfileArtifactOverlay> normalizeOverlays(List<ProfileArtifactOverlay> profileOverlays) {
    if (profileOverlays == null || profileOverlays.isEmpty()) {
      return Collections.emptyList();
    }
    List<ProfileArtifactOverlay> normalized = new ArrayList<ProfileArtifactOverlay>();
    for (ProfileArtifactOverlay overlay : profileOverlays) {
      if (overlay != null) {
        normalized.add(overlay);
      }
    }
    return normalized;
  }

  private RepositoryRef resolveProfileDefaultRepository(List<ProfileArtifactOverlay> overlays) {
    for (ProfileArtifactOverlay overlay : overlays) {
      if (overlay.getDefaultRepositoryRef() != null) {
        return overlay.getDefaultRepositoryRef();
      }
    }
    return null;
  }

  private void applyOverlay(List<AppArtifactBinding> current,
                            ProfileArtifactOverlay overlay,
                            RepositoryRef profileDefaultRepository,
                            List<ResolvedRuntimeArtifact> suppressedArtifacts) {
    if (overlay.getAction() == ProfileArtifactOverlayAction.ADD) {
      current.add(applyRepositoryPrecedence(overlay.getReplacementArtifact(), profileDefaultRepository));
      return;
    }
    int index = findIndexByKey(current, overlay.getTargetArtifactKey());
    if (index < 0) {
      throw new IllegalStateException("overlay target not found: " + overlay.getTargetArtifactKey());
    }
    AppArtifactBinding previous = current.get(index);
    suppressedArtifacts.add(toResolvedArtifact(previous));
    if (overlay.getAction() == ProfileArtifactOverlayAction.DISABLE) {
      current.remove(index);
      return;
    }
    if (overlay.getAction() == ProfileArtifactOverlayAction.REPLACE) {
      current.set(index, applyRepositoryPrecedence(overlay.getReplacementArtifact(), profileDefaultRepository));
      return;
    }
    throw new IllegalStateException("unsupported overlay action: " + overlay.getAction());
  }

  private int findIndexByKey(List<AppArtifactBinding> artifacts, String artifactKey) {
    if (artifactKey == null) {
      return -1;
    }
    for (int i = 0; i < artifacts.size(); i++) {
      if (artifactKey.equals(artifactKey(artifacts.get(i)))) {
        return i;
      }
    }
    return -1;
  }

  private List<AppArtifactBinding> applyProfileDefaultRepository(List<AppArtifactBinding> artifacts, RepositoryRef profileDefaultRepository) {
    List<AppArtifactBinding> resolved = new ArrayList<AppArtifactBinding>();
    for (AppArtifactBinding artifact : artifacts) {
      resolved.add(applyRepositoryPrecedence(artifact, profileDefaultRepository));
    }
    return resolved;
  }

  private AppArtifactBinding applyRepositoryPrecedence(AppArtifactBinding artifact, RepositoryRef profileDefaultRepository) {
    RepositoryRef effectiveRepository = artifact.getRepositoryRef() == null ? profileDefaultRepository : artifact.getRepositoryRef();
    return AppArtifactBinding.builder()
        .appId(artifact.getAppId())
        .groupId(artifact.getGroupId())
        .artifactId(artifact.getArtifactId())
        .version(artifact.getVersion())
        .packaging(artifact.getPackaging())
        .classifier(artifact.getClassifier())
        .enabled(artifact.isEnabled())
        .usage(artifact.getUsage())
        .repositoryRef(effectiveRepository)
        .build();
  }

  private ResolvedRuntimeArtifact toResolvedArtifact(AppArtifactBinding artifact) {
    return ResolvedRuntimeArtifact.builder()
        .groupId(artifact.getGroupId())
        .artifactId(artifact.getArtifactId())
        .version(artifact.getVersion())
        .packaging(artifact.getPackaging())
        .classifier(artifact.getClassifier())
        .repositoryRef(artifact.getRepositoryRef())
        .effectiveScope(normalizeScope(artifact.getUsage()))
        .sourceType(ResolvedArtifactSourceType.TOP_LEVEL_BINDING)
        .build();
  }

  private String normalizeScope(String usage) {
    if (usage == null || usage.trim().isEmpty()) {
      return "runtime";
    }
    return usage.trim().toLowerCase(Locale.ROOT);
  }

  private String artifactKey(AppArtifactBinding artifact) {
    return artifact.getGroupId() + ":" + artifact.getArtifactId();
  }
}
