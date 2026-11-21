package com.ouroboros.dev.app.service.runtime;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.env.StandardEnvironment;

import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.config.DevContainerConfig;
import com.ouroboros.dev.app.initializer.DataSourceInitializer;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.AppInfoWithMeta;
import com.ouroboros.dev.app.model.ContainerDatasource;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.model.AppInfo;

final class RuntimeFactoryTestSupport {
  private RuntimeFactoryTestSupport() {
  }

  static AppInfo appInfo(String appName, String title) {
    return new AppInfo()
        .setName(appName)
        .setTitle(title)
        .setDescription(title + " description");
  }

  static AppContainerRequest.DataSourceDefine datasource(String type) {
    AppContainerRequest.DataSourceDefine datasource = new AppContainerRequest.DataSourceDefine();
    datasource.setType(type);
    return datasource;
  }

  static Map<String, String> mqConfig() {
    Map<String, String> values = new LinkedHashMap<String, String>();
    values.put("SPRING_RABBITMQ_HOST", "mq.example.internal");
    values.put("SPRING_RABBITMQ_PORT", "5672");
    values.put("SPRING_RABBITMQ_USERNAME", "dev");
    values.put("SPRING_RABBITMQ_PASSWORD", "secret");
    return values;
  }

  static FakeDevContainerConfig devContainerConfig() {
    return new FakeDevContainerConfig(
        "/workspace/run",
        "/workspace/dev",
        mqConfig(),
        "org.h2.Driver",
        "jdbc:h2:mem:helper"
    );
  }

  static AppManagementService appManagementService(AppInfo appInfo) {
    return new AppManagementService() {
      @Override
      public Try<Void> createApp(AppInfo app) {
        return unsupported();
      }

      @Override
      public Try<Void> updateApp(String appName, AppInfo app) {
        return unsupported();
      }

      @Override
      public Try<Void> deleteApp(String appName) {
        return unsupported();
      }

      @Override
      public Try<Void> renameApp(String appName, String newName) {
        return unsupported();
      }

      @Override
      public Try<List<AppInfoWithMeta>> listApps(String category) {
        return Try.success(Collections.<AppInfoWithMeta>emptyList());
      }

      @Override
      public Try<List<AppInfoWithMeta>> listApps() {
        return Try.success(Collections.<AppInfoWithMeta>emptyList());
      }

      @Override
      public boolean appExists(String appName) {
        return appInfo != null && java.util.Objects.equals(appInfo.getName(), appName);
      }

      @Override
      public Try<AppInfo> getAppInfo(String appName) {
        return Try.success(appInfo);
      }

      @Override
      public Try<Void> importApp(InputStream zipInputStream) {
        return unsupported();
      }

      @Override
      public Try<InputStream> exportApp(String appName, boolean includeUncommitted, boolean includeGitRepo) {
        return unsupported();
      }
    };
  }

  static ConfigurationService configurationService(Map<String, Object> values) {
    return new ConfigurationService() {
      @Override
      public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) values.get(key));
      }

      @Override
      public Map<String, Object> getValues(ConfigScope scope) {
        return Collections.emptyMap();
      }
    };
  }

  static DataSourceInitializer dataSourceInitializer(String type, ContainerDatasource datasource) {
    return new DataSourceInitializer() {
      @Override
      public String getTypeName() {
        return type;
      }

      @Override
      public Try<ContainerDatasource> init(String appName, String profileName, String environmentName,
                                           AppContainerRequest.DataSourceDefine dataSourceDefine) {
        return Try.success(new ContainerDatasource()
            .setDriverClassName(datasource.getDriverClassName())
            .setUrl(datasource.getUrl())
            .setUsername(datasource.getUsername())
            .setPassword(datasource.getPassword()));
      }
    };
  }

  static ContainerDatasource datasourceValue() {
    return new ContainerDatasource()
        .setDriverClassName("org.h2.Driver")
        .setUrl("jdbc:h2:mem:crm")
        .setUsername("sa")
        .setPassword("pw");
  }

  static DevelopWorkspaceContext workspaceContext() {
    return workspaceContext(Paths.get("/workspace"));
  }

  static DevelopWorkspaceContext workspaceContext(Path rootPath) {
    final Path resolvedRoot = rootPath == null ? Paths.get("/workspace") : rootPath;
    final Path devRoot = resolvedRoot.resolve("dev");
    final Path runRoot = resolvedRoot.resolve("run");
    final Path debugSupport = resolvedRoot.resolve("debug-support");
    final Path developSupport = resolvedRoot.resolve("develop-support");
    return new DevelopWorkspaceContext() {
      @Override
      public String getRootDir() {
        return resolvedRoot.toString();
      }

      @Override
      public String getDevRootDir() {
        return devRoot.toString();
      }

      @Override
      public String getRunRootDir() {
        return runRoot.toString();
      }

      @Override
      public String getDebugSupportFilesDir() {
        return debugSupport.toString();
      }

      @Override
      public String getDevelopSupportFilesDir() {
        return developSupport.toString();
      }
    };
  }

  static class FakeDevContainerConfig extends DevContainerConfig {
    private final String networkName;
    private final String hostRunPath;
    private final String hostDevPath;
    private final Map<String, String> mqConfig;
    private final String memoryDatasourceDriverClass;
    private final String memoryDatasourceUrl;
    private String mothershipImageName = "ouroboros-mothership";

    FakeDevContainerConfig(String hostRunPath,
                           String hostDevPath,
                           Map<String, String> mqConfig,
                           String memoryDatasourceDriverClass,
                           String memoryDatasourceUrl) {
      super(configurationService(Collections.emptyMap()), null, workspaceContext(), new StandardEnvironment());
      this.networkName = "ouroboros-dev";
      this.hostRunPath = hostRunPath;
      this.hostDevPath = hostDevPath;
      this.mqConfig = new LinkedHashMap<String, String>(mqConfig);
      this.memoryDatasourceDriverClass = memoryDatasourceDriverClass;
      this.memoryDatasourceUrl = memoryDatasourceUrl;
    }

    @Override
    public String getHostRunPath() {
      return hostRunPath;
    }

    @Override
    public String getHostDevPath() {
      return hostDevPath;
    }

    @Override
    public Map<String, String> getMQConfig() {
      return Collections.unmodifiableMap(mqConfig);
    }

    @Override
    public String getNetworkName() {
      return networkName;
    }

    @Override
    public String getTestAppMemoryDatasourceDriverClass() {
      return memoryDatasourceDriverClass;
    }

    @Override
    public String getTestAppMemoryDatasourceUrl() {
      return memoryDatasourceUrl;
    }

    @Override
    public String getMothershipImageName() {
      return mothershipImageName;
    }

    void setMothershipImageName(String mothershipImageName) {
      this.mothershipImageName = mothershipImageName;
    }
  }

  private static <T> Try<T> unsupported() {
    return Try.failure(new UnsupportedOperationException("not used in test"));
  }
}
