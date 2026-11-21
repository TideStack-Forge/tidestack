package com.ouroboros.configuration;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.ouroboros.configuration.center.ConfigurationGroupMetadataCenter;
import com.ouroboros.configuration.center.ConfigurationMetadataCenter;
import com.ouroboros.configuration.service.RuntimeConfigurationService;
import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshedEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;

/**
 * @author liansz
 */
@DataModelScan
@Configuration("configurationAutoConfiguration")
@ComponentScan("com.ouroboros.configuration")
public class ConfigurationRuntimeAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(ConfigurationRuntimeAutoConfiguration.class);

  @Autowired
  private RuntimeConfigurationService configurationService;

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Configuration Auto Configuration loaded");
  }

  @EventListener
  public void onMetadataRefreshedEvent(OuroborosMetadataRefreshedEvent event) {
    ConfigurationMetadataCenter.refresh();
    ConfigurationGroupMetadataCenter.refresh();
  }

  @EventListener
  public void onModelRefreshedEvent(OuroborosModelRefreshedEvent event) {
    // 程序启动成功，在此查询所有配置、检查配置是否正常、缓存配置
    try {
      configurationService.clearCache();
      configurationService.getAllConfigurationModel();
      // TODO 扩展：打印配置信息
    } catch (Exception e) {
      throw new PlatformException("配置初始化失败：", e);
    }
  }
}
