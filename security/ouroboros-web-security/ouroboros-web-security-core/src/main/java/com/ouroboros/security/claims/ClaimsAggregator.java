package com.ouroboros.security.claims;

import java.beans.Transient;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.User;
import com.ouroboros.util.SPI;

/**
 * Claims 聚合器
 */
public class ClaimsAggregator {

  private static final Logger logger = LoggerFactory.getLogger(ClaimsAggregator.class);

  private final List<ClaimsProvider> providers;

  public ClaimsAggregator(List<ClaimsProvider> providers) {
    // 按优先级排序（数字越小先执行，数字越大后执行可覆盖）
    var spiProviders = SPI.getServiceStream(ClaimsProvider.class);
    this.providers = Stream.concat(spiProviders, providers.stream())
        .sorted(Comparator.comparingInt(this::getPriority))
        .collect(Collectors.toList());

    logProviders();
  }

  public Claims aggregate(User user) {
    Map<String, Object> aggregated = new HashMap<>();
    ClaimsContext context = new MutableClaimsContext(aggregated);
    User finalUser = new ProtectedUser(user);

    for (ClaimsProvider provider : providers) {
      try {
        Claims newClaims = provider.provide(finalUser, context);
        if (newClaims != null && !newClaims.getTypes().isEmpty()) {
          aggregated.putAll(newClaims.asMap());

          if (logger.isDebugEnabled()) {
            logger.debug("Provider [{}] provided {} claims for user [{}]",
                provider.getName(), newClaims.getTypes().size(), user.getUserIdentity());
          }
        }
      } catch (Exception e) {
        logger.error("Error loading claims from provider [{}] for user [{}]",
            provider.getName(), user.getUserIdentity(), e);
      }
    }

    return new ImmutableClaims(aggregated);
  }

  private void logProviders() {
    logger.info("Loaded {} ClaimsProviders:", providers.size());
    for (ClaimsProvider provider : providers) {
      logger.info("  - [priority={}] {} provides: {}",
          getPriority(provider), provider.getName(), provider.getProvidedClaimTypes());
    }
  }

  private Integer getPriority(ClaimsProvider provider) {
    var priority = provider.getClass().getAnnotation(Priority.class);
    return priority != null ? priority.value() : 0;
  }

  private static class MutableClaimsContext implements ClaimsContext {
    private final Map<String, Object> claims;

    MutableClaimsContext(Map<String, Object> claims) {
      this.claims = claims;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getClaim(String type, Class<T> valueType) {
      Object value = claims.get(type);
      if (value != null && valueType.isInstance(value)) {
        return Optional.of((T) value);
      }
      if (value != null && valueType == String.class) {
        return Optional.of((T) value.toString());
      }
      return Optional.empty();
    }

    @Override
    public boolean hasClaim(String type) {
      return claims.containsKey(type);
    }
  }

  private static class ProtectedUser implements User {
    private final User user;

    public ProtectedUser(User user) {
      this.user = user;
    }

    @Override
    public Claims getClaims() {
      throw new UnsupportedOperationException("在生成 Claims 阶段不支持访问 getClaims 方法。");
    }

    @Override
    public String getUserIdentity() {
      return user.getUserIdentity();
    }

    @Transient
    @Override
    public List<Role> getRoles() {
      return user.getRoles();
    }

    @Transient
    @Override
    public Subject getSubject() {
      return user.getSubject();
    }

    @Override
    public String getNickname() {
      return user.getNickname();
    }

    @Override
    public String getFormalName() {
      return user.getFormalName();
    }

    @Override
    public String getDisplayName() {
      return user.getDisplayName();
    }

    @Override
    public String getPicture() {
      return user.getPicture();
    }

    @Override
    public String getEmail() {
      return user.getEmail();
    }

    @Override
    public String getPhoneNumber() {
      return user.getPhoneNumber();
    }
  }
}
