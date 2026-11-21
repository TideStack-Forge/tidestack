package com.ouroboros.dev.app.initializer;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.ContainerDatasource;

public interface DataSourceInitializer {
  String getTypeName();

  Try<ContainerDatasource> init(String appName, String profileName, String environmentName,
                                AppContainerRequest.DataSourceDefine dataSourceDefine);
}
