package com.ouroboros.dev.runtime.service;

import java.util.List;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.ProfileArtifactOverlay;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.RuntimeKind;

public interface RuntimeLockService {
  Try<ResolvedRuntimeLock> resolveAndPersistLatest(String appId,
                                                   String profileId,
                                                   RuntimeKind runtimeKind,
                                                   String baseBomVersion,
                                                   List<AppArtifactBinding> appArtifacts,
                                                   List<ProfileArtifactOverlay> profileOverlays);

  Optional<ResolvedRuntimeLock> findLatestSuccessful(String appId, String profileId, RuntimeKind runtimeKind);
}
