package com.ouroboros.dev.app.initializer;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.ContainerDatasource;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;

@Component
public class EnvironmentDataSourceInitializer extends AbstractSimpleDataSourceInitializer implements DataSourceInitializer {

  @Autowired
  private AppDataSourceService appDataSourceService;

  @Autowired
  private PlatformDataSourceService platformDataSourceService;

  public EnvironmentDataSourceInitializer() {
    super("environment");
  }

  @Override
  public Try<ContainerDatasource> init(String appName, String profileName, String environmentName,
                                       AppContainerRequest.DataSourceDefine dataSourceDefine) {
    var dataSourceMapModel = appDataSourceService.getByAppNameAndAppDataSourceName(appName, "default");
    if (!dataSourceMapModel.isPresent()) {
      return Try.failure(new RuntimeException("未找到默认数据源"));
    }

    var platformDataSourceModel = platformDataSourceService.getByName(dataSourceMapModel.get().getPlatformDataSourceName());
    if (!platformDataSourceModel.isPresent()) {
      return Try.failure(new RuntimeException("未找到平台数据源: " + dataSourceMapModel.get().getPlatformDataSourceName()));
    }

    Map<String, Map<String, Object>> definition = platformDataSourceModel.get().getDefinition();
    if (definition == null || !definition.containsKey(environmentName)) {
      return Try.failure(new RuntimeException("数据源未配置环境: " + environmentName));
    }

    Map<String, Object> dataSourceDefinition = definition.get(environmentName);

    ContainerDatasource containerDataSource = new ContainerDatasource();
    containerDataSource.setDriverClassName((String) dataSourceDefinition.get("driverClassName"));
    containerDataSource.setUrl((String) dataSourceDefinition.get("url"));
    containerDataSource.setUsername((String) dataSourceDefinition.get("username"));
    containerDataSource.setPassword((String) dataSourceDefinition.get("password"));
    return Try.success(containerDataSource);
  }
}
