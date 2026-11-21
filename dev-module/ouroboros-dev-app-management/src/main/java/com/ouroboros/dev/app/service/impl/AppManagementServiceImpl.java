package com.ouroboros.dev.app.service.impl;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.model.AppEditAccessConfigRequest;
import com.ouroboros.dev.app.model.AppEditAccessSubject;
import com.ouroboros.dev.app.model.AppEditScopeEntity;
import com.ouroboros.dev.app.model.AppEditScopeSubjectType;
import com.ouroboros.dev.app.model.AppInfoWithMeta;
import com.ouroboros.dev.app.model.AppOwnerEntity;
import com.ouroboros.dev.app.service.AppCategoryService;
import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.dev.app.service.AppEditScopeService;
import com.ouroboros.dev.app.service.AppOwnerService;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.event.CreateAppEvent;
import com.ouroboros.dev.event.DeleteAppEvent;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.model.AppInfo;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.dev.app.util.AutoDeletingFileInputStream;
import com.ouroboros.util.Dates;
import com.ouroboros.util.Json;
import com.ouroboros.util.ZipReader;
import com.ouroboros.util.ZipWriter;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.file.VCSFile;

@Service
@SuppressWarnings("unused")
public class AppManagementServiceImpl implements AppManagementService {
  private static final Logger logger = LoggerFactory.getLogger(AppManagementServiceImpl.class);

  final static String TRASH_FOLDER = "_trash";
  final static String APP_INFO_FILE = "app.metadata.json";
  @Autowired
  private DevelopWorkspaceContext developWorkspaceContext;
  @Autowired
  private VCSManagerCenter vcsManagerCenter;
  @Autowired
  private AppCategoryService appCategoryService;
  @Autowired
  private ApplicationEventPublisher eventPublisher;
  @Autowired
  private AppEditScopeService appEditScopeService;
  @Autowired
  private AppEditAccessEvaluator appEditAccessEvaluator;
  @Autowired(required = false)
  private AppOwnerService appOwnerService;

  private String getWorkspace() {
    return developWorkspaceContext.getDevRootDir();
  }

  private String getUserIdentity() {
    return CurrentSecurityContextHolder.findIdentity()
        .map(SecurityIdentity::getUserIdentity)
        .orElse(null);
  }

  private void ensureTrashFolder() {
    var trashPath = Paths.get(getWorkspace(), TRASH_FOLDER);
    if (!Files.exists(trashPath)) {
      Try.run(() -> Files.createDirectories(trashPath));
    }
  }

  @Override
  public Try<AppInfo> getAppInfo(String appName) {
    var vcsManager = vcsManagerCenter.getVcsManager(appName);
    var file = vcsManager.getFile(APP_INFO_FILE);

    var appInfo = Json.tryToBean(AppInfo.class, file.getContent());
    return appInfo.toEither()
        .mapLeft(t -> new PlatformException("解析应用信息失败"))
        .fold(Try::failure, Try::success);
  }

  @Override
  public Try<Void> createApp(AppInfo app) {
    return Try.run(() -> {
      var path = Paths.get(getWorkspace(), app.getName());
      if (Files.exists(path)) {
        throw new BusinessException("应用名已存在");
      }

      Files.createDirectories(path);
      try {
        var createInfoResult = vcsManagerCenter.getVcsManager(app.getName())
            .createFile(APP_INFO_FILE)
            .content(Json.toPrettyJsonString(app))
            .unlockWhenCommit()
            .commit(String.format("创建应用(%s)", app.getName()));
        createInfoResult.getOrElseThrow(e -> e);
        assignOwner(app.getName(), getUserIdentity(), getUserIdentity());
      } catch (Throwable throwable) {
        throw rollbackCreateFailure(throwable, path);
      }

      eventPublisher.publishEvent(new CreateAppEvent(this, app.getName()));
    });
  }

  @Override
  public Try<Void> updateApp(String appName, AppInfo app) {
    return updateAppInfo(appName, app, "更新应用信息");
  }

  private Try<Void> updateAppInfo(String workspaceAppName, AppInfo app, String commitMessage) {
    var vcsManager = vcsManagerCenter.getVcsManager(workspaceAppName);
    var file = vcsManager.getFile(APP_INFO_FILE);

    var oldAppInfo = Json.tryToBean(AppInfo.class, file.getContent()).getOrElse(AppInfo::new);

    // 如果没有变化，不更新
    var isIconChanged = ObjectUtils.notEqual(app.getIcon(), oldAppInfo.getIcon());
    var isTitleChanged = ObjectUtils.notEqual(app.getTitle(), oldAppInfo.getTitle());
    var isDescriptionChanged = ObjectUtils.notEqual(app.getDescription(), oldAppInfo.getDescription());
    var isNameChanged = ObjectUtils.notEqual(app.getName(), oldAppInfo.getName());
    var isAppInfoChanged = isIconChanged || isTitleChanged || isDescriptionChanged || isNameChanged;

    if (!isAppInfoChanged) {
      return Try.success(null);
    }

    var optResult = vcsManager
        .updateFile(APP_INFO_FILE)
        .content(Json.toPrettyJsonString(app))
        .unlockWhenCommit()
        .commit(commitMessage);

    return optResult.map(ignore -> null);
  }

  @Override
  public Try<Void> deleteApp(String appId) {
    if (!canManageApp(appId, getUserIdentity())) {
      return Try.failure(new BusinessException("当前用户无权删除应用"));
    }

    List<AppEditScopeEntity> existingScopes = getAppEditScopes(appId);
    Optional<AppOwnerEntity> existingOwner = getAppOwner(appId);
    boolean movedToTrash = false;
    try {
      deleteScopes(appId);
      deleteOwner(appId);
      var path = Paths.get(getWorkspace(), appId);
      ensureTrashFolder();
      Files.move(path, Paths.get(getWorkspace(), TRASH_FOLDER, appId + "_at_" + Dates.getTimestamp()), StandardCopyOption.REPLACE_EXISTING);
      movedToTrash = true;
      eventPublisher.publishEvent(new DeleteAppEvent(this, appId));
      return Try.success(null);
    } catch (Throwable throwable) {
      Throwable failure = throwable;
      if (!movedToTrash) {
        try {
          restoreScopes(appId, existingScopes);
        } catch (Throwable restoreFailure) {
          failure = addSuppressed(failure, restoreFailure);
        }
        try {
          restoreOwner(existingOwner);
        } catch (Throwable restoreFailure) {
          failure = addSuppressed(failure, restoreFailure);
        }
      }
      return Try.failure(failure);
    }
  }

  @Override
  public Try<Void> renameApp(String appName, String newName) {
    if (Objects.equals(appName, newName)) {
      return Try.success(null);
    }
    if (appExists(newName)) {
      return Try.failure(new BusinessException("应用已存在"));
    }

    var appInfoResult = getAppInfo(appName);
    if (appInfoResult.isFailure()) {
      return Try.failure(appInfoResult.getCause());
    }
    var oldAppInfo = appInfoResult.get();
    var renamedAppInfo = oldAppInfo.clone().setName(newName);
    var oldPath = Paths.get(getWorkspace(), appName);
    var newPath = Paths.get(getWorkspace(), newName);

    try {
      Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      return Try.failure(new PlatformException("移动文件失败", e));
    }

    var updateAppInfoResult = updateAppInfo(newName, renamedAppInfo, String.format("重命名应用(%s -> %s)", appName, newName));
    if (updateAppInfoResult.isFailure()) {
      return rollbackRenameFailure(updateAppInfoResult.getCause(), newPath, oldPath);
    }

    boolean scopesRenamed = false;
    try {
      renameScopes(appName, newName);
      scopesRenamed = true;
      renameOwner(appName, newName);
      return Try.success(null);
    } catch (Throwable throwable) {
      Throwable failure = throwable;
      if (scopesRenamed) {
        try {
          renameScopes(newName, appName);
        } catch (Throwable rollbackFailure) {
          failure = addSuppressed(failure, rollbackFailure);
        }
      }
      var rollbackAppInfoResult = updateAppInfo(newName, oldAppInfo, String.format("回滚应用重命名(%s -> %s)", newName, appName));
      if (rollbackAppInfoResult.isFailure()) {
        failure = addSuppressed(failure, rollbackAppInfoResult.getCause());
      }
      return rollbackRenameFailure(failure, newPath, oldPath);
    }
  }

  @Override
  public Try<List<AppInfoWithMeta>> listApps(String category) {
    var allAppList = listApps();
    if (category == null || category.trim().isEmpty()) {
      return allAppList;
    }
    var categoryApps = appCategoryService.getApps(category);
    return allAppList.map(apps -> apps.stream()
        .filter(app -> categoryApps.contains(app.getAppInfo().getName()))
        .collect(Collectors.toList()));
  }

  @Override
  public Try<List<AppInfoWithMeta>> listApps() {
    return Try.of(() -> {
      String userIdentity = getUserIdentity();
      var path = Paths.get(getWorkspace());
      try (var stream = Files.list(path)) {
        return stream
            .filter(Files::isDirectory)
            .filter(p -> !p.getFileName().toString().equals(TRASH_FOLDER))
            .map(p -> {
              var appInfoFile = Paths.get(p.toString(), APP_INFO_FILE).toFile();
              if (!appInfoFile.exists() || appInfoFile.isDirectory()) {
                return null;
              }
              var creationTime = Try.of(() -> (FileTime) Files.getAttribute(appInfoFile.toPath(), "creationTime"))
                  .map(FileTime::toInstant)
                  .map(Dates::toLocalDateTime)
                  .getOrElse(Dates::now);
              var lastModifiedTime = Try.of(() -> (FileTime) Files.getAttribute(appInfoFile.toPath(), "lastModifiedTime"))
                  .map(FileTime::toInstant)
                  .map(Dates::toLocalDateTime)
                  .getOrElse(Dates::now);
              var contentStream = Try.of(() -> Files.readAllLines(appInfoFile.toPath()))
                  .map(lines -> lines.stream().collect(Collectors.joining()))
                  .getOrElse("");
              if (contentStream.trim().isEmpty()) {
                return null;
              }
              var info = Json.toBean(AppInfo.class, contentStream);
              if (info == null) {
                return null;
              }
              AppInfoWithMeta appInfoWithMeta = new AppInfoWithMeta(info, creationTime, lastModifiedTime);
              appInfoWithMeta.setCanManageEditAccess(appEditAccessEvaluator != null
                  && appEditAccessEvaluator.canManageEditAccess(info.getName(), userIdentity));
              return appInfoWithMeta;
            })
            .filter(Objects::nonNull)
            .filter(app -> canViewApp(app.getAppInfo().getName(), userIdentity))
            .sorted(Comparator.comparing(AppInfoWithMeta::getCreatedAt).reversed())
            .collect(Collectors.toList());
      }
    });
  }

  @Override
  public boolean appExists(String appName) {
    var path = Paths.get(getWorkspace(), appName);
    return Files.exists(path);
  }

  @Override
  public Try<Void> importApp(InputStream zipInputStream) {
    return Try.of(() -> {
      // 创建临时目录
      Path tempDir = Files.createTempDirectory("app_import_");
      Path tempZipFile = tempDir.resolve("app.zip");

      try {
        // 将输入流写入临时文件（Java 8兼容方式）
        try (FileOutputStream fos = new FileOutputStream(tempZipFile.toFile())) {
          byte[] buffer = new byte[8192];
          int bytesRead;
          while ((bytesRead = zipInputStream.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
          }
        }

        // 解析app.metadata.json获取应用名称
        ZipReader zipReader = new ZipReader();
        String metadataContent = zipReader.peekFileContentAsString(tempZipFile.toFile(), APP_INFO_FILE);
        if (metadataContent == null || metadataContent.trim().isEmpty()) {
          throw new PlatformException("压缩包中未找到 " + APP_INFO_FILE + " 文件");
        }

        AppInfo appInfo = Json.toBean(AppInfo.class, metadataContent);
        if (appInfo == null || appInfo.getName() == null || appInfo.getName().trim().isEmpty()) {
          throw new PlatformException(APP_INFO_FILE + " 文件格式错误或缺少name字段");
        }

        String originalAppName = appInfo.getName().trim();
        String finalAppName = generateUniqueAppName(originalAppName);
        Path targetDir = null;

        // 如果名称发生变化，更新AppInfo
        if (!originalAppName.equals(finalAppName)) {
          appInfo.setName(finalAppName);
        }

        try {
          // 解压到目标目录
          targetDir = Paths.get(getWorkspace(), finalAppName);
          Files.createDirectories(targetDir);

          zipReader.extractFile(tempZipFile.toFile(), targetDir.toString());

          // 更新app.metadata.json文件中的名称
          if (!originalAppName.equals(finalAppName)) {
            Path metadataFile = targetDir.resolve(APP_INFO_FILE);
            Files.write(metadataFile, Json.toPrettyJsonString(appInfo).getBytes());
          }

          // 检查是否为Git仓库，如果不是则初始化
          Path gitDir = targetDir.resolve(".git");
          if (!Files.exists(gitDir)) {
            initializeGitRepository(finalAppName);
          }

          assignOwner(finalAppName, getUserIdentity(), getUserIdentity());
        } catch (Throwable throwable) {
          throw rollbackCreateFailure(throwable, targetDir);
        }

        // 发布应用创建事件
        eventPublisher.publishEvent(new CreateAppEvent(this, finalAppName));

        return null;
      } finally {
        // 清理临时文件
        try {
          Files.walk(tempDir)
              .sorted(Comparator.reverseOrder())
              .map(Path::toFile)
              .forEach(File::delete);
        } catch (IOException e) {
          logger.warn("导入应用后清理临时目录失败: {}", tempDir, e);
        }
      }
    });
  }

  @Override
  public Try<InputStream> exportApp(String appName, boolean includeUncommitted, boolean includeGitRepo) {
    return Try.of(() -> {
      if (!appExists(appName)) {
        throw new PlatformException("应用不存在: " + appName);
      }

      // 创建临时文件
      Path tempFile = Files.createTempFile("app_export_" + appName + "_", ".zip");

      try (ZipWriter zipWriter = new ZipWriter(tempFile.toFile())) {
        zipWriter.setComment("应用导出: " + appName);

        if (includeUncommitted) {
          // 直接从文件系统读取
          exportFromFileSystem(appName, zipWriter, includeGitRepo);
        } else {
          // 从HEAD读取
          exportFromGitHead(appName, zipWriter, includeGitRepo);
        }

        zipWriter.finish();
      }

      // 返回自动删除的文件输入流
      return new AutoDeletingFileInputStream(tempFile.toFile());
    });
  }

  private boolean canViewApp(String appName, String userIdentity) {
    if (appEditAccessEvaluator == null) {
      return true;
    }
    return appEditAccessEvaluator.canView(appName, userIdentity);
  }

  private boolean canManageApp(String appName, String userIdentity) {
    if (appEditAccessEvaluator == null) {
      return true;
    }
    return appEditAccessEvaluator.canManageApp(appName, userIdentity);
  }

  private List<AppEditScopeEntity> getAppEditScopes(String appName) {
    if (appEditScopeService == null) {
      return Collections.emptyList();
    }
    return new ArrayList<>(appEditScopeService.getByAppName(appName));
  }

  private void renameScopes(String appName, String newName) {
    if (appEditScopeService == null) {
      return;
    }
    appEditScopeService.renameScopes(appName, newName);
  }

  private void deleteScopes(String appName) {
    if (appEditScopeService == null) {
      return;
    }
    appEditScopeService.deleteScopes(appName);
  }

  private Optional<AppOwnerEntity> getAppOwner(String appName) {
    if (appOwnerService == null) {
      return Optional.empty();
    }
    return appOwnerService.getByAppName(appName);
  }

  private void assignOwner(String appName, String ownerUserIdentity, String operator) {
    if (appOwnerService == null) {
      return;
    }
    appOwnerService.assignOwner(appName, ownerUserIdentity, operator);
  }

  private void renameOwner(String appName, String newAppName) {
    if (appOwnerService == null) {
      return;
    }
    appOwnerService.renameOwner(appName, newAppName);
  }

  private void deleteOwner(String appName) {
    if (appOwnerService == null) {
      return;
    }
    appOwnerService.deleteOwner(appName);
  }

  private void restoreOwner(Optional<AppOwnerEntity> owner) {
    if (appOwnerService == null || owner == null || !owner.isPresent()) {
      return;
    }
    AppOwnerEntity ownerEntity = owner.get();
    appOwnerService.assignOwner(ownerEntity.getAppName(),
        ownerEntity.getOwnerUserIdentity(),
        ObjectUtils.defaultIfNull(ownerEntity.getCreatedBy(), getUserIdentity()));
  }

  private void restoreScopes(String appName, List<AppEditScopeEntity> scopes) {
    if (appEditScopeService == null) {
      return;
    }
    appEditScopeService.replaceScopes(appName, buildConfigRequest(scopes), getUserIdentity());
  }

  private AppEditAccessConfigRequest buildConfigRequest(List<AppEditScopeEntity> scopes) {
    AppEditAccessConfigRequest request = new AppEditAccessConfigRequest();
    List<AppEditAccessSubject> users = new ArrayList<>();
    List<AppEditAccessSubject> roles = new ArrayList<>();
    List<AppEditAccessSubject> organizations = new ArrayList<>();
    request.setUsers(users);
    request.setRoles(roles);
    request.setOrganizations(organizations);

    for (AppEditScopeEntity scope : scopes) {
      if (scope == null || scope.getSubjectType() == null) {
        continue;
      }

      AppEditAccessSubject subject = new AppEditAccessSubject();
      subject.setId(scope.getSubjectId());
      subject.setName(scope.getSubjectName());
      subject.setType(scope.getSubjectType());

      AppEditScopeSubjectType subjectType = AppEditScopeSubjectType.valueOf(scope.getSubjectType());
      switch (subjectType) {
        case USER:
          users.add(subject);
          break;
        case ROLE:
          roles.add(subject);
          break;
        case ORG:
          organizations.add(subject);
          break;
        default:
          break;
      }
    }
    return request;
  }

  private Try<Void> rollbackRenameFailure(Throwable failure, Path currentPath, Path rollbackPath) {
    try {
      Files.move(currentPath, rollbackPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (Throwable rollbackFailure) {
      failure = addSuppressed(failure, rollbackFailure);
    }
    return Try.failure(failure);
  }

  private Throwable rollbackCreateFailure(Throwable failure, Path appPath) {
    if (appPath == null || !Files.exists(appPath)) {
      return failure;
    }
    try {
      deletePath(appPath);
    } catch (Throwable rollbackFailure) {
      failure = addSuppressed(failure, rollbackFailure);
    }
    return failure;
  }

  private Throwable addSuppressed(Throwable failure, Throwable rollbackFailure) {
    if (failure == null) {
      return rollbackFailure;
    }
    if (rollbackFailure != null && rollbackFailure != failure) {
      failure.addSuppressed(rollbackFailure);
    }
    return failure;
  }

  private void deletePath(Path path) throws IOException {
    try (var stream = Files.walk(path)) {
      for (Path current : stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
        Files.deleteIfExists(current);
      }
    }
  }

  /**
   * 生成唯一的应用名称（如果存在同名则添加后缀）
   */
  private String generateUniqueAppName(String originalName) {
    String baseName = originalName;
    int counter = 1;

    while (appExists(baseName)) {
      baseName = originalName + "_" + counter;
      counter++;
    }

    return baseName;
  }

  /**
   * 初始化Git仓库
   */
  private void initializeGitRepository(String appName) {
    try {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      // 通过获取文件来触发Git仓库初始化
      vcsManager.getFile(APP_INFO_FILE);
    } catch (Exception e) {
      throw new PlatformException("初始化Git仓库失败: " + e.getMessage(), e);
    }
  }

  /**
   * 从文件系统导出应用
   */
  private void exportFromFileSystem(String appName, ZipWriter zipWriter, boolean includeGitRepo) throws IOException {
    Path appDir = Paths.get(getWorkspace(), appName);

    Files.walk(appDir)
        .filter(Files::isRegularFile)
        .filter(path -> includeGitRepo || !path.toString().contains(".git"))
        .forEach(path -> {
          try {
            String relativePath = appDir.relativize(path).toString().replace("\\", "/");
            byte[] content = Files.readAllBytes(path);
            zipWriter.addEntry(relativePath, content);
          } catch (IOException e) {
            logger.warn("导出应用时跳过无法读取的文件: {}", path, e);
          }
        });
  }

  /**
   * 从Git HEAD导出应用
   */
  private void exportFromGitHead(String appName, ZipWriter zipWriter, boolean includeGitRepo) throws IOException {
    VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);

    try {
      // 获取HEAD版本的所有文件
      List<VCSFile> headFiles = vcsManager.getFileList("", "HEAD");

      // 添加Git版本控制的文件
      for (VCSFile vcsFile : headFiles) {
        // 排除目录
        if (!vcsFile.getPath().endsWith("/")) {
          try {
            VCSFile headFile = vcsFile.atRevision("HEAD");
            byte[] content = headFile.getBytesArray();
            if (content != null) {
              zipWriter.addEntry(vcsFile.getPath(), content);
            }
          } catch (Exception e) {
            logger.warn("导出应用 HEAD 文件时跳过无法读取的文件: {}", vcsFile.getPath(), e);
          }
        }
      }

      // 如果需要包含.git目录，从文件系统读取.git相关文件
      if (includeGitRepo) {
        Path appDir = Paths.get(getWorkspace(), appName);
        Path gitDir = appDir.resolve(".git");

        if (Files.exists(gitDir)) {
          Files.walk(gitDir)
              .filter(Files::isRegularFile)
              .forEach(path -> {
                try {
                  String relativePath = appDir.relativize(path).toString().replace("\\", "/");
                  byte[] content = Files.readAllBytes(path);
                  zipWriter.addEntry(relativePath, content);
                } catch (IOException e) {
                  logger.warn("导出应用 Git 仓库时跳过无法读取的文件: {}", path, e);
                }
              });
        }
      }
    } catch (Exception e) {
      throw new IOException("从Git HEAD读取文件失败: " + e.getMessage(), e);
    }
  }

}
