package com.ouroboros.dev.app.initializer;

import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.ContainerDatasource;

@Component
public class MemoryDataSourceInitializer extends AbstractSimpleDataSourceInitializer implements DataSourceInitializer {

  ConfigurationService configurationService;

  public MemoryDataSourceInitializer(ConfigurationService configurationService) {
    super("memory");
    this.configurationService = configurationService;
  }

  @Override
  public Try<ContainerDatasource> init(String appName, String profileName, String environmentName,
                                       AppContainerRequest.DataSourceDefine dataSourceDefine) {
    var dataSource = new ContainerDatasource();
    dataSource.setDriverClassName(
        configurationService.getRequired(ConfigurationConstant.TEST_APP_MEMORY_DATASOURCE_DRIVER_CLASS)
    );
    dataSource.setUrl(configurationService.getRequired(ConfigurationConstant.TEST_APP_MEMORY_DATASOURCE_URL));
    return Try.success(dataSource);
  }
}
