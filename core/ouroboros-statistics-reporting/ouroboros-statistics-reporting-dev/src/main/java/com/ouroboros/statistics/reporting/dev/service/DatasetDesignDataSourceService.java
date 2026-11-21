package com.ouroboros.statistics.reporting.dev.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MetadataOnlyDataModel;
import com.ouroboros.dev.datasource.model.DataSourceDefine;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.dev.datasource.service.DataSourceMetadataService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshotProvider;
import com.ouroboros.dev.metadata.MetadataProvider;
import com.ouroboros.dev.metadata.MetadataProviderMixer;
import com.ouroboros.dev.metadata.MetadataSnapshotStatus;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.statistics.reporting.dev.model.DatasetDataSourceRequest;
import com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection;
import com.ouroboros.util.Json;
import com.ouroboros.web.SelectOption;

/** Resolves logical sources and actual test connections within the application being edited. */
@Service
public class DatasetDesignDataSourceService {
  private final AppDataSourceService appDataSources;
  private final PlatformDataSourceService platformDataSources;
  private final List<MetadataProvider> metadataProviders;
  private final List<DataSourceMetadataService> metadataServices;
  private final ObjectProvider<RuntimeStateStore> runtimeStores;
  private final ObjectProvider<DevRuntimeService> runtimeServices;

  public DatasetDesignDataSourceService(AppDataSourceService appDataSources,
                                        PlatformDataSourceService platformDataSources,
                                        List<MetadataProvider> metadataProviders,
                                        List<DataSourceMetadataService> metadataServices,
                                        ObjectProvider<RuntimeStateStore> runtimeStores,
                                        ObjectProvider<DevRuntimeService> runtimeServices) {
    this.appDataSources = appDataSources;
    this.platformDataSources = platformDataSources;
    this.metadataProviders = List.copyOf(metadataProviders);
    this.metadataServices = List.copyOf(metadataServices);
    this.runtimeStores = runtimeStores;
    this.runtimeServices = runtimeServices;
  }

  public List<SelectOption> options(String appName) {
    Map<String, SelectOption> options = new LinkedHashMap<>();
    options.put("default", new SelectOption("default", "默认数据源"));
    appDataSources.getByAppName(appName).forEach(source -> options.putIfAbsent(
        source.getDataStationName(), new SelectOption(source.getDataStationName(), source.getDataStationName())));
    return List.copyOf(options.values());
  }

  public List<DataModel> models(String appName, String datasourceName) {
    String name = sourceName(datasourceName);
    return modelMetadata(appName, true).stream()
        .filter(model -> name.equals(sourceName(model.getDataStation())))
        .map(MetadataOnlyDataModel::new)
        .map(DataModel.class::cast)
        .toList();
  }

  private List<DataModelMeta> modelMetadata(String appName, boolean requireCurrent) {
    List<MetadataProvider> providers = new ArrayList<>();
    for (MetadataProvider provider : metadataProviders) {
      if (provider instanceof ApplicationMetadataSnapshotProvider snapshots) {
        var snapshot = snapshots.getApplicationMetadataSnapshot(appName);
        if (snapshot.getStatus() != MetadataSnapshotStatus.CURRENT) {
          if (requireCurrent) {
            throw new BusinessException("metadata-unavailable: 请先刷新应用开发助手的数据模型元数据");
          }
          continue;
        }
        providers.add((app, module) -> snapshot.getModule(module));
      } else {
        providers.add(provider);
      }
    }
    return MetadataProviderMixer.getDistinctMixer(providers).getAppAllMetadataByModule(appName, "data-model")
        .stream().map(metadata -> Json.toBean(DataModelMeta.class, Json.toJsonString(metadata.getDetails()))).toList();
  }

  public Optional<DataSourceDefine> connection(String appName, DatasetDataSourceRequest request) {
    String datasourceName = sourceName(request.getDatasource());
    if (!"default".equals(datasourceName)) {
      var source = appDataSources.getByAppNameAndAppDataSourceName(appName, datasourceName)
          .orElseThrow(() -> new BusinessException("datasource-not-found: 应用数据源未绑定实际连接"));
      return platformConnection(source.getPlatformDataSourceName(), request.getEnvName(), true);
    }
    DatasetTestConnection selected = request.getTestConnection();
    String kind = selected == null || isBlank(selected.getKind()) ? "application" : selected.getKind();
    return switch (kind) {
      case "platform" -> Optional.of(platformConnection(selected.getPlatformDataSourceName(), selected.getEnvName(), true)
          .orElseThrow(() -> new BusinessException("datasource-not-found: 请选择连接环境")));
      case "runtime" -> Optional.of(runtimeConnection(appName, selected.getRuntimeId()));
      case "application" -> appDataSources.getByAppNameAndAppDataSourceName(appName, sourceName(request.getDatasource()))
          .flatMap(source -> platformConnection(source.getPlatformDataSourceName(), request.getEnvName(), false));
      default -> throw new BusinessException("datasource-invalid: 不支持的测试连接方式");
    };
  }

  private Optional<DataSourceDefine> platformConnection(String name, String envName, boolean required) {
    if (isBlank(name)) {
      throw new BusinessException("datasource-not-found: 请选择实际连接");
    }
    var source = platformDataSources.getByName(name)
        .orElseThrow(() -> new BusinessException("datasource-not-found: 平台数据源不存在"));
    var definitions = source.getDefinition();
    String environment = !isBlank(envName) ? envName
        : definitions != null && definitions.containsKey("default") ? "default" : "dev";
    Map<String, Object> definition = definitions == null ? null : definitions.get(environment);
    if (definition == null || isBlank(String.valueOf(definition.getOrDefault("url", "")))) {
      if (required) {
        throw new BusinessException("datasource-not-found: 连接环境未配置 JDBC URL");
      }
      return Optional.empty();
    }
    return Optional.of(new DataSourceDefine(definition));
  }

  public List<SelectOption> platformOptions() {
    return platformDataSources.getAll().stream()
        .map(source -> new SelectOption(source.getName(), isBlank(source.getTitle()) ? source.getName() : source.getTitle()))
        .toList();
  }

  public List<SelectOption> environmentOptions(String name) {
    return platformDataSources.getByName(name).map(source -> source.getDefinition() == null ? List.<SelectOption>of()
        : source.getDefinition().keySet().stream().sorted().map(env -> new SelectOption(env, env)).toList())
        .orElse(List.of());
  }

  public List<SelectOption> runtimeOptions(String appName) {
    RuntimeStateStore store = runtimeStores.getIfAvailable();
    DevRuntimeService runtimeService = runtimeServices.getIfAvailable();
    if (store == null || runtimeService == null) {
      return List.of();
    }
    runtimeService.recover(appName, RuntimeKind.TEST_APP)
        .getOrElseThrow(error -> new BusinessException("runtime-unavailable: 无法发现应用运行容器", error));
    return store.allInstances().entrySet().stream()
        .filter(entry -> appName.equals(entry.getKey().getAppName()))
        .filter(entry -> entry.getKey().getRuntimeKind() == RuntimeKind.TEST_APP)
        .filter(entry -> runtimeService.getStatus(entry.getKey())
            .getOrElseThrow(error -> new BusinessException("runtime-unavailable: 无法获取应用容器状态", error))
            .getStatus() == RuntimeStatus.RUNNING)
        .filter(entry -> store.findSpec(entry.getKey()).map(this::hasAccessibleJdbcConnection).orElse(false))
        .map(entry -> new SelectOption(entry.getValue().getRuntimeId(),
            entry.getKey().getProfile() + " · " + entry.getKey().getBackendType()))
        .toList();
  }

  private DataSourceDefine runtimeConnection(String appName, String runtimeId) {
    RuntimeStateStore store = runtimeStores.getIfAvailable();
    if (store == null || isBlank(runtimeId)) {
      throw new BusinessException("datasource-not-found: 请选择运行容器");
    }
    DevRuntimeService runtimeService = runtimeServices.getIfAvailable();
    if (runtimeService == null) {
      throw new BusinessException("runtime-unavailable: 运行时服务未配置");
    }
    if (store.findKeyByRuntimeId(runtimeId).isEmpty()) {
      runtimeService.recover(appName, RuntimeKind.TEST_APP)
          .getOrElseThrow(error -> new BusinessException("runtime-unavailable: 无法发现应用运行容器", error));
    }
    var key = store.findKeyByRuntimeId(runtimeId)
        .filter(identity -> appName.equals(identity.getAppName()) && identity.getRuntimeKind() == RuntimeKind.TEST_APP)
        .orElseThrow(() -> new BusinessException("datasource-not-found: 当前应用的运行容器不存在"));
    var instance = runtimeService.getStatus(key)
        .getOrElseThrow(error -> new BusinessException("runtime-unavailable: 无法获取应用容器状态", error));
    if (instance.getStatus() != RuntimeStatus.RUNNING) {
      throw new BusinessException("datasource-unreachable: 容器未运行，请启动或重新选择");
    }
    RuntimeSpec spec = store.findSpec(key).filter(this::hasAccessibleJdbcConnection)
        .orElseThrow(() -> new BusinessException("datasource-unreachable: 容器没有可供连接的 JDBC 数据库"));
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("type", "jdbc");
    definition.put("driverClassName", spec.getEnv().get("SPRING_DATASOURCE_DRIVERCLASSNAME"));
    definition.put("url", spec.getEnv().get("SPRING_DATASOURCE_URL"));
    definition.put("username", spec.getEnv().get("SPRING_DATASOURCE_USERNAME"));
    definition.put("password", spec.getEnv().get("SPRING_DATASOURCE_PASSWORD"));
    return new DataSourceDefine(definition);
  }

  private boolean hasAccessibleJdbcConnection(RuntimeSpec spec) {
    String url = spec.getEnv().get("SPRING_DATASOURCE_URL");
    return !isBlank(url) && url.startsWith("jdbc:") && !url.startsWith("jdbc:h2:mem:");
  }

  public TableCatalog tables(String appName, DatasetDataSourceRequest request) {
    Optional<DataSourceDefine> connection = connection(appName, request);
    Map<String, Table> tables = new TreeMap<>(String::compareToIgnoreCase);
    List<String> warnings = new ArrayList<>();
    List<DataModelMeta> models = modelMetadata(appName, connection.isEmpty());
    for (DataModelMeta meta : models) {
      if (!sourceName(request.getDatasource()).equals(sourceName(meta.getDataStation()))) {
        continue;
      }
      DataModel model = new MetadataOnlyDataModel(meta);
      var fields = model.getFields().stream()
          .map(field -> new Column(field.getRawName(), field.getLabel(), field.getType())).toList();
      tables.put(model.getRawName(), new Table(model.getRawName(), model.getLabel(), model.getFullName(), "model", fields));
    }
    if (connection.isPresent()) {
      DataSourceDefine definition = connection.get();
      var service = metadataServices.stream().filter(candidate -> Boolean.TRUE.equals(candidate.isSupported(definition)))
          .findFirst().orElseThrow(() -> new BusinessException("datasource-unreachable: 不支持的连接类型"));
      try (var metadata = service.getMetadata(definition)) {
        for (var table : metadata.getAllTables()) {
          if (!tables.containsKey(table.getName())) {
            var fields = table.getColumns().stream()
                .map(column -> new Column(column.getName(), column.getRemarks(), column.getTypeName())).toList();
            tables.put(table.getName(), new Table(table.getName(), table.getRemarks(), null, "database", fields));
          }
        }
      } catch (Exception error) {
        throw new BusinessException("datasource-unreachable: 无法读取数据库表结构", error);
      }
    } else {
      warnings.add("当前使用数据模型元数据；选择测试连接后可查看数据库中的其他表。");
    }
    return new TableCatalog(List.copyOf(tables.values()), warnings);
  }

  public static String sourceName(String name) {
    return isBlank(name) ? "default" : name;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  public record Column(String name, String title, String dataType) { }
  public record Table(String name, String title, String model, String source, List<Column> columns) { }
  public record TableCatalog(List<Table> items, List<String> warnings) { }
}
