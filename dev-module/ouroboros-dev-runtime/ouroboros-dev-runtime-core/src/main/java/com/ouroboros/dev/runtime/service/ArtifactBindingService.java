package com.ouroboros.dev.runtime.service;

import java.util.List;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.ProfileArtifactOverlay;
import com.ouroboros.dev.runtime.model.RepositoryRef;
import com.ouroboros.dev.runtime.model.RuntimeKind;

public interface ArtifactBindingService {
  Try<Void> replaceRepositoryRefs(String appId, List<RepositoryRef> repositoryRefs);

  Try<Void> replaceAppArtifactBindings(String appId, List<AppArtifactBinding> appArtifactBindings);

  Try<Void> replaceProfileArtifactOverlays(String appId, String profileId, List<ProfileArtifactOverlay> overlays);

  Try<Void> requestResolve(String appId, String profileId, RuntimeKind runtimeKind);

  interface StorageGateway {
    Try<Void> saveRepositoryRefs(String appId, List<RepositoryRef> repositoryRefs);

    Try<Void> saveAppArtifactBindings(String appId, List<AppArtifactBinding> appArtifactBindings);

    Try<Void> saveProfileArtifactOverlays(String appId, String profileId, List<ProfileArtifactOverlay> overlays);

    Try<Void> markResolveRequested(String appId, String profileId, RuntimeKind runtimeKind);
  }
}
