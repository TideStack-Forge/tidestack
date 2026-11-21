package com.ouroboros.dev.app.service.vcs;

import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppVcsLocalBinding;
import com.ouroboros.dev.app.model.AppVcsPolicy;
import com.ouroboros.vcs.VCSManagerCenter;

@Service
public class AppVcsConfigService {
  private final AppVcsPolicyStore appVcsPolicyStore;
  private final AppVcsLocalBindingStore appVcsLocalBindingStore;
  private final VCSManagerCenter vcsManagerCenter;

  public AppVcsConfigService(AppVcsPolicyStore appVcsPolicyStore,
                             AppVcsLocalBindingStore appVcsLocalBindingStore,
                             VCSManagerCenter vcsManagerCenter) {
    this.appVcsPolicyStore = appVcsPolicyStore;
    this.appVcsLocalBindingStore = appVcsLocalBindingStore;
    this.vcsManagerCenter = vcsManagerCenter;
  }

  public AppVcsPolicy getPolicy(String appName) {
    return appVcsPolicyStore.loadSanitized(appName);
  }

  public AppVcsLocalBinding getLocalBinding(String appName) {
    return AppVcsRemoteSupport.sanitizeBindingForApi(
        appVcsLocalBindingStore.load(appName),
        AppVcsRemoteSupport.resolveDefaultRemoteName(appVcsPolicyStore.load(appName))
    );
  }

  public Try<Void> savePolicy(String appName, AppVcsPolicy policy) {
    return Try.run(() -> AppVcsRemoteSupport.validateEffectiveBindingCompatibility(policy, appVcsLocalBindingStore.load(appName)))
        .flatMap(ignored -> appVcsPolicyStore.save(appName, policy))
        .map(ignored -> {
          vcsManagerCenter.refresh(appName);
          return null;
        });
  }

  public Try<Void> saveLocalBinding(String appName, AppVcsLocalBinding binding) {
    return Try.run(() -> AppVcsRemoteSupport.validateEffectiveBindingCompatibility(appVcsPolicyStore.load(appName), binding))
        .flatMap(ignored -> appVcsLocalBindingStore.save(appName, binding))
        .map(ignored -> {
          vcsManagerCenter.refresh(appName);
          return null;
        });
  }
}
