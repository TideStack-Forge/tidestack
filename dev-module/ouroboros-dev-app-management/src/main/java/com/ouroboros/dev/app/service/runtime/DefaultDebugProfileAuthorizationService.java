package com.ouroboros.dev.app.service.runtime;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AuthorizeDebugProfileDTO;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.runtime.model.DebugKeyMaterial;
import com.ouroboros.exception.BusinessException;

@Service
public class DefaultDebugProfileAuthorizationService implements DebugProfileAuthorizationService {
  private final DebugKeyMaterialProvider debugKeyMaterialProvider;
  private final AppManagementService appManagementService;

  public DefaultDebugProfileAuthorizationService(DebugKeyMaterialProvider debugKeyMaterialProvider,
                                                 AppManagementService appManagementService) {
    this.debugKeyMaterialProvider = debugKeyMaterialProvider;
    this.appManagementService = appManagementService;
  }

  @Override
  public Try<AuthorizeDebugProfileDTO> authorize(String appName, String devKey) {
    return Try.of(() -> doAuthorize(appName, devKey));
  }

  private AuthorizeDebugProfileDTO doAuthorize(String appName, String devKey) {
    String normalizedAppName = requireAppName(appName);
    if (StringUtils.isBlank(devKey)) {
      throw new BusinessException("devKey 不能为空");
    }
    DebugKeyMaterial debugKeyMaterial = debugKeyMaterialProvider.findByDevKey(normalizedAppName, devKey).get();
    String profileId = debugKeyMaterial.getProfileId();
    if (StringUtils.isBlank(profileId)) {
      throw new BusinessException("开发密钥材料缺少 profile");
    }
    if (debugKeyMaterial.getExpiresAt() == null || !debugKeyMaterial.getExpiresAt().isAfter(Instant.now())) {
      throw new BusinessException("开发密钥已过期: " + profileId);
    }
    if (!StringUtils.equals(StringUtils.trim(devKey), debugKeyMaterial.getDebugKey())) {
      throw new BusinessException("开发密钥不匹配");
    }
    AuthorizeDebugProfileDTO dto = new AuthorizeDebugProfileDTO();
    dto.setProfileId(profileId);
    dto.setAuthorized(Boolean.TRUE);
    dto.setRuntimeStrategy(debugKeyMaterial.getRuntimeStrategy() == null ? null : debugKeyMaterial.getRuntimeStrategy().name());
    dto.setExpiresAt(debugKeyMaterial.getExpiresAt());
    return dto;
  }

  private String requireAppName(String appName) {
    if (StringUtils.isBlank(appName)) {
      throw new BusinessException("appName 不能为空");
    }
    String normalizedAppName = StringUtils.trim(appName);
    if (appManagementService != null && !appManagementService.appExists(normalizedAppName)) {
      throw new BusinessException(404, "应用不存在: " + normalizedAppName);
    }
    return normalizedAppName;
  }
}
