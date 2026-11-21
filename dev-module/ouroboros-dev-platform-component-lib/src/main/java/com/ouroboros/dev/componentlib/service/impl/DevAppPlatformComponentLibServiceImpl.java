package com.ouroboros.dev.componentlib.service.impl;

import java.util.ArrayList;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.dev.componentlib.config.DevAppPlatformComponentLibConstConfig;
import com.ouroboros.dev.componentlib.dto.DevAppPlatformComponentLibDTO;
import com.ouroboros.dev.componentlib.dto.ImportedPlatformComponentLibDTO;
import com.ouroboros.dev.componentlib.dto.PlatformComponentLibDTO;
import com.ouroboros.dev.componentlib.exception.ComponentLibNotFoundException;
import com.ouroboros.dev.componentlib.service.DevAppPlatformComponentLibService;
import com.ouroboros.dev.componentlib.service.PlatformComponentLibService;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.file.VCSFile;

@Service
public class DevAppPlatformComponentLibServiceImpl implements DevAppPlatformComponentLibService {
  private static final Logger logger = LoggerFactory.getLogger(DevAppPlatformComponentLibServiceImpl.class);

  private final VCSManagerCenter vcsManagerCenter;
  private final PlatformComponentLibService platformComponentLibService;

  public DevAppPlatformComponentLibServiceImpl(
      VCSManagerCenter vcsManagerCenter,
      PlatformComponentLibService platformComponentLibService) {
    this.vcsManagerCenter = vcsManagerCenter;
    this.platformComponentLibService = platformComponentLibService;
  }

  @Override
  public DevAppPlatformComponentLibDTO getDevAppComponentLib(String appName) {
    try {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      VCSFile file = vcsManager.getFile(DevAppPlatformComponentLibConstConfig.DEV_APP_COMPONENT_LIB_FILENAME);

      if (!file.exists()) {
        return new DevAppPlatformComponentLibDTO().setImports(new ArrayList<>());
      }

      String content = file.getContent();
      DevAppPlatformComponentLibDTO result = Json.toBean(DevAppPlatformComponentLibDTO.class, content);
      return result != null ? result : new DevAppPlatformComponentLibDTO().setImports(new ArrayList<>());
    } catch (Exception e) {
      logger.error("读取应用 {} 的组件库配置失败", appName, e);
      return new DevAppPlatformComponentLibDTO().setImports(new ArrayList<>());
    }
  }

  @Override
  public void importComponentLib(String appName, ImportedPlatformComponentLibDTO componentLibImportDTO) {
    Try.run(() -> {
      // 验证组件库是否存在
      Optional<PlatformComponentLibDTO> componentLibOpt =
          platformComponentLibService.get(componentLibImportDTO.getName());
      if (!componentLibOpt.isPresent()) {
        throw new ComponentLibNotFoundException(componentLibImportDTO.getName());
      }

      PlatformComponentLibDTO componentLib = componentLibOpt.get();

      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      VCSFile file = vcsManager.getFile(DevAppPlatformComponentLibConstConfig.DEV_APP_COMPONENT_LIB_FILENAME);

      DevAppPlatformComponentLibDTO config;
      if (file.exists()) {
        String content = file.getContent();
        config = Json.toBean(DevAppPlatformComponentLibDTO.class, content);
        if (config == null) {
          config = new DevAppPlatformComponentLibDTO().setImports(new ArrayList<>());
        }
      } else {
        config = new DevAppPlatformComponentLibDTO().setImports(new ArrayList<>());
      }

      // 检查是否已导入，如果已导入则更新版本和publicPath
      var imports = new ArrayList<>(config.getImports());
      boolean alreadyImported = imports.removeIf(imported -> imported.getName().equals(componentLib.getName()));

      // 创建完整的导入配置
      ImportedPlatformComponentLibDTO importedLib = new ImportedPlatformComponentLibDTO()
          .setName(componentLib.getName())
          .setPublicPath(componentLib.getPublicPath())
          .setVersion(componentLib.getVersion());

      imports.add(importedLib);
      config.setImports(imports);

      // 写入文件
      String newContent = Json.toPrettyJsonString(config);

      String action = alreadyImported ? "更新" : "导入";
      if (file.exists()) {
        vcsManager.updateFile(DevAppPlatformComponentLibConstConfig.DEV_APP_COMPONENT_LIB_FILENAME)
            .content(newContent)
            .commit(String.format("%s平台组件库: %s (v%s)", action, componentLib.getName(), componentLib.getVersion()));
      } else {
        vcsManager.createFile(DevAppPlatformComponentLibConstConfig.DEV_APP_COMPONENT_LIB_FILENAME)
            .content(newContent)
            .commit(String.format("创建组件库配置并导入: %s (v%s)", componentLib.getName(), componentLib.getVersion()));
      }

      logger.info("成功{}组件库 {} (v{}) 到应用 {}", action, componentLib.getName(), componentLib.getVersion(), appName);
    }).onFailure(e -> {
      logger.error("导入组件库到应用 {} 失败", appName, e);
      throw new PlatformException("导入组件库失败: " + e.getMessage(), e);
    });
  }

  @Override
  public void removeComponentLib(String appName, String componentLibName) {
    Try.run(() -> {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      VCSFile file = vcsManager.getFile(DevAppPlatformComponentLibConstConfig.DEV_APP_COMPONENT_LIB_FILENAME);

      if (!file.exists()) {
        logger.warn("应用 {} 的组件库配置文件不存在", appName);
        return;
      }

      String content = file.getContent();
      DevAppPlatformComponentLibDTO config = Json.toBean(DevAppPlatformComponentLibDTO.class, content);
      if (config == null) {
        logger.warn("应用 {} 的组件库配置文件解析失败", appName);
        return;
      }

      // 移除组件库
      var imports = new ArrayList<>(config.getImports());
      boolean removed = imports.removeIf(imported -> imported.getName().equals(componentLibName));

      if (!removed) {
        logger.warn("组件库 {} 未在应用 {} 中导入", componentLibName, appName);
        return;
      }

      config.setImports(imports);

      // 写入文件
      String newContent = Json.toPrettyJsonString(config);

      vcsManager.updateFile(DevAppPlatformComponentLibConstConfig.DEV_APP_COMPONENT_LIB_FILENAME)
          .content(newContent)
          .commit(String.format("移除平台组件库: %s", componentLibName));

      logger.info("成功从应用 {} 移除组件库 {}", appName, componentLibName);
    }).onFailure(e -> {
      logger.error("从应用 {} 移除组件库失败", appName, e);
      throw new PlatformException("移除组件库失败: " + e.getMessage(), e);
    });
  }
}
