package com.ouroboros.dev.app.service.runtime;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppArtifactBindingDTO;
import com.ouroboros.dev.app.model.ProfileArtifactOverlayDTO;
import com.ouroboros.dev.app.model.RepositoryRefDTO;
import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.ProfileArtifactOverlay;
import com.ouroboros.dev.runtime.model.ProfileArtifactOverlayAction;
import com.ouroboros.dev.runtime.model.RepositoryRef;
import com.ouroboros.dev.runtime.model.RepositoryType;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.service.ArtifactBindingService;
import com.ouroboros.exception.BusinessException;

public class AppRuntimeLockFacade {
  private final ArtifactBindingService artifactBindingService;

  public AppRuntimeLockFacade(ArtifactBindingService artifactBindingService) {
    this.artifactBindingService = artifactBindingService;
  }

  public Try<Void> replaceRepositoryRefs(String appName, List<RepositoryRefDTO> repositoryRefs) {
    return Try.of(() -> toRepositoryRefs(repositoryRefs))
        .flatMap(refs -> artifactBindingService.replaceRepositoryRefs(appName, refs));
  }

  public Try<Void> replaceAppArtifactBindings(String appName, List<AppArtifactBindingDTO> bindings) {
    return Try.of(() -> toAppArtifactBindings(appName, bindings))
        .flatMap(mapped -> artifactBindingService.replaceAppArtifactBindings(appName, mapped));
  }

  public Try<Void> replaceProfileArtifactOverlays(String appName, String profileId, List<ProfileArtifactOverlayDTO> overlays) {
    return Try.of(() -> toProfileArtifactOverlays(appName, profileId, overlays))
        .flatMap(mapped -> artifactBindingService.replaceProfileArtifactOverlays(appName, profileId, mapped));
  }

  public Try<Void> requestResolve(String appName, String profileId, String runtimeKind) {
    return Try.of(() -> parseRuntimeKind(runtimeKind))
        .flatMap(kind -> artifactBindingService.requestResolve(appName, profileId, kind));
  }

  private List<RepositoryRef> toRepositoryRefs(List<RepositoryRefDTO> repositoryRefs) {
    if (repositoryRefs == null || repositoryRefs.isEmpty()) {
      return Collections.emptyList();
    }
    return repositoryRefs.stream()
        .map(this::toRepositoryRef)
        .collect(Collectors.toList());
  }

  private List<AppArtifactBinding> toAppArtifactBindings(String appName, List<AppArtifactBindingDTO> bindings) {
    if (bindings == null || bindings.isEmpty()) {
      return Collections.emptyList();
    }
    return bindings.stream()
        .map(binding -> toAppArtifactBinding(appName, binding))
        .collect(Collectors.toList());
  }

  private List<ProfileArtifactOverlay> toProfileArtifactOverlays(String appName,
                                                                 String profileId,
                                                                 List<ProfileArtifactOverlayDTO> overlays) {
    if (overlays == null || overlays.isEmpty()) {
      return Collections.emptyList();
    }
    return overlays.stream()
        .map(overlay -> toProfileArtifactOverlay(appName, profileId, overlay))
        .collect(Collectors.toList());
  }

  private RepositoryRef toRepositoryRef(RepositoryRefDTO repositoryRef) {
    if (repositoryRef == null) {
      throw new BusinessException("repositoryRef 不能为空");
    }
    return RepositoryRef.builder()
        .repositoryId(requireText(repositoryRef.getRepositoryId(), "repositoryId 不能为空"))
        .repositoryType(parseRepositoryType(repositoryRef.getRepositoryType()))
        .repositoryUrl(requireText(repositoryRef.getRepositoryUrl(), "repositoryUrl 不能为空"))
        .credentialRef(trimToNull(repositoryRef.getCredentialRef()))
        .defaultEnabled(repositoryRef.isDefaultEnabled())
        .build();
  }

  private AppArtifactBinding toAppArtifactBinding(String appName, AppArtifactBindingDTO binding) {
    if (binding == null) {
      throw new BusinessException("artifactBinding 不能为空");
    }
    validateExactVersion(binding.getVersion());
    return AppArtifactBinding.builder()
        .appId(requireText(appName, "appName 不能为空"))
        .groupId(requireText(binding.getGroupId(), "groupId 不能为空"))
        .artifactId(requireText(binding.getArtifactId(), "artifactId 不能为空"))
        .version(requireText(binding.getVersion(), "version 不能为空"))
        .packaging(trimToNull(binding.getPackaging()))
        .classifier(trimToNull(binding.getClassifier()))
        .enabled(binding.isEnabled())
        .usage(trimToNull(binding.getUsage()))
        .repositoryRef(binding.getRepositoryRef() == null ? null : toRepositoryRef(binding.getRepositoryRef()))
        .build();
  }

  private ProfileArtifactOverlay toProfileArtifactOverlay(String appName,
                                                          String profileId,
                                                          ProfileArtifactOverlayDTO overlay) {
    if (overlay == null) {
      throw new BusinessException("overlay 不能为空");
    }
    return ProfileArtifactOverlay.builder()
        .appId(requireText(appName, "appName 不能为空"))
        .profileId(requireText(profileId, "profileId 不能为空"))
        .action(parseOverlayAction(overlay.getAction()))
        .targetArtifactKey(trimToNull(overlay.getTargetArtifactKey()))
        .replacementArtifact(overlay.getReplacementArtifact() == null ? null : toAppArtifactBinding(appName, overlay.getReplacementArtifact()))
        .defaultRepositoryRef(overlay.getDefaultRepositoryRef() == null ? null : toRepositoryRef(overlay.getDefaultRepositoryRef()))
        .build();
  }

  private RepositoryType parseRepositoryType(String repositoryType) {
    try {
      return RepositoryType.valueOf(normalizeEnumValue(repositoryType));
    } catch (Exception error) {
      throw new BusinessException("不支持的 repositoryType: " + repositoryType);
    }
  }

  private ProfileArtifactOverlayAction parseOverlayAction(String action) {
    try {
      return ProfileArtifactOverlayAction.valueOf(normalizeEnumValue(action));
    } catch (Exception error) {
      throw new BusinessException("不支持的 overlayAction: " + action);
    }
  }

  private RuntimeKind parseRuntimeKind(String runtimeKind) {
    try {
      return RuntimeKind.valueOf(normalizeEnumValue(runtimeKind));
    } catch (Exception error) {
      throw new BusinessException("不支持的 runtimeKind: " + runtimeKind);
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
      throw new BusinessException("仅支持精确版本号，不支持动态 Maven 版本语义");
    }
  }

  private String normalizeEnumValue(String value) {
    return requireText(value, "枚举值不能为空")
        .replace('-', '_')
        .replace(' ', '_')
        .toUpperCase(Locale.ROOT);
  }

  private String requireText(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new BusinessException(message);
    }
    return value.trim();
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
