package com.ouroboros.security.externalauth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ouroboros.WebAppInfoContributor;
import com.ouroboros.security.externalauth.config.ProviderSpec;

@Service
public class ExternalAuthProviderInfoService {

  private final ExternalAuthProviderFactory providerFactory;

  public ExternalAuthProviderInfoService(ExternalAuthProviderFactory providerFactory) {
    this.providerFactory = providerFactory;
    SingletonHolder.INSTANCE = this;
  }

  public List<ProviderInfo> listProviders() {
    Collection<ExternalAuthProvider> providers = providerFactory.getAllProviders();
    List<ProviderInfo> result = new ArrayList<ProviderInfo>();
    for (ExternalAuthProvider provider : providers) {
      ProviderSpec spec = provider.getSpec();
      if (!spec.isEnabled()) {
        continue;
      }
      if (providerFactory.isUnavailableAtStartup(spec.getKey())) {
        continue;
      }
      ProviderInfo info = new ProviderInfo();
      info.setKey(spec.getKey());
      info.setType(spec.getType());
      info.setDisplayName(spec.getDisplayName());
      info.setLoginUrl(buildLoginUrl(spec));

      ProviderHealthChecker healthChecker = providerFactory.getHealthChecker(spec.getKey());
      if (healthChecker != null && !healthChecker.isHealthy()) {
        info.setAvailability("unhealthy-runtime");
      } else {
        info.setAvailability("healthy");
      }
      result.add(info);
    }
    return result;
  }

  public Map<String, Object> getWebAppInfo() {
    Map<String, Object> webAppInfo = new HashMap<String, Object>();
    webAppInfo.put("authProviders", listProviders());
    return webAppInfo;
  }

  private String buildLoginUrl(ProviderSpec spec) {
    String type = spec.getType();
    if ("cas".equals(type)) {
      return "/api/auth/cas/" + spec.getKey() + "/login";
    } else if ("oauth2".equals(type) || spec.isConnectionScoped()) {
      return "/api/auth/oauth2/" + spec.getKey() + "/authorize";
    }
    return null;
  }

  public static class SingletonHolder implements WebAppInfoContributor {
    public static ExternalAuthProviderInfoService INSTANCE;

    @Override
    public Map<String, Object> getWebAppInfo() {
      return Optional.ofNullable(INSTANCE)
          .map(ExternalAuthProviderInfoService::getWebAppInfo)
          .orElseGet(Collections::emptyMap);
    }
  }

  public static class ProviderInfo {
    private String key;
    private String type;
    private String displayName;
    private String loginUrl;
    private String availability;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getLoginUrl() { return loginUrl; }
    public void setLoginUrl(String loginUrl) { this.loginUrl = loginUrl; }

    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }
  }
}
