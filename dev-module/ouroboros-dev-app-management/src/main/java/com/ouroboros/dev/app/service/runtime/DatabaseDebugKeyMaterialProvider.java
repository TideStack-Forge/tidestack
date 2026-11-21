package com.ouroboros.dev.app.service.runtime;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.DebugAccessStrategy;
import com.ouroboros.dev.runtime.model.DebugKeyMaterial;
import com.ouroboros.exception.BusinessException;

@Component
public class DatabaseDebugKeyMaterialProvider implements DebugKeyMaterialProvider {
  private final DevKeyStore devKeyStore;
  private final AuthenticationPrincipalTestAppProfileResolver profileResolver;

  public DatabaseDebugKeyMaterialProvider(DevKeyStore devKeyStore,
                                          AuthenticationPrincipalTestAppProfileResolver profileResolver) {
    this.devKeyStore = devKeyStore;
    this.profileResolver = profileResolver;
  }

  @Override
  public Try<DebugKeyMaterial> read(String appName, String userIdentity) {
    return Try.of(() -> toRuntimeMaterial(requiredMaterial(devKeyStore.load(appName, userIdentity))));
  }

  @Override
  public Try<DebugKeyMaterial> findByDevKey(String appName, String devKey) {
    return Try.of(() -> toRuntimeMaterial(requiredMaterial(devKeyStore.findByDevKey(appName, devKey))));
  }

  private com.ouroboros.dev.app.model.DebugKeyMaterial requiredMaterial(com.ouroboros.dev.app.model.DebugKeyMaterial material) {
    if (material == null) {
      throw new BusinessException(404, "未找到开发密钥材料");
    }
    return material;
  }

  private DebugKeyMaterial toRuntimeMaterial(com.ouroboros.dev.app.model.DebugKeyMaterial material) {
    String userIdentity = requireText(material.getUserIdentity(), "开发密钥材料缺少 userIdentity");
    String profileId = profileResolver.resolveProfileId(userIdentity);
    String devKey = requireText(material.getDebugKey(), "开发密钥材料缺少 devKey");
    DebugAccessStrategy runtimeStrategy = material.getRuntimeStrategy();
    if (runtimeStrategy == null) {
      throw new BusinessException("debugKeyMaterial 格式不合法: 缺少 runtimeStrategy (profileId=" + profileId + ")");
    }
    return DebugKeyMaterial.builder()
        .profileId(profileId)
        .debugKey(devKey)
        .runtimeStrategy(runtimeStrategy)
        .expiresAt(material.getExpiresAt())
        .build();
  }

  private String requireText(String value, String message) {
    if (StringUtils.isBlank(value)) {
      throw new BusinessException(message);
    }
    return StringUtils.trim(value);
  }
}
