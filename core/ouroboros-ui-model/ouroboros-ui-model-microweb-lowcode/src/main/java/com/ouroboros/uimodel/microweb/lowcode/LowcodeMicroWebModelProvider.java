package com.ouroboros.uimodel.microweb.lowcode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ouroboros.AppContext;
import com.ouroboros.uimodel.microweb.MicroWebModel;
import com.ouroboros.uimodel.microweb.MicroWebModelProvider;
import com.ouroboros.util.Json;

@Component
public class LowcodeMicroWebModelProvider implements MicroWebModelProvider {
  private final static Logger logger = LoggerFactory.getLogger(LowcodeMicroWebModelProvider.class);
  private final static String MICRO_WEB_DIR = "micro-web";
  private final static String MICRO_WEB_PACKAGE_DIR = "package";
  private final static String MODEL_SUFFIX = ".micro-web.json";
  private final static String LEGACY_MODEL_SUFFIX = ".json";
  @Autowired
  AppContext appContext;

  public LowcodeMicroWebModelProvider() {
    SingletonHolder.INSTANCE = this;
  }

  @Override
  public List<? extends MicroWebModel> getAllMicroWebModel() {
    var rootPath = Paths.get(appContext.getHomePath(), MICRO_WEB_DIR);
    if (!rootPath.toFile().exists()) {
      return Collections.emptyList();
    }
    Map<String, LowcodeMicroWebModel> models = new TreeMap<>(String::compareToIgnoreCase);
    loadModels(rootPath, models);
    return models.values().stream().collect(Collectors.toList());
  }

  private void loadModels(Path rootPath, Map<String, LowcodeMicroWebModel> models) {
    loadModels(rootPath, LEGACY_MODEL_SUFFIX, true, models);
    loadModels(rootPath, MODEL_SUFFIX, false, models);
  }

  private void loadModels(Path rootPath, String modelSuffix, boolean excludeTypedSuffix,
      Map<String, LowcodeMicroWebModel> models) {
    try (var walkStream = Files.walk(rootPath)) {
      walkStream.filter(Files::isRegularFile)
          .filter(path -> isDirectModelFile(rootPath, path))
          .filter(path -> path.getFileName().toString().endsWith(modelSuffix))
          .filter(path -> !excludeTypedSuffix || !path.getFileName().toString().endsWith(MODEL_SUFFIX))
          .map(path -> readModel(rootPath, path))
          .filter(Objects::nonNull)
          .filter(model -> StringUtils.isNotBlank(model.getName()))
          .forEach(model -> models.put(model.getName(), model));
    } catch (IOException e) {
      logger.error("获取低代码微前端模型失败", e);
    }
  }

  private LowcodeMicroWebModel readModel(Path rootPath, Path path) {
    try {
      var modelContent = FileUtils.readFileToString(path.toFile(), "UTF-8");
      var result = Json.tryToBean(LowcodeMicroWebModel.class, modelContent);
      if (result.isFailure()) {
        logger.error("加载低代码微前端模型({})失败", rootPath.relativize(path), result.getCause());
        return null;
      }
      var model = result.get();
      model.appContext = appContext;
      return model;
    } catch (IOException e) {
      logger.error("读取低代码微前端模型({})失败", rootPath.relativize(path), e);
      return null;
    }
  }

  private boolean isDirectModelFile(Path rootPath, Path path) {
    return rootPath.equals(path.getParent());
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class LowcodeMicroWebModel implements MicroWebModel {
    private String name;
    private MicroWebType type = MicroWebType.APP;
    private String entry;
    private String publicPath;
    private String packagePath;
    private AppContext appContext;

    @Override
    public String getName() {
      return name;
    }

    @Override
    public MicroWebType getType() {
      return Optional.ofNullable(type).orElse(MicroWebType.APP);
    }

    @Override
    public String getEntry() {
      return entry;
    }

    @Override
    public String getPublicPath() {
      return publicPath;
    }

    public String getPackagePath() {
      return packagePath;
    }

    @Override
    public InputStream getPackageContent() {
      if (StringUtils.isBlank(packagePath)) {
        return null;
      }
      try {
        var absolutePath = Paths.get(appContext.getHomePath(), MICRO_WEB_DIR, MICRO_WEB_PACKAGE_DIR, packagePath);
        return Files.newInputStream(absolutePath);
      } catch (Exception e) {
        logger.error("获取低代码微前端模型包{}失败", getName(), e);
        return null;
      }
    }
  }

  public static class SingletonHolder implements MicroWebModelProvider {
    private static LowcodeMicroWebModelProvider INSTANCE;

    @Override
    public List<? extends MicroWebModel> getAllMicroWebModel() {
      if (INSTANCE == null) {
        logger.error("低代码微前端模型提供者未初始化");
        return Collections.emptyList();
      }
      return INSTANCE.getAllMicroWebModel();
    }
  }
}
