package com.ouroboros.i18n.runtime;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.data.annotation.DataModelScan;

/**
 * 国际化运行时自动配置
 *
 * @author AI Assistant
 */
@Configuration
@EnableCaching
@ComponentScan
@DataModelScan
public class I18nAutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(I18nAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("I18n module loaded - providers: FileSystemI18nLocaleProvider, DatabaseI18nLocaleProvider");
  }
}
