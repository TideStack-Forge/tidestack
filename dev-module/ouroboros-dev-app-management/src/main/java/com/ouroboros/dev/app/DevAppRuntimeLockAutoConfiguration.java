package com.ouroboros.dev.app;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.dev.app.service.runtime.AppRuntimeLockFacade;
import com.ouroboros.dev.app.service.runtime.VCSArtifactBindingStorage;
import com.ouroboros.dev.runtime.DevRuntimeCoreAutoConfiguration;
import com.ouroboros.dev.runtime.service.ArtifactBindingService;
import com.ouroboros.dev.runtime.service.DefaultArtifactBindingService;
import com.ouroboros.dev.runtime.service.PersistentRuntimeLockStore;
import com.ouroboros.dev.runtime.service.RuntimeLockStore;
import com.ouroboros.vcs.VCSManagerCenter;

@Configuration
@AutoConfigureBefore(DevRuntimeCoreAutoConfiguration.class)
public class DevAppRuntimeLockAutoConfiguration {

  @Bean
  public VCSArtifactBindingStorage vcsArtifactBindingStorage(VCSManagerCenter vcsManagerCenter) {
    return new VCSArtifactBindingStorage(vcsManagerCenter);
  }

  @Bean
  public ArtifactBindingService artifactBindingService(VCSArtifactBindingStorage vcsArtifactBindingStorage) {
    return new DefaultArtifactBindingService(vcsArtifactBindingStorage);
  }

  @Bean
  public RuntimeLockStore runtimeLockStore(VCSArtifactBindingStorage vcsArtifactBindingStorage) {
    return new PersistentRuntimeLockStore(vcsArtifactBindingStorage);
  }

  @Bean
  public AppRuntimeLockFacade appRuntimeLockFacade(ArtifactBindingService artifactBindingService) {
    return new AppRuntimeLockFacade(artifactBindingService);
  }
}
