package com.ouroboros.data.initialization.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.ouroboros.data.initialization.DataInitializationAsset;
import com.ouroboros.data.initialization.DataInitializationPlan;
import com.ouroboros.data.initialization.InitializationPlanProvider;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.util.Json;

public class ResourceDataInitializationPlanProvider implements InitializationPlanProvider {
  public static final String CLASSPATH_DIRECTORY = ClasspathMetadataSource.METADATA_DIRECTORY;
  public static final String APP_HOME_DIRECTORY = "data-init";
  private static final String METADATA_FILE_SUFFIX = ".data-init.json";

  private final ClasspathMetadataSource classpathMetadataSource;
  private final Path appHomePath;

  public ResourceDataInitializationPlanProvider(ClassLoader classLoader, Path appHomePath) {
    classpathMetadataSource = new ClasspathMetadataSource(Objects.requireNonNull(classLoader, "classLoader"));
    this.appHomePath = appHomePath;
  }

  public ResourceDataInitializationPlanProvider(Path appHomePath) {
    this(Thread.currentThread().getContextClassLoader(), appHomePath);
  }

  @Override
  public List<DataInitializationAsset> loadPlans() {
    List<DataInitializationAsset> assets = new ArrayList<>();
    assets.addAll(loadClasspathPlans());
    loadAppHomePlans().ifPresent(assets::addAll);
    return assets;
  }

  private List<DataInitializationAsset> loadClasspathPlans() {
    return classpathMetadataSource.findTypedResourcesInClasspathOrder(METADATA_FILE_SUFFIX)
        .stream()
        .sorted(Comparator.comparing(MetadataResource::resourceName))
        .map(resource -> asset("classpath", CLASSPATH_DIRECTORY + resource.resourceName(), resource.content()))
        .toList();
  }

  private Optional<List<DataInitializationAsset>> loadAppHomePlans() {
    if (appHomePath == null) {
      return Optional.empty();
    }
    Path rootPath = appHomePath.resolve(APP_HOME_DIRECTORY);
    if (!Files.exists(rootPath)) {
      return Optional.empty();
    }
    try (var stream = Files.walk(rootPath)) {
      return Optional.of(stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(METADATA_FILE_SUFFIX))
          .sorted()
          .map(path -> asset("app-home", rootPath.relativize(path).toString().replace('\\', '/'), read(path)))
          .toList());
    } catch (IOException e) {
      throw new IllegalStateException("扫描应用数据初始化资源失败: " + rootPath, e);
    }
  }

  private String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("读取应用数据初始化资源失败: " + path, e);
    }
  }

  private DataInitializationAsset asset(String sourceKind, String sourceLocation, String content) {
    DataInitializationPlan plan = Json.tryToBean(DataInitializationPlan.class, content)
        .getOrElseThrow(cause -> new IllegalStateException("解析数据初始化资源失败: " + sourceLocation, cause));
    return new DataInitializationAsset(sourceKind, sourceLocation, plan);
  }
}
