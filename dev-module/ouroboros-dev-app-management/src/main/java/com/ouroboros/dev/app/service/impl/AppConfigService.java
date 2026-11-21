package com.ouroboros.dev.app.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.service.runtime.LogicFlowDebugHelperArtifactMaterializer;
import com.ouroboros.dev.datasource.model.AppDataSourceModel;
import com.ouroboros.dev.datasource.model.PlatformDataSourceModel;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;

import com.ouroboros.util.Strings;

@Service
public class AppConfigService {

  private static final Logger logger = LoggerFactory.getLogger(AppConfigService.class);

  private final DevelopWorkspaceContext developWorkspaceContext;
  private final AppDataSourceService appDataSourceService;
  private final PlatformDataSourceService platformDataSourceService;
  private final LogicFlowDebugHelperArtifactMaterializer logicFlowDebugHelperArtifactMaterializer;

  public AppConfigService(DevelopWorkspaceContext developWorkspaceContext,
                          AppDataSourceService appDataSourceService,
                          PlatformDataSourceService platformDataSourceService) {
    this(developWorkspaceContext, appDataSourceService, platformDataSourceService, null);
  }

  @Autowired
  public AppConfigService(DevelopWorkspaceContext developWorkspaceContext,
                          AppDataSourceService appDataSourceService,
                          PlatformDataSourceService platformDataSourceService,
                          LogicFlowDebugHelperArtifactMaterializer logicFlowDebugHelperArtifactMaterializer) {
    this.developWorkspaceContext = developWorkspaceContext;
    this.appDataSourceService = appDataSourceService;
    this.platformDataSourceService = platformDataSourceService;
    this.logicFlowDebugHelperArtifactMaterializer = logicFlowDebugHelperArtifactMaterializer;
  }

  public Try<AppContainerRequest> prepareRequest(AppContainerRequest request) {
    return resolveAdditionalDatasourceEnv(request.getAppName(), request.getEnvironmentName())
        .map(additionalEnv -> {
          Map<String, String> env = new LinkedHashMap<String, String>();
          if (request.getEnv() != null) {
            env.putAll(request.getEnv());
          }
          env.putAll(additionalEnv);

          AppContainerRequest copy = new AppContainerRequest();
          copy.setAppName(request.getAppName());
          copy.setImage(request.getImage());
          copy.setBackendType(request.getBackendType());
          copy.setExposureStrategy(request.getExposureStrategy());
          copy.setPortBindings(request.getPortBindings());
          copy.setEnvironmentName(request.getEnvironmentName());
          copy.setDatasource(request.getDatasource());
          copy.setConfigFile(request.getConfigFile());
          copy.setEnv(env);
          return copy;
        });
  }

  public void resetConfigFile(String appName, String profile, AppContainerRequest.ConfigFile configFile) throws Exception {
    if (configFile == null || !configFile.isReset()) {
      return;
    }
    Path configFilePath = Paths.get(developWorkspaceContext.getRunRootDir(), appName, profile, "application.yml");
    if (Files.exists(configFilePath) && !configFilePath.toFile().delete()) {
      throw new PlatformException("删除配置文件失败！");
    }
  }

  private Try<Map<String, String>> resolveAdditionalDatasourceEnv(String appName, String environmentName) {
    return getAdditionalDatasource(appName, environmentName)
        .map(additionalDatasourceMap -> additionalDatasourceMap.entrySet().stream()
            .flatMap(outerEntry -> outerEntry.getValue().entrySet().stream().map(innerEntry -> {
              String envKey = String.format(
                  "OUROBOROS_ADDITIONAL-DATASOURCE_%s_%s",
                  outerEntry.getKey(),
                  Strings.toKebabCase(innerEntry.getKey()).toUpperCase()
              );
              String envValue = ObjectUtils.defaultIfNull(innerEntry.getValue(), "").toString();
              if (Objects.equals(innerEntry.getKey(), "type")
                  && innerEntry.getValue() != null
                  && "jdbc".equalsIgnoreCase(innerEntry.getValue().toString())) {
                envValue = "com.alibaba.druid.pool.DruidDataSource";
              }
              return Tuple.of(envKey, envValue);
            }))
            .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2, (left, right) -> right, LinkedHashMap::new)));
  }

  private Try<Map<String, Map<String, Object>>> getAdditionalDatasource(String appName, String environmentName) {
    List<AppDataSourceModel> appDataSourceModels = appDataSourceService.getByAppName(appName);
    if (appDataSourceModels == null || appDataSourceModels.isEmpty()) {
      return Try.success(Collections.emptyMap());
    }

    List<String> platformDataSourceNames = appDataSourceModels.stream()
        .filter(model -> !Objects.equals(model.getDataStationName(), "default"))
        .map(AppDataSourceModel::getPlatformDataSourceName)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
    if (platformDataSourceNames.isEmpty()) {
      return Try.success(Collections.emptyMap());
    }

    List<PlatformDataSourceModel> platformDataSourceModels = platformDataSourceService.getByNames(platformDataSourceNames);
    if (platformDataSourceModels == null) {
      platformDataSourceModels = Collections.emptyList();
    }
    if (platformDataSourceNames.size() != platformDataSourceModels.size()) {
      Set<String> names = platformDataSourceModels.stream().map(PlatformDataSourceModel::getName).collect(Collectors.toSet());
      String notExistNames = platformDataSourceNames.stream().filter(name -> !names.contains(name)).collect(Collectors.joining(","));
      return Try.failure(new BusinessException("应用数据源配置错误，平台数据源: " + notExistNames + "不存在!"));
    }

    Map<String, String> platformDataSourceNameAppDatasourceNameMap = appDataSourceModels.stream()
        .collect(Collectors.toMap(
            AppDataSourceModel::getPlatformDataSourceName,
            AppDataSourceModel::getDataStationName,
            (left, right) -> right
        ));
    Map<String, Map<String, Object>> additionalDataSources = platformDataSourceModels.stream()
        .filter(model -> model.getDefinition().containsKey(environmentName))
        .collect(Collectors.toMap(
            model -> platformDataSourceNameAppDatasourceNameMap.get(model.getName()),
            model -> model.getDefinition().get(environmentName),
            (left, right) -> right,
            LinkedHashMap::new
        ));

    if (additionalDataSources.size() != platformDataSourceNames.size()) {
      Set<String> names = additionalDataSources.keySet();
      String notExistNames = appDataSourceModels.stream()
          .map(AppDataSourceModel::getPlatformDataSourceName)
          .distinct()
          .filter(platformDatasourceName -> !names.contains(platformDatasourceName))
          .collect(Collectors.joining(","));
      return Try.failure(new BusinessException("应用数据源配置错误，平台数据源: " + notExistNames + "在" + environmentName + "环境中未配置!"));
    }

    return Try.success(additionalDataSources);
  }

  public Try<Void> buildRunWorkspace(String appName, String profile) {
    Path source = Paths.get(developWorkspaceContext.getDevRootDir(), appName);
    Path target = Paths.get(developWorkspaceContext.getRunRootDir(), appName, profile, "metadata");

    Path tempTargetHolder = null;
    try {
      Files.createDirectories(target.getParent());
      tempTargetHolder = Files.createTempDirectory(target.getParent(), ".workspace-staging-");
      final Path tempTarget = tempTargetHolder;

      Path gitPath = Paths.get(source.toString(), ".git");
      try (java.util.stream.Stream<Path> walk = Files.walk(source)) {
        walk.filter(sourcePath -> !sourcePath.startsWith(gitPath))
            .filter(sourcePath -> !Files.isSymbolicLink(sourcePath))
            .forEach(sourcePath -> {
              Path stagingPath = tempTarget.resolve(source.relativize(sourcePath));
              if (Files.isDirectory(sourcePath)) {
                if (Files.notExists(stagingPath)) {
                  createDirectories(stagingPath);
                }
                return;
              }
              copyPath(sourcePath, stagingPath);
            });
      }

      materializeLogicFlowDebugHelper().get();
      Path debugSupportWorkspaceRoot = Paths.get(developWorkspaceContext.getDebugSupportFilesDir());
      if (Files.exists(debugSupportWorkspaceRoot)) {
        Path stagingExtendPath = tempTarget.resolveSibling(tempTarget.getFileName() + "-extend");
        Files.createDirectories(stagingExtendPath);
        final Path finalStagingExtendPath = stagingExtendPath;
        try (java.util.stream.Stream<Path> walk = Files.walk(debugSupportWorkspaceRoot)) {
          walk.filter(sourcePath -> !Files.isSymbolicLink(sourcePath))
              .forEach(sourcePath -> {
                Path stagingPath = finalStagingExtendPath.resolve(debugSupportWorkspaceRoot.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                  if (Files.notExists(stagingPath)) {
                    createDirectories(stagingPath);
                  }
                  return;
                }
                copyPath(sourcePath, stagingPath);
              });
        }

        Path runWorkspaceExtendPath = Paths.get(developWorkspaceContext.getRunRootDir(), appName, profile, "extend");
        if (Files.exists(runWorkspaceExtendPath)) {
          try (java.util.stream.Stream<Path> walk = Files.walk(runWorkspaceExtendPath)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(path -> Try.run(() -> deletePath(path)));
          }
        }
        Files.move(stagingExtendPath, runWorkspaceExtendPath, StandardCopyOption.ATOMIC_MOVE);
      }

      if (Files.exists(target)) {
        try (java.util.stream.Stream<Path> walk = Files.walk(target)) {
          walk.sorted(Comparator.reverseOrder())
              .forEach(path -> Try.run(() -> deletePath(path)));
        }
      }
      Files.move(tempTarget, target, StandardCopyOption.ATOMIC_MOVE);
      tempTargetHolder = null;

      return Try.success(null);
    } catch (Exception e) {
      if (tempTargetHolder != null && Files.exists(tempTargetHolder)) {
        try (java.util.stream.Stream<Path> walk = Files.walk(tempTargetHolder)) {
          walk.sorted(Comparator.reverseOrder())
              .forEach(path -> Try.run(() -> deletePath(path)));
        } catch (Exception cleanupEx) {
          logger.warn("清理临时工作空间失败", cleanupEx);
        }
      }
      return Try.failure(new PlatformException("构建运行工作空间失败！", e));
    }
  }

  private Try<Path> materializeLogicFlowDebugHelper() {
    if (logicFlowDebugHelperArtifactMaterializer == null) {
      return Try.success(null);
    }
    return logicFlowDebugHelperArtifactMaterializer.materialize();
  }

  public void moveRunWorkspaceToTrash(String appName) {
    Path appRunPath = Paths.get(developWorkspaceContext.getRunRootDir(), appName);
    if (!Files.exists(appRunPath)) {
      return;
    }

    Path trashRoot = Paths.get(developWorkspaceContext.getRunRootDir(), "_trash");
    Try.run(() -> Files.createDirectories(trashRoot))
        .onFailure(error -> logger.warn("创建trash目录失败", error));

    Path trashPath = Paths.get(
        trashRoot.toString(),
        appName + "_at_" + System.currentTimeMillis()
    );
    Try.run(() -> Files.move(appRunPath, trashPath, StandardCopyOption.REPLACE_EXISTING))
        .onFailure(error -> logger.warn("移动应用运行目录到trash失败: {}", appName, error));
  }

  private void deletePath(Path path) throws Exception {
    Files.delete(path);
  }

  private void createDirectories(Path path) {
    Try.run(() -> Files.createDirectories(path)).get();
  }

  private void copyPath(Path sourcePath, Path targetPath) {
    Try.run(() -> {
      if (targetPath.getParent() != null) {
        Files.createDirectories(targetPath.getParent());
      }
      Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }).get();
  }
}
