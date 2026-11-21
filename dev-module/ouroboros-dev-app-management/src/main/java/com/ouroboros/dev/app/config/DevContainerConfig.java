package com.ouroboros.dev.app.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.container.model.Container;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.constant.PropertyConstant;

@Component
public class DevContainerConfig {
  private final static String DEFAULT_NETWORK_NAME = "ouroboros-dev";

  private final static String CONTAINER_NETWORK_NAME_KEY = "ouroboros-dev.container.default-network-name";
  private final static String CONTAINER_RABBITMQ_HOST_KEY = "ouroboros-dev.container.rabbitmq.host";
  private final static String CONTAINER_RABBITMQ_HOSTS_KEY = "ouroboros-dev.container.rabbitmq.hosts";
  private final static String CONTAINER_RABBITMQ_PORT_KEY = "ouroboros-dev.container.rabbitmq.port";
  private final static String CONTAINER_RABBITMQ_ADDRESSES_KEY = "ouroboros-dev.container.rabbitmq.addresses";
  private final static String CONTAINER_RABBITMQ_VIRTUAL_HOST_KEY = "ouroboros-dev.container.rabbitmq.virtual-host";
  private final static String CONTAINER_RABBITMQ_USERNAME_KEY = "ouroboros-dev.container.rabbitmq.username";
  private final static String CONTAINER_RABBITMQ_PASSWORD_KEY = "ouroboros-dev.container.rabbitmq.password";

  private final static String RABBITMQ_HOST_ENV_KEY = "SPRING_RABBITMQ_HOST";
  private final static String RABBITMQ_PORT_ENV_KEY = "SPRING_RABBITMQ_PORT";
  private final static String RABBITMQ_HOSTS_ENV_KEY = "SPRING_RABBITMQ_HOSTS";
  private final static String RABBITMQ_ADDRESSES_ENV_KEY = "SPRING_RABBITMQ_ADDRESSES";
  private final static String RABBITMQ_VIRTUAL_HOST_ENV_KEY = "SPRING_RABBITMQ_VIRTUAL_HOST";
  private final static String RABBITMQ_USERNAME_ENV_KEY = "SPRING_RABBITMQ_USERNAME";
  private final static String RABBITMQ_PASSWORD_ENV_KEY = "SPRING_RABBITMQ_PASSWORD";

  private final ConfigurationService configurationService;
  private final ContainerService containerService;
  private final DevelopWorkspaceContext developWorkspaceContext;
  private final Environment environment;

  public DevContainerConfig(ConfigurationService configurationService, ContainerService containerService, DevelopWorkspaceContext developWorkspaceContext, Environment environment) {
    this.configurationService = configurationService;
    this.containerService = containerService;
    this.developWorkspaceContext = developWorkspaceContext;
    this.environment = environment;
  }

  public String getMothershipImageName() {
    return configurationService.getRequired(ConfigurationConstant.DEV_HELPER_CONTAINER_IMAGE);
  }

  public String getHostRunPath() {
    return Optional.ofNullable(PropertyConstant.HOST_RUN_WORKSPACE_PATH)
        .orElseGet(developWorkspaceContext::getRunRootDir);
  }

  public String getHostDevPath() {
    return Optional.ofNullable(PropertyConstant.HOST_DEV_WORKSPACE_PATH)
        .orElseGet(developWorkspaceContext::getDevRootDir);
  }

  public Map<String, String> getMQConfig() {
    var mqConfig = getMQConfigFromDevConfig();
    if (!mqConfig.isEmpty()) {
      return mqConfig;
    }
    return getMQConfigFromEnv();
  }

  // TODO: 考虑使用统一配置
  public String getNetworkName() {
    String networkName = getNetworkNameFromEnv();
    if (!StringUtils.isEmpty(networkName)) {
      return networkName;
    }
    String hostname = getHostName();
    if (hostname.isEmpty()) {
      return DEFAULT_NETWORK_NAME;
    }
    return containerService.getContainerInfoByName(hostname)
        .fold(e -> DEFAULT_NETWORK_NAME, Container::getNetwork);
  }

  public Map<String, String> getMQConfigFromDevConfig() {
    Map<String, String> mqConfig = new LinkedHashMap<>();
    var addresses = environment.getProperty(CONTAINER_RABBITMQ_ADDRESSES_KEY);
    var host = environment.getProperty(CONTAINER_RABBITMQ_HOST_KEY);
    var hosts = environment.getProperty(CONTAINER_RABBITMQ_HOSTS_KEY);
    if (StringUtils.isAllBlank(addresses, host, hosts)) {
      return mqConfig;
    }

    var port = environment.getProperty(CONTAINER_RABBITMQ_PORT_KEY);
    var virtualHost = environment.getProperty(CONTAINER_RABBITMQ_VIRTUAL_HOST_KEY);
    var username = environment.getProperty(CONTAINER_RABBITMQ_USERNAME_KEY);
    var password = environment.getProperty(CONTAINER_RABBITMQ_PASSWORD_KEY);

    if (StringUtils.isNotBlank(addresses)) {
      mqConfig.put(RABBITMQ_ADDRESSES_ENV_KEY, addresses);
    }
    if (StringUtils.isNotBlank(hosts)) {
      mqConfig.put(RABBITMQ_HOSTS_ENV_KEY, hosts);
    }
    if (StringUtils.isNotBlank(host)) {
      mqConfig.put(RABBITMQ_HOST_ENV_KEY, host);
    }
    if (StringUtils.isNotBlank(port)) {
      mqConfig.put(RABBITMQ_PORT_ENV_KEY, port);
    }
    if (StringUtils.isNotBlank(virtualHost)) {
      mqConfig.put(RABBITMQ_VIRTUAL_HOST_ENV_KEY, virtualHost);
    }
    if (StringUtils.isNotBlank(username)) {
      mqConfig.put(RABBITMQ_USERNAME_ENV_KEY, username);
    }
    if (StringUtils.isNotBlank(password)) {
      mqConfig.put(RABBITMQ_PASSWORD_ENV_KEY, password);
    }
    return mqConfig;
  }

  public Map<String, String> getMQConfigFromEnv() {
    Map<String, String> mqConfig = new LinkedHashMap<>();
    var hosts = environment.getProperty("spring.rabbitmq.hosts");
    var host = environment.getProperty("spring.rabbitmq.host");
    var addresses = environment.getProperty("spring.rabbitmq.addresses");
    if (StringUtils.isAllBlank(host, hosts, addresses)) {
      return mqConfig;
    }

    var port = environment.getProperty("spring.rabbitmq.port");
    var virtualHost = environment.getProperty("spring.rabbitmq.virtual-host");
    var username = environment.getProperty("spring.rabbitmq.username");
    var password = environment.getProperty("spring.rabbitmq.password");

    if (StringUtils.isNotBlank(addresses)) {
      mqConfig.put(RABBITMQ_ADDRESSES_ENV_KEY, addresses);
    }
    if (StringUtils.isNotBlank(hosts)) {
      mqConfig.put(RABBITMQ_HOSTS_ENV_KEY, hosts);
    }
    if (StringUtils.isNotBlank(host)) {
      mqConfig.put(RABBITMQ_HOST_ENV_KEY, host);
    }
    if (StringUtils.isNotBlank(port)) {
      mqConfig.put(RABBITMQ_PORT_ENV_KEY, port);
    }
    if (StringUtils.isNotBlank(virtualHost)) {
      mqConfig.put(RABBITMQ_VIRTUAL_HOST_ENV_KEY, virtualHost);
    }
    if (StringUtils.isNotBlank(username)) {
      mqConfig.put(RABBITMQ_USERNAME_ENV_KEY, username);
    }
    if (StringUtils.isNotBlank(password)) {
      mqConfig.put(RABBITMQ_PASSWORD_ENV_KEY, password);
    }
    return mqConfig;
  }

  // 从 /etc/hosts 中获取主机名
  private String getHostName() {
    Path path = Paths.get("/etc/hostname");
    if (!Files.exists(path)) {
      return "";
    }
    if (!Files.isReadable(path)) {
      return "";
    }
    // 读取 /etc/hostname 文件
    try {
      List<String> lines = Files.readAllLines(path);
      if (!lines.isEmpty()) {
        return lines.get(0).trim();
      }
      return "";
    } catch (IOException e) {
      return "";
    }
  }

  private String getNetworkNameFromEnv() {
    return Optional.ofNullable(environment.getProperty(CONTAINER_NETWORK_NAME_KEY)).orElse("");
  }

  public String getTestAppMemoryDatasourceDriverClass() {
    return configurationService.getRequired(ConfigurationConstant.TEST_APP_MEMORY_DATASOURCE_DRIVER_CLASS);
  }

  public String getTestAppMemoryDatasourceUrl() {
    return configurationService.getRequired(ConfigurationConstant.TEST_APP_MEMORY_DATASOURCE_URL);
  }
}
