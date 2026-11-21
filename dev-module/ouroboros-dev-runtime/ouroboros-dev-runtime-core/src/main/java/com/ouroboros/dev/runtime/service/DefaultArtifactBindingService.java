package com.ouroboros.dev.runtime.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.ProfileArtifactOverlay;
import com.ouroboros.dev.runtime.model.RepositoryRef;
import com.ouroboros.dev.runtime.model.RuntimeKind;

public class DefaultArtifactBindingService implements ArtifactBindingService {
  private final StorageGateway storageGateway;

  public DefaultArtifactBindingService(StorageGateway storageGateway) {
    this.storageGateway = storageGateway;
  }

  @Override
  public Try<Void> replaceRepositoryRefs(String appId, List<RepositoryRef> repositoryRefs) {
    return Try.of(() -> normalizeAppId(appId))
        .flatMap(ignore -> {
          List<RepositoryRef> normalizedRefs = immutableCopy(repositoryRefs);
          normalizedRefs.forEach(this::validateRepositoryRef);
          return storageGateway.saveRepositoryRefs(appId, normalizedRefs);
        });
  }

  @Override
  public Try<Void> replaceAppArtifactBindings(String appId, List<AppArtifactBinding> appArtifactBindings) {
    return Try.of(() -> normalizeAppId(appId))
        .flatMap(ignore -> {
          List<AppArtifactBinding> normalizedBindings = immutableCopy(appArtifactBindings);
          normalizedBindings.forEach(binding -> validateAppArtifactBinding(appId, binding));
          return storageGateway.saveAppArtifactBindings(appId, normalizedBindings);
        });
  }

  @Override
  public Try<Void> replaceProfileArtifactOverlays(String appId, String profileId, List<ProfileArtifactOverlay> overlays) {
    return Try.of(() -> normalizeProfileId(appId, profileId))
        .flatMap(ignore -> {
          List<ProfileArtifactOverlay> normalizedOverlays = immutableCopy(overlays);
          normalizedOverlays.forEach(overlay -> validateProfileArtifactOverlay(appId, profileId, overlay));
          return storageGateway.saveProfileArtifactOverlays(appId, profileId, normalizedOverlays);
        });
  }

  @Override
  public Try<Void> requestResolve(String appId, String profileId, RuntimeKind runtimeKind) {
    return Try.of(() -> {
      normalizeProfileId(appId, profileId);
      if (runtimeKind == null) {
        throw new IllegalArgumentException("runtimeKind 不能为空");
      }
      return runtimeKind;
    }).flatMap(ignore -> storageGateway.markResolveRequested(appId, profileId, runtimeKind));
  }

  private void validateRepositoryRef(RepositoryRef repositoryRef) {
    if (repositoryRef == null) {
      throw new IllegalArgumentException("repositoryRef 不能为空");
    }
    requireText(repositoryRef.getRepositoryId(), "repositoryId 不能为空");
    requireText(repositoryRef.getRepositoryUrl(), "repositoryUrl 不能为空");
    if (repositoryRef.getRepositoryType() == null) {
      throw new IllegalArgumentException("repositoryType 不能为空");
    }
  }

  private void validateAppArtifactBinding(String appId, AppArtifactBinding binding) {
    if (binding == null) {
      throw new IllegalArgumentException("artifactBinding 不能为空");
    }
    if (!appId.equals(binding.getAppId())) {
      throw new IllegalArgumentException("artifactBinding.appId 与路径应用不一致");
    }
    requireText(binding.getGroupId(), "groupId 不能为空");
    requireText(binding.getArtifactId(), "artifactId 不能为空");
    requireText(binding.getVersion(), "version 不能为空");
    validateExactVersion(binding.getVersion());
    if (binding.getRepositoryRef() != null) {
      validateRepositoryRef(binding.getRepositoryRef());
    }
  }

  private void validateProfileArtifactOverlay(String appId, String profileId, ProfileArtifactOverlay overlay) {
    if (overlay == null) {
      throw new IllegalArgumentException("overlay 不能为空");
    }
    if (!appId.equals(overlay.getAppId())) {
      throw new IllegalArgumentException("overlay.appId 与路径应用不一致");
    }
    if (!profileId.equals(overlay.getProfileId())) {
      throw new IllegalArgumentException("overlay.profileId 与路径 profile 不一致");
    }
    if (overlay.getAction() == null) {
      throw new IllegalArgumentException("overlay.action 不能为空");
    }
    if (overlay.getReplacementArtifact() != null) {
      validateAppArtifactBinding(appId, overlay.getReplacementArtifact());
    }
    if (overlay.getDefaultRepositoryRef() != null) {
      validateRepositoryRef(overlay.getDefaultRepositoryRef());
    }
  }

  private void validateExactVersion(String version) {
    String normalized = requireText(version, "version 不能为空");
    String upperValue = normalized.toUpperCase(Locale.ROOT);
    if (upperValue.equals("LATEST")
        || upperValue.equals("RELEASE")
        || upperValue.contains("SNAPSHOT")
        || normalized.contains("[")
        || normalized.contains("]")
        || normalized.contains("(")
        || normalized.contains(")")
        || normalized.contains(",")
        || normalized.contains("+")
        || normalized.contains("*")
        || normalized.contains("$")) {
      throw new IllegalArgumentException("仅支持精确版本号，不支持动态 Maven 版本语义");
    }
  }

  private String normalizeAppId(String appId) {
    return requireText(appId, "appId 不能为空");
  }

  private String normalizeProfileId(String appId, String profileId) {
    normalizeAppId(appId);
    return requireText(profileId, "profileId 不能为空");
  }

  private String requireText(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private static <T> List<T> immutableCopy(List<T> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<T>(values));
  }
}
