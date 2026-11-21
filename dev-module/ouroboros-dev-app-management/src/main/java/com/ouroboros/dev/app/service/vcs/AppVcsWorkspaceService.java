package com.ouroboros.dev.app.service.vcs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppVcsFileDTO;
import com.ouroboros.dev.app.model.AppVcsFileRequest;
import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;

/** 当前工作区操作，复用 VCS 的文件状态、锁和批量提交。 */
@Service
public class AppVcsWorkspaceService {
  private final VCSManagerCenter vcsManagerCenter;
  private final AppEditAccessEvaluator accessEvaluator;

  public AppVcsWorkspaceService(VCSManagerCenter vcsManagerCenter, AppEditAccessEvaluator accessEvaluator) {
    this.vcsManagerCenter = vcsManagerCenter;
    this.accessEvaluator = accessEvaluator;
  }

  public Try<List<AppVcsFileDTO>> getFiles(String appName, boolean onlyChanged) {
    return Try.of(() -> {
      String user = currentUser();
      boolean canEdit = accessEvaluator.canEdit(appName, user);
      boolean canManage = accessEvaluator.canManageApp(appName, user);
      return workspaceFiles(vcsManagerCenter.getVcsManager(appName)).values().stream()
          .filter(file -> !onlyChanged || file.getVersionState() != VersionState.NOTCHANGED)
          .sorted(java.util.Comparator.comparing(VCSFile::getPath))
          .map(file -> {
            LockState lock = file.getLockState();
            return new AppVcsFileDTO(file.getPath(), file.getVersionState(), lock,
                file.getLockOwnerName(), file.exists(),
                canEdit && lock == LockState.OWNED && file.getVersionState() != VersionState.NOTCHANGED,
                canEdit && lock == LockState.OWNED, canManage && lock == LockState.LOCKED);
          }).toList();
    });
  }

  public Try<Boolean> commit(String appName, AppVcsFileRequest request) {
    return Try.of(() -> {
      if (!accessEvaluator.canEdit(appName, currentUser())) {
        throw new BusinessException("没有应用编辑权限");
      }
      String message = StringUtils.trimToNull(request == null ? null : request.message());
      if (message == null) { throw new BusinessException("请填写提交说明"); }
      VCSManager manager = vcsManagerCenter.getVcsManager(appName);
      List<VCSFile> files = selectedFiles(manager, request);
      ensureOwned(files);
      if (files.stream().anyMatch(file -> file.getVersionState() == VersionState.NOTCHANGED)) {
        throw new BusinessException("选中的文件没有待提交修改");
      }
      var batch = manager.batchOperation();
      for (VCSFile file : files) {
        if (file.exists()) { batch.add(file.getPath()); }
        else { batch.remove(file.getPath()); }
      }
      // 写入和提交均走 VCS，锁归属在写前再次校验；提交不会擅自释放编辑器的锁。
      return batch.beforeWrite(() -> ensureOwned(files)).commit(message).get();
    });
  }

  public Try<Void> unlock(String appName, AppVcsFileRequest request) {
    return Try.of(() -> {
      boolean force = request != null && request.force();
      String user = currentUser();
      if (force ? !accessEvaluator.canManageApp(appName, user) : !accessEvaluator.canEdit(appName, user)) {
        throw new BusinessException(force ? "仅仓库管理员可以强制解锁" : "没有应用编辑权限");
      }
      VCSManager manager = vcsManagerCenter.getVcsManager(appName);
      List<VCSFile> files = selectedFiles(manager, request);
      if (!force) { ensureOwned(files); }
      manager.unlock(files.stream().map(VCSFile::getPath).toList(), force).get();
      return null;
    });
  }

  public Try<Map<String, String>> getFileContent(String appName, String path) {
    return Try.of(() -> {
      VCSFile file = workspaceFiles(vcsManagerCenter.getVcsManager(appName)).get(path);
      if (file == null || !file.exists()) { throw new BusinessException("当前工作区中不存在该文件"); }
      return Map.of("path", file.getPath(), "content", file.getContent());
    });
  }

  private List<VCSFile> selectedFiles(VCSManager manager, AppVcsFileRequest request) {
    if (request == null || request.paths() == null || request.paths().isEmpty()) {
      throw new BusinessException("请选择文件");
    }
    Map<String, VCSFile> files = workspaceFiles(manager);
    return request.paths().stream().distinct().map(path -> {
      VCSFile file = files.get(path);
      if (file == null) { throw new BusinessException("文件不在当前工作区中：" + path); }
      return file;
    }).toList();
  }

  private Map<String, VCSFile> workspaceFiles(VCSManager manager) {
    Map<String, VCSFile> files = new LinkedHashMap<>();
    try (var stream = manager.walkDirectory("./", null)) {
      stream.forEach(file -> files.put(file.getPath(), file));
    }
    // walkDirectory 不含已删除文件；它们仍可能持有锁并需要提交删除。
    manager.getUncommittedFiles(".").forEach(file -> files.put(file.getPath(), file));
    return files;
  }

  private void ensureOwned(List<VCSFile> files) {
    for (VCSFile file : files) {
      if (file.getLockState() != LockState.OWNED) {
        throw new BusinessException("只能操作自己持有锁的文件：" + file.getPath());
      }
    }
  }

  private String currentUser() {
    return CurrentSecurityContextHolder.findIdentity().map(SecurityIdentity::getUserIdentity).orElse(null);
  }
}
