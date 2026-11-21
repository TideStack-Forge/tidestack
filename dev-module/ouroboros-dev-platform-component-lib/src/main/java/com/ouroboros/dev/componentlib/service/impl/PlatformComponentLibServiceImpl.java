package com.ouroboros.dev.componentlib.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.app.service.AppTemplateService;
import com.ouroboros.dev.componentlib.config.DevAppPlatformComponentLibConstConfig;
import com.ouroboros.dev.componentlib.config.PlatformComponentLibConfig;
import com.ouroboros.dev.componentlib.dto.ComponentDefineDTO;
import com.ouroboros.dev.componentlib.dto.DevAppPlatformComponentLibDTO;
import com.ouroboros.dev.componentlib.dto.ImportedPlatformComponentLibDTO;
import com.ouroboros.dev.componentlib.dto.PlatformComponentLibDTO;
import com.ouroboros.dev.componentlib.exception.ComponentLibConflictException;
import com.ouroboros.dev.componentlib.exception.ComponentLibValidationException;
import com.ouroboros.dev.componentlib.service.DevAppPlatformComponentLibService;
import com.ouroboros.dev.componentlib.service.PlatformComponentLibService;
import com.ouroboros.model.AppInfo;
import com.ouroboros.uimodel.microweb.MicroWebDeployService;
import com.ouroboros.util.Json;
import com.ouroboros.util.ZipReader;
import com.ouroboros.vcs.VCSManagerCenter;

@Service
public class PlatformComponentLibServiceImpl implements PlatformComponentLibService {
  private static final Logger logger = LoggerFactory.getLogger(PlatformComponentLibServiceImpl.class);
  private static final String MANIFEST_FILE_NAME = "components.manifest.json";

  private final PlatformComponentLibConfig config;
  private final MicroWebDeployService microWebDeployService;
  private final AppManagementService appManagementService;
  private final AppTemplateService appTemplateService;
  private final VCSManagerCenter vcsManagerCenter;
  private final ObjectProvider<DevAppPlatformComponentLibService> devAppComponentLibServiceProvider;

  public PlatformComponentLibServiceImpl(
      PlatformComponentLibConfig config,
      MicroWebDeployService microWebDeployService,
      AppManagementService appManagementService,
      AppTemplateService appTemplateService,
      VCSManagerCenter vcsManagerCenter,
      ObjectProvider<DevAppPlatformComponentLibService> devAppComponentLibService) {
    this.config = config;
    this.microWebDeployService = microWebDeployService;
    this.appManagementService = appManagementService;
    this.appTemplateService = appTemplateService;
    this.vcsManagerCenter = vcsManagerCenter;
    this.devAppComponentLibServiceProvider = devAppComponentLibService;
  }

  @Override
  public Optional<PlatformComponentLibDTO> get(String name) {
    File libRoot = new File(config.getComponentLibRoot());
    if (!libRoot.exists() || !libRoot.isDirectory()) {
      return Optional.empty();
    }

    File[] dirs = libRoot.listFiles(File::isDirectory);
    if (dirs == null) {
      return Optional.empty();
    }

    return Arrays.stream(dirs)
        .filter(dir -> dir.getName().startsWith(name + "@"))
        .findFirst()
        .flatMap(this::loadComponentLibFromDirectory);
  }

  @Override
  public List<PlatformComponentLibDTO> getAll() {
    File libRoot = new File(config.getComponentLibRoot());
    if (!libRoot.exists() || !libRoot.isDirectory()) {
      return Collections.emptyList();
    }

    File[] dirs = libRoot.listFiles(File::isDirectory);
    if (dirs == null) {
      return Collections.emptyList();
    }

    return Arrays.stream(dirs)
        .map(this::loadComponentLibFromDirectory)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  @Override
  public List<ComponentDefineDTO> getAllComponentDefine() {
    return getAll().stream()
        .flatMap(lib -> {
          List<ComponentDefineDTO> components = new ArrayList<>();
          components.addAll(lib.getRenderers());
          components.addAll(lib.getFormItems());
          components.addAll(lib.getOptionsControls());
          components.addAll(lib.getActions());
          components.addAll(lib.getRules());
          components.addAll(lib.getFormulas());
          components.addAll(lib.getRendererEditorPlugins());
          components.addAll(lib.getActionEditorPlugins());
          components.addAll(lib.getValidatorEditorPlugins());
          return components.stream();
        })
        .collect(Collectors.toList());
  }

  @Override
  public Try<PlatformComponentLibDTO> inspectComponentLibPackage(File zipFile) {
    return Try.of(() -> {
      if (!zipFile.exists() || !zipFile.isFile()) {
        throw new ComponentLibValidationException("ZIP文件不存在: " + zipFile.getAbsolutePath());
      }

      ZipReader reader = new ZipReader();
      if (!reader.containsFile(zipFile, MANIFEST_FILE_NAME)) {
        throw new ComponentLibValidationException("ZIP包中缺少 " + MANIFEST_FILE_NAME + " 文件");
      }

      String manifestContent = reader.peekFileContentAsString(zipFile, MANIFEST_FILE_NAME);
      PlatformComponentLibDTO componentLib = Json.toBean(PlatformComponentLibDTO.class, manifestContent);

      if (componentLib == null) {
        throw new ComponentLibValidationException("无法解析组件库配置文件");
      }

      // 验证必填字段
      if (componentLib.getName() == null || componentLib.getName().trim().isEmpty()) {
        throw new ComponentLibValidationException("组件库名称不能为空");
      }
      if (componentLib.getVersion() == null || componentLib.getVersion().trim().isEmpty()) {
        throw new ComponentLibValidationException("组件库版本不能为空");
      }
      if (componentLib.getPublicPath() == null || componentLib.getPublicPath().trim().isEmpty()) {
        throw new ComponentLibValidationException("组件库公共路径不能为空");
      }

      return componentLib;
    });
  }

  @Override
  public Try<Void> checkComponentLib(PlatformComponentLibDTO componentLib) {
    return Try.of(() -> {
      // 1. 检查名称和版本
      Optional<PlatformComponentLibDTO> existingOpt = get(componentLib.getName());
      if (existingOpt.isPresent()) {
        PlatformComponentLibDTO existing = existingOpt.get();
        if (!isVersionNewer(componentLib.getVersion(), existing.getVersion())) {
          throw new ComponentLibConflictException(
              String.format("组件库 %s 的版本 %s 不大于已安装的版本 %s",
                  componentLib.getName(), componentLib.getVersion(), existing.getVersion()));
        }
      }

      // 2. 检查 publicPath 是否重复
      String publicPath = componentLib.getPublicPath();
      for (PlatformComponentLibDTO lib : getAll()) {
        if (lib.getPublicPath().equals(publicPath) && !lib.getName().equals(componentLib.getName())) {
          throw new ComponentLibConflictException(
              String.format("公共路径 %s 已被组件库 %s 使用", publicPath, lib.getName()));
        }
      }

      // 3. 检查组件定义是否重复
      List<ComponentDefineDTO> allComponents = getAllComponentDefine();
      List<ComponentDefineDTO> newComponents = getAllComponentsFromLib(componentLib);

      for (ComponentDefineDTO newComp : newComponents) {
        for (ComponentDefineDTO existingComp : allComponents) {
          if (existingComp.getType().equals(newComp.getType())
              && existingComp.getName().equals(newComp.getName())) {
            // 检查是否来自同一个组件库
            boolean isSameLib = getAll().stream()
                .filter(lib -> lib.getName().equals(componentLib.getName()))
                .flatMap(lib -> getAllComponentsFromLib(lib).stream())
                .anyMatch(comp -> comp.getType().equals(existingComp.getType())
                    && comp.getName().equals(existingComp.getName()));

            if (!isSameLib) {
              throw new ComponentLibConflictException(
                  String.format("组件定义 %s:%s 已存在", newComp.getType(), newComp.getName()));
            }
          }
        }
      }

      return null;
    });
  }

  @Override
  public List<AppInfo> getRelatedApps(PlatformComponentLibDTO componentLib) {
    List<AppInfo> relatedApps = new ArrayList<>();

    // 检查所有应用
    Try<List<com.ouroboros.dev.app.model.AppInfoWithMeta>> appsResult = appManagementService.listApps();
    if (appsResult.isSuccess()) {
      for (var appWithMeta : appsResult.get()) {
        if (isAppUsingComponentLib(appWithMeta.getAppInfo().getName(), componentLib.getName())) {
          relatedApps.add(appWithMeta.getAppInfo());
        }
      }
    }

    return relatedApps;
  }

  @Override
  public Try<Void> installComponentLibPackage(File zipFile) {
    return inspectComponentLibPackage(zipFile)
        .flatMap(componentLib -> checkComponentLib(componentLib)
            .flatMap(v -> {
              // 创建组件库目录
              String dirName = componentLib.getName() + "@" + componentLib.getVersion();
              File targetDir = new File(config.getComponentLibRoot(), dirName);

              try {
                // 解压ZIP文件
                ZipReader reader = new ZipReader();
                reader.extractFile(zipFile, targetDir.getAbsolutePath());

                // 部署微前端资源
                deployMicroWeb(componentLib.getPublicPath(), targetDir);

                logger.info("成功安装组件库: {} version {}", componentLib.getName(), componentLib.getVersion());

                // 自动更新所有使用该组件库的应用
                updateRelatedApps(componentLib);

                return Try.success(null);
              } catch (Exception e) {
                // 清理失败的安装
                if (targetDir.exists()) {
                  Try.run(() -> FileUtils.deleteDirectory(targetDir));
                }
                return Try.failure(new RuntimeException("安装组件库失败: " + e.getMessage(), e));
              }
            }));
  }

  /**
   * 自动更新所有使用该组件库的应用
   */
  private void updateRelatedApps(PlatformComponentLibDTO componentLib) {
    List<AppInfo> relatedApps = getRelatedApps(componentLib);
    if (relatedApps.isEmpty()) {
      logger.info("组件库 {} 没有被任何应用使用，无需更新", componentLib.getName());
      return;
    }

    logger.info("组件库 {} 版本更新为 {}，开始更新 {} 个相关应用",
        componentLib.getName(), componentLib.getVersion(), relatedApps.size());
    var devAppComponentLibService = devAppComponentLibServiceProvider.getIfAvailable();
    if (devAppComponentLibService == null) {
      logger.warn("无法更新应用，请检查是否已启用组件库开发功能");
      return;
    }
    for (AppInfo app : relatedApps) {
      try {
        ImportedPlatformComponentLibDTO importDTO = new ImportedPlatformComponentLibDTO()
            .setName(componentLib.getName());
        devAppComponentLibService.importComponentLib(app.getName(), importDTO);
        logger.info("成功更新应用 {} 的组件库配置", app.getName());
      } catch (Exception e) {
        logger.error("更新应用 {} 的组件库配置失败", app.getName(), e);
      }
    }
  }

  @Override
  public Try<Void> uninstallComponentLib(PlatformComponentLibDTO componentLib) {
    return Try.of(() -> {
      // 检查是否有应用正在使用
      List<AppInfo> relatedApps = getRelatedApps(componentLib);
      if (!relatedApps.isEmpty()) {
        String appNames = relatedApps.stream()
            .map(AppInfo::getName)
            .collect(Collectors.joining(", "));
        throw new ComponentLibConflictException(
            String.format("无法卸载组件库 %s，以下应用正在使用: %s", componentLib.getName(), appNames));
      }

      // 删除组件库目录
      String dirName = componentLib.getName() + "@" + componentLib.getVersion();
      File targetDir = new File(config.getComponentLibRoot(), dirName);

      if (targetDir.exists()) {
        FileUtils.deleteDirectory(targetDir);
        logger.info("成功卸载组件库: {} version {}", componentLib.getName(), componentLib.getVersion());
      }

      microWebDeployService.remove(componentLib.getPublicPath());

      return null;
    });
  }

  // ========== 私有辅助方法 ==========

  private Optional<PlatformComponentLibDTO> loadComponentLibFromDirectory(File dir) {
    File manifestFile = new File(dir, MANIFEST_FILE_NAME);
    if (!manifestFile.exists()) {
      logger.warn("组件库目录缺少manifest文件: {}", dir.getAbsolutePath());
      return Optional.empty();
    }

    try {
      PlatformComponentLibDTO lib = Json.toBean(PlatformComponentLibDTO.class,
          new FileInputStream(manifestFile));
      return Optional.ofNullable(lib);
    } catch (Exception e) {
      logger.error("加载组件库manifest失败: {}", dir.getAbsolutePath(), e);
      return Optional.empty();
    }
  }

  private List<ComponentDefineDTO> getAllComponentsFromLib(PlatformComponentLibDTO lib) {
    List<ComponentDefineDTO> components = new ArrayList<>();
    components.addAll(lib.getRenderers());
    components.addAll(lib.getFormItems());
    components.addAll(lib.getOptionsControls());
    components.addAll(lib.getActions());
    components.addAll(lib.getRules());
    components.addAll(lib.getFormulas());
    components.addAll(lib.getRendererEditorPlugins());
    components.addAll(lib.getActionEditorPlugins());
    components.addAll(lib.getValidatorEditorPlugins());
    return components;
  }

  private boolean isVersionNewer(String newVersion, String oldVersion) {
    // 简单的版本比较，实际可以使用更复杂的semver比较
    try {
      String[] newParts = newVersion.split("\\.");
      String[] oldParts = oldVersion.split("\\.");

      int maxLen = Math.max(newParts.length, oldParts.length);
      for (int i = 0; i < maxLen; i++) {
        int newPart = i < newParts.length ? Integer.parseInt(newParts[i]) : 0;
        int oldPart = i < oldParts.length ? Integer.parseInt(oldParts[i]) : 0;

        if (newPart > oldPart) {
          return true;
        } else if (newPart < oldPart) {
          return false;
        }
      }
      return false; // 版本相同
    } catch (NumberFormatException e) {
      // 如果解析失败，使用字符串比较
      return newVersion.compareTo(oldVersion) > 0;
    }
  }

  private boolean isAppUsingComponentLib(String appName, String componentLibName) {
    try {
      var vcsManager = vcsManagerCenter.getVcsManager(appName);
      var file = vcsManager.getFile(DevAppPlatformComponentLibConstConfig.DEV_APP_COMPONENT_LIB_FILENAME);

      if (!file.exists()) {
        return false;
      }

      String content = file.getContent();
      DevAppPlatformComponentLibDTO dto = Json.toBean(DevAppPlatformComponentLibDTO.class, content);

      if (dto == null) {
        return false;
      }

      return dto.getImports().stream()
          .anyMatch(imported -> imported.getName().equals(componentLibName));
    } catch (Exception e) {
      logger.warn("检查应用 {} 是否使用组件库 {} 时出错", appName, componentLibName, e);
      return false;
    }
  }

  private void deployMicroWeb(String publicPath, File distDir) throws IOException {
    // 遍历dist目录下的所有文件，部署到微前端服务
    microWebDeployService.deploy(publicPath, distDir);
  }
}
