package com.ouroboros.ability.componentlib;

import java.io.File;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.WebAppInfoContributor;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshingEvent;
import com.ouroboros.util.Json;

/**
 * 平台组件库 WebAppInfo 贡献者
 * 读取应用的 platform-component-lib.json 配置，拼接成微前端entry并注入到web-app-info
 */
@Component
public class PlatformComponentLibWebAppInfoContributor {
  private static final Logger logger = LoggerFactory.getLogger(PlatformComponentLibWebAppInfoContributor.class);
  private static final String COMPONENT_LIB_FILENAME = "platform-component-lib.json";

  private final AppContext appContext;

  @Value("${ouroboros.platformComponentLib.baseUrl:}")
  private String microWebBaseUrl;

  private Map<String, Object> webAppInfo;

  public PlatformComponentLibWebAppInfoContributor(AppContext appContext) {
    this.appContext = appContext;
    SingletonHolder.INSTANCE = this;
  }

  @EventListener(OuroborosContextRefreshingEvent.class)
  public void init() {
    webAppInfo = new HashMap<>();

    // 读取应用的组件库配置
    Try.of(() -> {
          String appDir = appContext.getHomePath();
          File configFile = new File(appDir, COMPONENT_LIB_FILENAME);

          if (!configFile.exists()) {
            logger.debug("应用未配置平台组件库");
            return Collections.<ComponentLibEntry>emptyList();
          }

          // 读取配置文件
          ComponentLibConfig config = Json.toBean(ComponentLibConfig.class, new java.io.FileInputStream(configFile));
          if (config == null) {
            logger.warn("平台组件库配置文件解析失败");
            return Collections.<ComponentLibEntry>emptyList();
          }

          // 构建组件库entry列表
          List<ComponentLibEntry> entries = new ArrayList<>();
          for (ImportedComponentLib imported : config.getImports()) {
            String entry = buildEntry(imported.getPublicPath());
            entries.add(new ComponentLibEntry(
                imported.getName(),
                imported.getVersion(),
                imported.getPublicPath(),
                entry
            ));
          }

          logger.info("加载了 {} 个平台组件库", entries.size());
          return entries;
        })
        .onSuccess(entries -> {
          webAppInfo.put("platformComponentLibraries", entries);
        })
        .onFailure(e -> {
          logger.error("加载平台组件库配置失败", e);
          webAppInfo.put("platformComponentLibraries", Collections.emptyList());
        });
  }

  private String buildEntry(String publicPath) {
    if (microWebBaseUrl == null || microWebBaseUrl.trim().isEmpty()) {
      return publicPath;
    }

    String baseUrl = microWebBaseUrl.endsWith("/")
        ? microWebBaseUrl.substring(0, microWebBaseUrl.length() - 1)
        : microWebBaseUrl;

    String path = publicPath.startsWith("/") ? publicPath : "/" + publicPath;
    return baseUrl + path;
  }

  public Map<String, Object> getWebAppInfo() {
    return Optional.ofNullable(webAppInfo)
        .orElseGet(Collections::emptyMap);
  }

  // ========== 内部类 ==========

  /**
   * 组件库配置
   */
  public static class ComponentLibConfig {
    private List<ImportedComponentLib> imports;

    public List<ImportedComponentLib> getImports() {
      return imports == null ? Collections.emptyList() : imports;
    }

    public void setImports(List<ImportedComponentLib> imports) {
      this.imports = imports;
    }
  }

  /**
   * 导入的组件库
   */
  public static class ImportedComponentLib {
    private String name;
    private String publicPath;
    private String version;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getPublicPath() {
      return publicPath;
    }

    public void setPublicPath(String publicPath) {
      this.publicPath = publicPath;
    }

    public String getVersion() {
      return version;
    }

    public void setVersion(String version) {
      this.version = version;
    }
  }

  /**
   * 组件库Entry信息
   */
  public static final class ComponentLibEntry {
    private final String name;
    private final String version;
    private final String publicPath;
    private final String entry;

    /**
     *
     */
    public ComponentLibEntry(String name, String version, String publicPath, String entry) {
      this.name = name;
      this.version = version;
      this.publicPath = publicPath;
      this.entry = entry;
    }

    public String name() {
      return name;
    }

    public String version() {
      return version;
    }

    public String publicPath() {
      return publicPath;
    }

    public String entry() {
      return entry;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj == null || obj.getClass() != this.getClass()) {
        return false;
      }
      var that = (ComponentLibEntry) obj;
      return Objects.equals(this.name, that.name) &&
          Objects.equals(this.version, that.version) &&
          Objects.equals(this.publicPath, that.publicPath) &&
          Objects.equals(this.entry, that.entry);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, version, publicPath, entry);
    }

    @Override
    public String toString() {
      return "ComponentLibEntry[" +
          "name=" + name + ", " +
          "version=" + version + ", " +
          "publicPath=" + publicPath + ", " +
          "entry=" + entry + ']';
    }

  }

  /**
   * WebAppInfoContributor 实现
   */
  public static class SingletonHolder implements WebAppInfoContributor {
    public static PlatformComponentLibWebAppInfoContributor INSTANCE;

    @Override
    public Map<String, Object> getWebAppInfo() {
      return Optional.ofNullable(INSTANCE)
          .map(PlatformComponentLibWebAppInfoContributor::getWebAppInfo)
          .orElseGet(Collections::emptyMap);
    }
  }
}
