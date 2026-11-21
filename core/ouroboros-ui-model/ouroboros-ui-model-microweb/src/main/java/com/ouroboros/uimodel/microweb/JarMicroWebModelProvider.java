package com.ouroboros.uimodel.microweb;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.util.Json;
import com.ouroboros.util.ResourceUtil;

public class JarMicroWebModelProvider implements MicroWebModelProvider {
  private final static Logger logger = LoggerFactory.getLogger(JarMicroWebModelProvider.class);

  /**
   * 微前端元数据路径
   */
  private final static String MICRO_WEB_METADATA_PATH = "META-INF/ouroboros/micro-web/";
  private final static String MICRO_WEB_METADATA_MIRROR_PATH = ClasspathMetadataSource.METADATA_DIRECTORY + "micro-web/";

  /**
   * 微前端包路径
   */
  private final static String MICRO_WEB_PACKAGE_PATH = MICRO_WEB_METADATA_PATH + "package/";
  private final static String MICRO_WEB_PACKAGE_MIRROR_PATH = MICRO_WEB_METADATA_MIRROR_PATH + "package/";

  /**
   * 微前端元数据文件正则
   */
  private final static String MODEL_SUFFIX = ".micro-web.json";
  private final static String LEGACY_MODEL_SUFFIX = ".json";
  private final static Pattern LEGACY_MODEL_FILE_PATTERN = Pattern.compile(".*" + Pattern.quote(LEGACY_MODEL_SUFFIX));

  private static final Supplier<List<MetadataResource>> CLASSPATH_RESOURCES = cacheResources(new ClasspathMetadataSource());
  private final Supplier<List<MetadataResource>> resources;

  public JarMicroWebModelProvider() {
    resources = CLASSPATH_RESOURCES;
  }

  JarMicroWebModelProvider(ClasspathMetadataSource metadataSource) {
    resources = cacheResources(Objects.requireNonNull(metadataSource, "metadataSource"));
  }

  private static Supplier<List<MetadataResource>> cacheResources(ClasspathMetadataSource metadataSource) {
    return new Supplier<>() {
      private List<MetadataResource> snapshot;

      @Override
      public synchronized List<MetadataResource> get() {
        if (snapshot != null) {
          return snapshot;
        }
        var loaded = new ArrayList<MetadataResource>();
        boolean complete = true;
        for (var path : List.of(MICRO_WEB_METADATA_PATH, MICRO_WEB_METADATA_MIRROR_PATH)) {
          try {
            loaded.addAll(metadataSource.findResourcesForOverride(path, LEGACY_MODEL_FILE_PATTERN));
          } catch (Exception e) {
            logger.error("读取微前端元数据目录 {} 失败，已跳过", path, e);
            complete = false;
          }
        }
        if (complete && loaded.stream()
            .filter(resource -> isLegacyModelResource(resource.resourceName()) || isTypedModelResource(resource.resourceName()))
            .allMatch(resource -> tryParseModel(resource).isSuccess())) {
          snapshot = List.copyOf(loaded);
        }
        return loaded;
      }
    };
  }

  @Override
  public List<? extends MicroWebModel> getAllMicroWebModel() {
    return parseModels(resources.get());
  }

  private static List<JarMicroWebModel> parseModels(List<MetadataResource> resources) {
    Map<String, JarMicroWebModel> models = new TreeMap<>(String::compareToIgnoreCase);
    loadModels(resources, models, JarMicroWebModelProvider::isLegacyModelResource);
    loadModels(resources, models, JarMicroWebModelProvider::isTypedModelResource);
    return new ArrayList<>(models.values());
  }

  private static void loadModels(List<MetadataResource> resources, Map<String, JarMicroWebModel> models,
      Predicate<String> resourceFilter) {
    resources.stream()
        .filter(resource -> resourceFilter.test(resource.resourceName()))
        .flatMap(resource -> tryParseModel(resource)
            .onFailure(e -> logger.warn("解析微前端元数据 {} 失败，已跳过", resource.resourceName(), e))
            .toJavaStream())
        .forEach(model -> models.put(model.getName(), model));
  }

  private static Try<JarMicroWebModel> tryParseModel(MetadataResource resource) {
    return Json.tryToBean(JarMicroWebModel.class, resource.content())
        .filter(model -> model != null && StringUtils.isNotBlank(model.getName()));
  }

  private static boolean isTypedModelResource(String resourceName) {
    return !resourceName.contains("/")
        && resourceName.endsWith(MODEL_SUFFIX);
  }

  private static boolean isLegacyModelResource(String resourceName) {
    return !resourceName.contains("/")
        && resourceName.endsWith(LEGACY_MODEL_SUFFIX)
        && !resourceName.endsWith(MODEL_SUFFIX);
  }

  public static class JarMicroWebModel implements MicroWebModel {
    private String name;

    /**
     * 入口
     */
    private String entry;

    /**
     * 打包公共路径
     */
    private String publicPath;

    private MicroWebType type = MicroWebType.APP;

    /**
     * 包名称
     */
    private String packageName;

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

    public void setEntry(String entry) {
      this.entry = entry;
    }

    @Override
    public String getPublicPath() {
      return publicPath;
    }

    @Override
    public InputStream getPackageContent() {
      List<URL> resourceUrlList = getPackageResourceUrls(packageName);
      if (resourceUrlList.size() == 0) {
        logger.error("微前端 {} 描述的资源 {} 未找到！", publicPath, packageName);
        return null;
      }

      if (resourceUrlList.size() > 1) {
        logger.warn("微前端 {} 描述的资源 {} 存在多个，请保证唯一！", publicPath, packageName);
      }

      try {
        return resourceUrlList.get(0).openStream();
      } catch (IOException e) {
        logger.error("微前端 {} 描述的资源 {} 读取失败！", publicPath, packageName, e);
        return null;
      }
    }

    private List<URL> getPackageResourceUrls(String packageName) {
      List<URL> resourceUrlList = new ArrayList<>();
      resourceUrlList.addAll(ResourceUtil.getResourceUrlListForOverride(Paths.get(MICRO_WEB_PACKAGE_MIRROR_PATH, packageName).toString()));
      resourceUrlList.addAll(ResourceUtil.getResourceUrlListForOverride(Paths.get(MICRO_WEB_PACKAGE_PATH, packageName).toString()));
      return resourceUrlList;
    }

    public void setPublicPath(String publicPath) {
      this.publicPath = publicPath;
    }

    public String getPackageName() {
      return packageName;
    }

    public void setPackageName(String packageName) {
      this.packageName = packageName;
    }
  }
}
