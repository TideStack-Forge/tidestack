package com.ouroboros.vcs;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.event.DeleteAppEvent;
import com.ouroboros.vcs.lock.LocalFileLockManager;

/**
 * @author liansz
 */
@Component
public class VCSManagerCenter {

  private final ConcurrentMap<String, VCSManager> vcsManagerMap = new ConcurrentHashMap<>();

  @Autowired
  AppHomeResolver appHomeResolver;

  @Autowired(required = false)
  VCSConfigResolver vcsConfigResolver;


  public VCSManager getVcsManager(String appName) {
    return vcsManagerMap.computeIfAbsent(appName, this::createVcsManager);
  }

  public VCSManager refresh(String appName) {
    evict(appName);
    return getVcsManager(appName);
  }

  public void evict(String appName) {
    var vcsManager = vcsManagerMap.remove(appName);
    if (vcsManager != null) {
      vcsManager.destroy();
    }
  }

  private VCSManager createVcsManager(String appName) {
    var vcsConfig = resolveConfig(appName);
    var lockFilePath = vcsConfig.getRepositoryPath() + ".lock";
    var lockManager = new LocalFileLockManager(lockFilePath);
    return new LocalGitVCSManager(vcsConfig, lockManager);
  }

  protected VCSConfig resolveConfig(String appName) {
    if (vcsConfigResolver != null) {
      var resolved = vcsConfigResolver.resolve(appName);
      if (resolved != null) {
        return resolved;
      }
    }
    return new VCSConfig(appHomeResolver.getAppHomeByAppId(appName));
  }

  @EventListener(DeleteAppEvent.class)
  public void onDeleteApp(DeleteAppEvent event) {
    evict(event.getAppName());
  }
}
