package com.ouroboros.menu.provider;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelProvider;
import com.ouroboros.menu.model.JarMenuModel;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
public class JarMenuModelProvider implements MenuModelProvider {
  private static final Logger logger = LoggerFactory.getLogger(JarMenuModelProvider.class);

  private static final String LEGACY_MENU_DIRECTORY = "META-INF/ouroboros/menu-model/";
  private static final String MENU_SUFFIX = ".menu.json";
  private static final Pattern JSON_FILE_PATTERN = Pattern.compile(".*\\.json");

  private Map<String, MenuModel> menuModelMap;

  private final Supplier<List<MetadataResource>> resources;

  private static final Supplier<List<MetadataResource>> CLASSPATH_RESOURCES = cacheResources(new ClasspathMetadataSource());

  public JarMenuModelProvider() {
    this(CLASSPATH_RESOURCES);
  }

  JarMenuModelProvider(MetadataSource source) {
    this(cacheResources(source));
  }

  private static Supplier<List<MetadataResource>> cacheResources(MetadataSource source) {
    return new Supplier<>() {
      private List<MetadataResource> snapshot;

      @Override
      public synchronized List<MetadataResource> get() {
        if (snapshot != null) {
          return snapshot;
        }
        var loaded = List.copyOf(source.findResources(metadataSpec()));
        var result = new TypedMetadataCatalog(ignored -> loaded).load(metadataSpec());
        if (result.diagnostics().isEmpty()) {
          snapshot = loaded;
        }
        return loaded;
      }
    };
  }

  private JarMenuModelProvider(Supplier<List<MetadataResource>> resources) {
    this.resources = resources;
    loadDefinitions();
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("菜单元数据文件 {} 加载失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("菜单元数据文件 {} 加载跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }

  private synchronized Map<String, MenuModel> loadDefinitions() {
    if (menuModelMap != null) {
      return menuModelMap;
    }
    try {
      var spec = metadataSpec();
      var result = new TypedMetadataCatalog(ignored -> resources.get()).load(spec);
      result.diagnostics().forEach(JarMenuModelProvider::logDiagnostic);
      Map<String, MenuModel> menus = result.values().values().stream()
          .collect(() -> new TreeMap<String, MenuModel>(String::compareToIgnoreCase),
              (map, model) -> map.put(model.getFullName(), model), Map::putAll);
      if (result.diagnostics().isEmpty()) {
        menuModelMap = menus;
      }
      return menus;
    } catch (Exception e) {
      logger.error("加载 JAR 元数据失败，跳过本次加载: JarMenuModelProvider", e);
      return Map.of();
    }
  }

  private static TypedMetadataSpec<MenuModel> metadataSpec() {
    return TypedMetadataSpec.<MenuModel>builder(MenuModel.class)
        .typedSuffix(MENU_SUFFIX)
        .sourcePlan(TypedMetadataSpec.SourcePlan.LEGACY_DIRECTORY_THEN_TYPED_SUFFIX)
        .legacyDirectory(LEGACY_MENU_DIRECTORY)
        .legacyFilePattern(JSON_FILE_PATTERN)
        .resourceParser(resource -> Json.tryToBean(JarMenuModel.class, resource.content())
            .filter(Objects::nonNull))
        .keyExtractor(MenuModel::getFullName)
        .build();
  }

  @Override
  public Map<String, MenuModel> getAllMenus() {
    return loadDefinitions();
  }
}
