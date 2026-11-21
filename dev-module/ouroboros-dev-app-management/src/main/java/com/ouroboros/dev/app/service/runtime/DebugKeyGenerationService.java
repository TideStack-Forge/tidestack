package com.ouroboros.dev.app.service.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ouroboros.dev.app.model.DebugKeyMaterial;
import com.ouroboros.dev.runtime.model.DebugAccessStrategy;
import com.ouroboros.exception.BusinessException;

@Service
public class DebugKeyGenerationService {
  private static final Duration DEFAULT_EXPIRY_DURATION = Duration.ofDays(90);

  @Value("${ouroboros.dev.debugKeyDefaultExpiryDays:90}")
  private long defaultExpiryDays;

  private Duration defaultExpiryDuration;

  public DebugKeyGenerationService() {
  }

  public DebugKeyGenerationService(Duration defaultExpiryDuration) {
    this.defaultExpiryDuration = defaultExpiryDuration == null ? DEFAULT_EXPIRY_DURATION : defaultExpiryDuration;
  }

  private Duration getDefaultExpiryDuration() {
    if (defaultExpiryDuration == null) {
      defaultExpiryDuration = Duration.ofDays(defaultExpiryDays);
    }
    return defaultExpiryDuration;
  }

  public String generateDebugKey() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  public Instant calculateExpiresAt(Duration duration) {
    Duration effectiveDuration = duration == null ? getDefaultExpiryDuration() : duration;
    return Instant.now().plus(effectiveDuration);
  }

  public DebugKeyMaterial createDebugKeyMaterial(String profileId, String description) {
    if (StringUtils.isBlank(profileId)) {
      throw new BusinessException("profileId 不能为空");
    }

    Instant createdAt = Instant.now();
    return DebugKeyMaterial.builder()
        .profileId(profileId)
        .debugKey(generateDebugKey())
        .runtimeStrategy(DebugAccessStrategy.SESSION_TUNNEL)
        .createdAt(createdAt)
        .expiresAt(createdAt.plus(getDefaultExpiryDuration()))
        .description(description)
        .lastRotatedAt(null)
        .createdBy(null)
        .updatedBy(null)
        .build();
  }
}
