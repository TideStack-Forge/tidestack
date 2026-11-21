package com.ouroboros.organizationstructure;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.AppContext;
import com.ouroboros.organizationstructure.definition.BundledDefaultPositionDefinitionProvider;
import com.ouroboros.organizationstructure.definition.AppDefaultPositionDefinitionProvider;
import com.ouroboros.organizationstructure.definition.RuntimeOverridePositionDefinitionProvider;
import com.ouroboros.organizationstructure.service.AdminLevelService;
import com.ouroboros.organizationstructure.service.impl.AdminLevelSeedRunner;

/**
 * @author liansz
 */
@DataModelScan
@ComponentScan
public class OrganizationStructureAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(OrganizationStructureAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Organization Structure Auto Configuration loaded");
  }

  @Bean
  @ConditionalOnMissingBean
  public AppDefaultPositionDefinitionProvider appDefaultPositionDefinitionProvider(AppContext appContext) {
    return new AppDefaultPositionDefinitionProvider(java.nio.file.Paths.get(appContext.getHomePath()));
  }

  @Bean
  @ConditionalOnMissingBean
  public BundledDefaultPositionDefinitionProvider bundledDefaultPositionDefinitionProvider() {
    return new BundledDefaultPositionDefinitionProvider();
  }

  @Bean
  @ConditionalOnMissingBean
  public RuntimeOverridePositionDefinitionProvider runtimeOverridePositionDefinitionProvider(AppContext appContext) {
    return new RuntimeOverridePositionDefinitionProvider(appContext);
  }

  @Bean
  @ConditionalOnMissingBean(AdminLevelSeedRunner.class)
  public ApplicationRunner adminLevelSeedRunner(AdminLevelService adminLevelService) {
    return new AdminLevelSeedRunner(adminLevelService, true);
  }
}
