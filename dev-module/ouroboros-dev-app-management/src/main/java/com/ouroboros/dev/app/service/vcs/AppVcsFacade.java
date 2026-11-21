package com.ouroboros.dev.app.service.vcs;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppBranchDTO;
import com.ouroboros.dev.app.model.AppBranchSwitchRequest;
import com.ouroboros.dev.app.model.AppManagedBranchCreateRequest;
import com.ouroboros.dev.app.model.AppVcsStateDTO;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;

@Service
public class AppVcsFacade {
  private final VCSManagerCenter vcsManagerCenter;
  private final AppVcsPolicyStore appVcsPolicyStore;

  public AppVcsFacade(VCSManagerCenter vcsManagerCenter,
                      AppVcsPolicyStore appVcsPolicyStore) {
    this.vcsManagerCenter = vcsManagerCenter;
    this.appVcsPolicyStore = appVcsPolicyStore;
  }

  public Try<AppVcsStateDTO> getState(String appName) {
    return Try.of(() -> {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      String trunkBranch = getTrunkBranch(appName);
      String currentBranch = vcsManager.getCurrentBranch().get();
      AppVcsBranchModel.BranchInfo branchInfo = AppVcsBranchModel.resolveBranch(currentBranch, trunkBranch);
      VCSConfig.RemoteConfig remoteConfig = vcsManager.getRemoteConfig().getOrElse(new VCSConfig.RemoteConfig());
      boolean workspaceClean = vcsManager.isWorkspaceClean().getOrElse(false);
      var latestCommit = vcsManager.getChangeLogs(".", 0, 1).stream().findFirst();
      return new AppVcsStateDTO()
          .setCurrentRevision(latestCommit.map(com.ouroboros.vcs.versioning.ChangeLog::getId).orElse(null))
          .setLastCommitMessage(latestCommit.map(com.ouroboros.vcs.versioning.ChangeLog::getMessage).orElse(null))
          .setCurrentBranch(currentBranch)
          .setReleaseTrack(branchInfo.getReleaseTrack())
          .setSourceBranch(currentBranch)
          .setEditionCode(branchInfo.getEditionCode())
          .setRemoteName(remoteConfig.getRemoteName())
          .setRemoteUri(AppVcsRemoteSupport.sanitizeRemoteUriForApi(remoteConfig.getRemoteUri()))
          .setRemoteBranch(currentBranch)
          .setWorkspaceClean(workspaceClean)
          .setBranchManaged(branchInfo.isManaged());
    });
  }

  public Try<List<AppBranchDTO>> getManagedBranches(String appName) {
    return Try.of(() -> {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      String trunkBranch = getTrunkBranch(appName);
      String currentBranch = vcsManager.getCurrentBranch().get();
      List<String> branches = vcsManager.getBranchList().get();
      List<AppBranchDTO> managedBranches = new ArrayList<>();
      for (String branchName : branches) {
        AppVcsBranchModel.BranchInfo branchInfo = AppVcsBranchModel.resolveBranch(branchName, trunkBranch);
        if (!branchInfo.isManaged()) {
          continue;
        }
        managedBranches.add(branchInfo.toDto(StringUtils.equals(branchName, currentBranch)));
      }
      return managedBranches;
    });
  }

  public Try<AppBranchDTO> getCurrentBranch(String appName) {
    return Try.of(() -> {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      String currentBranch = vcsManager.getCurrentBranch().get();
      return AppVcsBranchModel.resolveBranch(currentBranch, getTrunkBranch(appName)).toDto(true);
    });
  }

  public String getTrunkBranch(String appName) {
    String configuredTrunk = AppVcsRemoteSupport.resolveTrunkBranch(appVcsPolicyStore.load(appName));
    if (StringUtils.isNotBlank(configuredTrunk)) {
      return AppVcsBranchModel.normalizeTrunkBranch(configuredTrunk);
    }
    return AppVcsBranchModel.normalizeTrunkBranch(null);
  }

  public Try<AppBranchDTO> createManagedBranch(String appName, AppManagedBranchCreateRequest request) {
    return Try.of(() -> {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      AppVcsBranchModel.BranchInfo branchInfo = AppVcsBranchModel.createManagedBranch(request);
      String fromRevision = AppVcsBranchModel.resolveCreateFromRevision(request,
          branchInfo,
          vcsManager.getBranchList().get(),
          getTrunkBranch(appName));
      vcsManager.createBranch(branchInfo.getBranchName(), fromRevision).get();
      return branchInfo.toDto(false);
    });
  }

  public Try<AppVcsStateDTO> switchBranch(String appName, AppBranchSwitchRequest request) {
    return ensureWorkspaceClean(appName, "切换分支")
        .flatMap(ignored -> Try.of(() -> {
          String branchName = StringUtils.trimToNull(request == null ? null : request.getBranchName());
          if (branchName == null) {
            throw new BusinessException("branchName 不能为空");
          }
          AppVcsBranchModel.BranchInfo branchInfo = AppVcsBranchModel.resolveBranch(branchName, getTrunkBranch(appName));
          if (!branchInfo.isManaged()) {
            throw new BusinessException("仅允许切换到受管分支: master 或 custom/{editionCode}");
          }
          vcsManagerCenter.getVcsManager(appName).switchBranch(branchName).get();
          return getState(appName).get();
        }));
  }

  public Try<AppVcsStateDTO> pull(String appName) {
    return ensureWorkspaceClean(appName, "Pull")
        .flatMap(ignored -> syncCurrentBranch(appName, true));
  }

  public Try<AppVcsStateDTO> push(String appName) {
    return ensureWorkspaceClean(appName, "Push")
        .flatMap(ignored -> syncCurrentBranch(appName, false));
  }

  public Try<Void> ensureWorkspaceClean(String appName, String actionName) {
    return Try.of(() -> {
      boolean workspaceClean = vcsManagerCenter.getVcsManager(appName).isWorkspaceClean().get();
      if (!workspaceClean) {
        throw new BusinessException("工作区存在未提交更改，禁止" + actionName);
      }
      return null;
    });
  }

  private Try<AppVcsStateDTO> syncCurrentBranch(String appName, boolean pull) {
    return Try.of(() -> {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      String currentBranch = vcsManager.getCurrentBranch().get();
      configureRemoteBranch(vcsManager, currentBranch).get();
      if (pull) {
        vcsManager.pull().get();
      } else {
        vcsManager.push().get();
      }
      return getState(appName).get();
    });
  }

  private Try<Void> configureRemoteBranch(VCSManager vcsManager, String branchName) {
    return Try.of(() -> {
      VCSConfig.RemoteConfig remoteConfig = vcsManager.getRemoteConfig().get();
      String remoteUri = AppVcsRemoteSupport.validateTrackedRemoteUri(remoteConfig.getRemoteUri());
      if (StringUtils.isBlank(remoteUri)) {
        throw new BusinessException("请先配置远程仓库");
      }
      remoteConfig.setRemoteUri(remoteUri);
      remoteConfig.setRemoteBranch(branchName);
      vcsManager.setRemoteConfig(remoteConfig).get();
      return null;
    });
  }
}
