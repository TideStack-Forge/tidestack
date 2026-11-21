package com.ouroboros.dev.app.service.runtime;

import java.time.Duration;
import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ouroboros.dev.app.model.DebugKeyMaterial;
import com.ouroboros.dev.app.model.DevKeyMaterialDTO;
import com.ouroboros.dev.app.model.DevKeyPluginConfigDTO;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.exception.BusinessException;

@Service
public class DebugKeyManagementService {
  private static final Logger logger = LoggerFactory.getLogger(DebugKeyManagementService.class);
  private static final String ACTION_GENERATE = "GENERATE";
  private static final String ACTION_ROTATE = "ROTATE";
  private static final String ACTION_DELETE = "DELETE";
  private static final String RESULT_SUCCESS = "SUCCESS";
  private static final String RESULT_FAILURE = "FAILURE";

  private final DebugKeyGenerationService debugKeyGenerationService;
  private final DevKeyStore devKeyStore;
  private final AuthenticationPrincipalTestAppProfileResolver profileResolver;
  private final AppManagementService appManagementService;
  private final String platformUrl;

  @Autowired
  public DebugKeyManagementService(DebugKeyGenerationService debugKeyGenerationService,
                                   DevKeyStore devKeyStore,
                                   AuthenticationPrincipalTestAppProfileResolver profileResolver,
                                   AppManagementService appManagementService,
                                   @Value("${ouroboros.dev.platformUrl:}") String platformUrl) {
    this.debugKeyGenerationService = debugKeyGenerationService;
    this.devKeyStore = devKeyStore;
    this.profileResolver = profileResolver;
    this.appManagementService = appManagementService;
    this.platformUrl = platformUrl;
  }

  public DebugKeyManagementService(DebugKeyGenerationService debugKeyGenerationService,
                                   DevKeyStore devKeyStore,
                                   AuthenticationPrincipalTestAppProfileResolver profileResolver) {
    this(debugKeyGenerationService, devKeyStore, profileResolver, null, "");
  }

  public DebugKeyManagementService(DebugKeyGenerationService debugKeyGenerationService,
                                   DevKeyStore devKeyStore,
                                   AuthenticationPrincipalTestAppProfileResolver profileResolver,
                                   String platformUrl) {
    this(debugKeyGenerationService, devKeyStore, profileResolver, null, platformUrl);
  }

  public DebugKeyMaterial generateDebugKey(String appName, String userIdentity, String description, Integer expiryDays) {
    String normalizedAppName = requireAppName(appName);
    String normalizedUserIdentity = requireUserIdentity(userIdentity);
    String profileId = profileIdFor(normalizedUserIdentity);
    String keyPrefix = null;
    try {
      DebugKeyMaterial generated = saveGenerated(normalizedAppName, normalizedUserIdentity, description, expiryDays, false);
      keyPrefix = keyPrefix(generated.getDebugKey());
      audit(ACTION_GENERATE, normalizedAppName, normalizedUserIdentity, profileId, RESULT_SUCCESS, keyPrefix, null);
      return generated;
    } catch (RuntimeException exception) {
      audit(ACTION_GENERATE, normalizedAppName, normalizedUserIdentity, profileId, RESULT_FAILURE, keyPrefix, exception);
      throw exception;
    }
  }

  public DevKeyMaterialDTO generateDevKey(String appName, String description, Integer expiryDays) {
    String normalizedAppName = requireAppName(appName);
    return DevKeyMaterialDTO.from(normalizedAppName, generateDebugKey(normalizedAppName, currentUserIdentity(), description, expiryDays));
  }

  public DebugKeyMaterial getDebugKey(String appName, String userIdentity) {
    String normalizedAppName = requireAppName(appName);
    String normalizedUserIdentity = authorizeUser(userIdentity);
    DebugKeyMaterial material = withDerivedProfile(devKeyStore.load(normalizedAppName, normalizedUserIdentity));
    if (material == null) {
      return null;
    }
    return maskDebugKey(material);
  }

  public DevKeyMaterialDTO getDevKey(String appName) {
    String normalizedAppName = requireAppName(appName);
    return DevKeyMaterialDTO.from(normalizedAppName, getDebugKey(normalizedAppName, currentUserIdentity()));
  }

  public DebugKeyMaterial rotateDebugKey(String appName, String userIdentity, String description, Integer expiryDays) {
    String normalizedAppName = requireAppName(appName);
    String normalizedUserIdentity = requireUserIdentity(userIdentity);
    String profileId = profileIdFor(normalizedUserIdentity);
    String keyPrefix = null;
    try {
      DebugKeyMaterial generated = saveGenerated(normalizedAppName, normalizedUserIdentity, description, expiryDays, true);
      keyPrefix = keyPrefix(generated.getDebugKey());
      audit(ACTION_ROTATE, normalizedAppName, normalizedUserIdentity, profileId, RESULT_SUCCESS, keyPrefix, null);
      return generated;
    } catch (RuntimeException exception) {
      audit(ACTION_ROTATE, normalizedAppName, normalizedUserIdentity, profileId, RESULT_FAILURE, keyPrefix, exception);
      throw exception;
    }
  }

  public DevKeyMaterialDTO rotateDevKey(String appName, String description, Integer expiryDays) {
    String normalizedAppName = requireAppName(appName);
    return DevKeyMaterialDTO.from(normalizedAppName, rotateDebugKey(normalizedAppName, currentUserIdentity(), description, expiryDays));
  }

  public void deleteDebugKey(String appName, String userIdentity) {
    String normalizedAppName = requireAppName(appName);
    String normalizedUserIdentity = requireUserIdentity(userIdentity);
    String profileId = profileIdFor(normalizedUserIdentity);
    String keyPrefix = null;
    try {
      authorizeUser(normalizedUserIdentity);
      DebugKeyMaterial existing = devKeyStore.load(normalizedAppName, normalizedUserIdentity);
      if (existing == null) {
        throw new BusinessException(404, "未找到调试密钥材料: " + normalizedUserIdentity);
      }
      keyPrefix = keyPrefix(existing.getDebugKey());
      devKeyStore.delete(normalizedAppName, normalizedUserIdentity);
      audit(ACTION_DELETE, normalizedAppName, normalizedUserIdentity, profileId, RESULT_SUCCESS, keyPrefix, null);
    } catch (RuntimeException exception) {
      audit(ACTION_DELETE, normalizedAppName, normalizedUserIdentity, profileId, RESULT_FAILURE, keyPrefix, exception);
      throw exception;
    }
  }

  public void deleteDevKey(String appName) {
    String normalizedAppName = requireAppName(appName);
    deleteDebugKey(normalizedAppName, currentUserIdentity());
  }

  public DevKeyPluginConfigDTO getDevKeyPluginConfig(String appName) {
    String normalizedAppName = requireAppName(appName);
    String userIdentity = currentUserIdentity();
    DebugKeyMaterial material = devKeyStore.load(normalizedAppName, userIdentity);
    if (material == null) {
      throw new BusinessException(404, "未找到开发密钥材料: " + userIdentity);
    }
    DevKeyPluginConfigDTO dto = new DevKeyPluginConfigDTO();
    dto.setDevelopmentServerUrl(platformUrl);
    dto.setAppName(normalizedAppName);
    dto.setDevKey(material.getDebugKey());
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

  private DebugKeyMaterial saveGenerated(String appName, String userIdentity, String description, Integer expiryDays, boolean rotating) {
    String normalizedUserIdentity = authorizeUser(userIdentity);
    String profileId = profileIdFor(normalizedUserIdentity);
    DebugKeyMaterial existing = devKeyStore.load(appName, normalizedUserIdentity);
    if (!rotating && existing != null) {
      throw new BusinessException(409, "调试密钥已存在: " + normalizedUserIdentity);
    }
    if (rotating && existing == null) {
      throw new BusinessException(404, "未找到调试密钥材料: " + normalizedUserIdentity);
    }
    DebugKeyMaterial generated = debugKeyGenerationService.createDebugKeyMaterial(profileId, description);
    generated.setAppName(appName);
    generated.setUserIdentity(normalizedUserIdentity);
    if (expiryDays != null) {
      generated.setExpiresAt(debugKeyGenerationService.calculateExpiresAt(Duration.ofDays(expiryDays.longValue())));
    }
    generated.setCreatedBy(currentUserIdentity());
    generated.setUpdatedBy(currentUserIdentity());
    if (rotating && existing != null) {
      generated.setLastRotatedAt(Instant.now());
      generated.setCreatedAt(existing.getCreatedAt());
    }
    devKeyStore.save(appName, normalizedUserIdentity, generated);
    return generated;
  }

  private DebugKeyMaterial withDerivedProfile(DebugKeyMaterial material) {
    if (material == null) {
      return null;
    }
    material.setProfileId(profileIdFor(material.getUserIdentity()));
    return material;
  }

  private DebugKeyMaterial maskDebugKey(DebugKeyMaterial material) {
    if (material == null) {
      return null;
    }
    DebugKeyMaterial masked = DebugKeyMaterial.builder()
        .appName(material.getAppName())
        .userIdentity(material.getUserIdentity())
        .profileId(material.getProfileId())
        .debugKey(maskValue(material.getDebugKey()))
        .runtimeStrategy(material.getRuntimeStrategy())
        .createdAt(material.getCreatedAt())
        .expiresAt(material.getExpiresAt())
        .description(material.getDescription())
        .lastRotatedAt(material.getLastRotatedAt())
        .createdBy(material.getCreatedBy())
        .updatedBy(material.getUpdatedBy())
        .build();
    return masked;
  }

  private String maskValue(String debugKey) {
    if (StringUtils.isBlank(debugKey)) {
      return debugKey;
    }
    String text = debugKey.trim();
    if (text.length() <= 8) {
      return text + "****";
    }
    return text.substring(0, 8) + "****";
  }

  private String keyPrefix(String debugKey) {
    if (StringUtils.isBlank(debugKey)) {
      return null;
    }
    String text = StringUtils.trim(debugKey);
    return text.length() <= 8 ? text : text.substring(0, 8);
  }

  private void audit(String action, String appName, String userIdentity, String profileId, String result, String keyPrefix, Throwable throwable) {
    String operator = currentUserIdentity();
    String message = "DebugKey operation: action={}, appName={}, userIdentity={}, profileId={}, operator={}, result={}, keyPrefix={}, time={}";
    if (StringUtils.equalsIgnoreCase(RESULT_SUCCESS, result)) {
      logger.info(message, action, appName, userIdentity, profileId, operator, result, StringUtils.defaultString(keyPrefix, "null"), Instant.now());
      return;
    }
    logger.warn(message, action, appName, userIdentity, profileId, operator, result, StringUtils.defaultString(keyPrefix, "null"), Instant.now(), throwable);
  }

  private String authorizeUser(String userIdentity) {
    String normalizedUserIdentity = requireUserIdentity(userIdentity);
    String currentUserIdentity = currentUserIdentity();
    if (!StringUtils.equals(currentUserIdentity, normalizedUserIdentity)) {
      throw new BusinessException(403, "无权访问用户 Dev Key: " + normalizedUserIdentity);
    }
    return normalizedUserIdentity;
  }

  private String requireUserIdentity(String userIdentity) {
    if (StringUtils.isBlank(userIdentity)) {
      throw new BusinessException("userIdentity 不能为空");
    }
    return StringUtils.trim(userIdentity);
  }

  private String profileIdFor(String userIdentity) {
    return profileResolver.resolveProfileId(userIdentity);
  }

  private String currentUserIdentity() {
    return requireUserIdentity(profileResolver.resolveCurrentUserIdentity());
  }
}
