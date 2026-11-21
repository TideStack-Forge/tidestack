package com.ouroboros.dev.app.constant;

/**
 * @author liansz
 */
public interface ConfigurationConstant {

  /**
   * 开发助手容器镜像名称
   */
  String DEV_HELPER_CONTAINER_IMAGE = "dev-app-management.dev-helper.container.image";

  /**
   * 测试应用容器镜像名称
   */
  String TEST_APP_CONTAINER_IMAGE = "dev-app-management.test-app.container.image";

  /**
   * 测试应用端口范围
   */
  String TEST_APP_PORT_RANGE = "dev-app-management.test-app.port-range";

  /**
   * 测试应用运行时后端偏好
   */
  String TEST_APP_PREFERRED_RUNTIME_BACKEND = "dev-app-management.test-app.preferred-runtime-backend";

  /**
   * 测试应用默认暴露策略
   */
  String TEST_APP_DEFAULT_EXPOSURE_STRATEGY = "dev-app-management.test-app.default-exposure-strategy";

  /**
   * 测试应用默认数据源驱动
   */
  String TEST_APP_DEFAULT_DATASOURCE_DRIVER_CLASS = "dev-app-management.test-app.default-datasource.driver-class-name";

  /**
   * 测试应用默认数据源URL
   */
  String TEST_APP_DEFAULT_DATASOURCE_URL = "dev-app-management.test-app.default-datasource.url-template";

  /**
   * 测试应用默认数据源用户名
   */
  String TEST_APP_DEFAULT_DATASOURCE_USERNAME = "dev-app-management.test-app.default-datasource.username";

  /**
   * 测试应用默认数据源密码
   */
  String TEST_APP_DEFAULT_DATASOURCE_PASSWORD = "dev-app-management.test-app.default-datasource.password";

  /**
   * 测试应用内存数据源驱动
   */
  String TEST_APP_MEMORY_DATASOURCE_DRIVER_CLASS = "dev-app-management.test-app.memory-datasource.driver-class-name";
  /**
   * 测试应用内存数据源URL
   */
  String TEST_APP_MEMORY_DATASOURCE_URL = "dev-app-management.test-app.memory-datasource.url";

  /**
   * 测试应用日志缓存行数
   */
  String TEST_APP_LOG_CACHE_LINES = "dev-app-management.test-app.log-cache-lines";
}
