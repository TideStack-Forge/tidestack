package com.ouroboros.businessmodel;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import com.ouroboros.businessmodel.config.BasicAuditFieldsConfiguration;
import com.ouroboros.businessmodel.config.BasicAuditFieldsProperties;

@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION,
    classes = AutoConfiguration.class))
@Configuration
@EnableConfigurationProperties(BasicAuditFieldsProperties.class)
public class AppBusinessModelAutoConfiguration {
  final static Logger logger = LoggerFactory.getLogger(AppBusinessModelAutoConfiguration.class);

  @Autowired
  public void configure(BasicAuditFieldsConfiguration props) {
    BasicAuditFieldsConfigurationProxy.INSTANCE = props;
  }

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Business Model Auto Configuration loaded");
  }

  public static class BasicAuditFieldsConfigurationProxy implements BasicAuditFieldsConfiguration {
    private static BasicAuditFieldsConfiguration INSTANCE;

    @Override
    public FieldSetting getCreatedAt() {
      return INSTANCE.getCreatedAt();
    }

    @Override
    public FieldSetting getCreatedBy() {
      return INSTANCE.getCreatedBy();
    }

    @Override
    public FieldSetting getUpdatedAt() {
      return INSTANCE.getUpdatedAt();
    }

    @Override
    public FieldSetting getUpdatedBy() {
      return INSTANCE.getUpdatedBy();
    }

    @Override
    public FieldSetting getDeletedAt() {
      return INSTANCE.getDeletedAt();
    }

    @Override
    public FieldSetting getDeletedBy() {
      return INSTANCE.getDeletedBy();
    }

    @Override
    public FieldSetting getIsDeleted() {
      return INSTANCE.getIsDeleted();
    }
  }
}
