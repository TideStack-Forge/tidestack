package com.ouroboros.dev.app.service.vcs;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.app.model.AppVcsPolicy;
import com.ouroboros.dev.app.model.AppVcsRemoteBinding;
import com.ouroboros.dev.app.model.AppVcsRemoteDefinition;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.VCSConfigResolver;

@Component
public class AppVcsConfigResolver implements VCSConfigResolver {
  private final AppHomeResolver appHomeResolver;
  private final AppVcsPolicyStore appVcsPolicyStore;
  private final AppVcsLocalBindingStore appVcsLocalBindingStore;

  public AppVcsConfigResolver(AppHomeResolver appHomeResolver,
                              AppVcsPolicyStore appVcsPolicyStore,
                              AppVcsLocalBindingStore appVcsLocalBindingStore) {
    this.appHomeResolver = appHomeResolver;
    this.appVcsPolicyStore = appVcsPolicyStore;
    this.appVcsLocalBindingStore = appVcsLocalBindingStore;
  }

  @Override
  public VCSConfig resolve(String appName) {
    VCSConfig config = new VCSConfig(appHomeResolver.getAppHomeByAppId(appName));
    AppVcsPolicy policy = appVcsPolicyStore.load(appName);
    AppVcsRemoteDefinition remote = applyPolicy(config, policy);
    applyLocalBinding(config, AppVcsRemoteSupport.resolveBinding(appVcsLocalBindingStore.load(appName), remote == null ? null : remote.getName()));
    return config;
  }

  private AppVcsRemoteDefinition applyPolicy(VCSConfig config, AppVcsPolicy policy) {
    AppVcsRemoteDefinition remote = AppVcsRemoteSupport.resolveEffectiveRemote(policy);
    if (remote != null) {
      config.setRemoteName(remote.getName());
      config.setRemoteUri(remote.getUrl());
    }
    String trunkBranch = AppVcsRemoteSupport.resolveTrunkBranch(policy);
    if (trunkBranch != null) {
      config.setDefaultBranch(trunkBranch);
    }
    return remote;
  }

  private void applyLocalBinding(VCSConfig config, AppVcsRemoteBinding binding) {
    if (binding == null || binding.getAuthType() == null || binding.getAuthType() == VCSConfig.AuthType.NONE) {
      return;
    }
    AppVcsRemoteSupport.validateBindingCompatibility(config.getRemoteUri(), binding);
    VCSConfig.AuthConfig authConfig = new VCSConfig.AuthConfig();
    if (binding.getAuthType() == VCSConfig.AuthType.HTTP_BASIC) {
      if (StringUtils.isAnyBlank(binding.getUsername(), binding.getPassword())) {
        return;
      }
      authConfig.setAuthType(VCSConfig.AuthType.HTTP_BASIC);
      authConfig.setUsername(binding.getUsername());
      authConfig.setPassword(binding.getPassword());
      config.setAuthConfig(authConfig);
      return;
    }
    if (binding.getAuthType() == VCSConfig.AuthType.SSH_KEY && StringUtils.isNotBlank(binding.getSshKeyPath())) {
      authConfig.setAuthType(VCSConfig.AuthType.SSH_KEY);
      authConfig.setSshKeyPath(binding.getSshKeyPath());
      config.setAuthConfig(authConfig);
    }
  }
}
