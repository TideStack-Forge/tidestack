package com.ouroboros.documentmanagement.autoconfigure;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.ouroboros.documentmanagement.persistence.DocumentManagementPersistenceAutoConfiguration;
import com.ouroboros.documentmanagement.storage.DocumentStorageAutoConfiguration;

/**
 * 文档管理模块自动配置
 *
 * <p>根据配置属性自动配置：
 * <ul>
 *   <li>存储服务（本地文件系统、S3、OSS 等）</li>
 *   <li>持久化仓储（TypedDataModel、JPA 等）</li>
 *   <li>业务服务和门面服务</li>
 *   <li>REST API Controllers</li>
 * </ul>
 */
@Configuration
@org.springframework.boot.autoconfigure.AutoConfigureAfter(
    org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class)
@ConditionalOnProperty(prefix = "ouroboros.document-management", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DocumentManagementProperties.class)
@Import({
    DocumentManagementPersistenceAutoConfiguration.class,
    DocumentStorageAutoConfiguration.class,
    DocumentManagementServiceAutoConfiguration.class,
    DocumentManagementWebApiAutoConfiguration.class
})
public class DocumentManagementAutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(DocumentManagementAutoConfiguration.class);
  // 主配置类，通过 @Import 导入各个子配置
  @PostConstruct
  public void init() {
    logger.info("Document Management AutoConfiguration initialized.");
  }
}
