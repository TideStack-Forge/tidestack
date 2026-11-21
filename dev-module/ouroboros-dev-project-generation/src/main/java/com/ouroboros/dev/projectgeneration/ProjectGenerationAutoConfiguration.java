package com.ouroboros.dev.projectgeneration;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dev.projectgeneration.service.ProjectPlatformVersionProvider;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogReplayStateStore;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCatalogReplayStateModel;
import com.ouroboros.dev.projectgeneration.catalog.TypedDataModelProjectTemplateCatalogReplayStateStore;
import com.ouroboros.dev.projectgeneration.service.impl.BuildMetadataProjectPlatformVersionProvider;

@Configuration
@ComponentScan
@DataModelScan
public class ProjectGenerationAutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(ProjectGenerationAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Dev Project Generation Auto Configuration loaded");
  }

  @Bean
  @ConditionalOnMissingBean
  public ProjectPlatformVersionProvider projectPlatformVersionProvider(
      ObjectProvider<BuildProperties> buildProperties,
      @Value("${ouroboros.version:${project.version:unknown}}") String configuredVersion) {
    return new BuildMetadataProjectPlatformVersionProvider(buildProperties.getIfAvailable(), configuredVersion);
  }

  @Bean
  @ConditionalOnMissingBean
  public ProjectTemplateCatalogReplayStateStore projectTemplateCatalogReplayStateStore(
      TypedDataModel<String, ProjectTemplateCatalogReplayStateModel> dataModel) {
    return new TypedDataModelProjectTemplateCatalogReplayStateStore(dataModel);
  }
}
