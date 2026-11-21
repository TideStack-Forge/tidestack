package com.ouroboros.documentmanagement.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.ouroboros.data.TypedDataModelAutoConfiguration;
import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.documentmanagement.persistence.repository.AclEntryRepositoryImpl;
import com.ouroboros.documentmanagement.persistence.repository.LibraryNodeRepositoryImpl;
import com.ouroboros.documentmanagement.persistence.repository.LibraryRepositoryImpl;
import com.ouroboros.documentmanagement.repository.AclEntryRepository;
import com.ouroboros.documentmanagement.repository.LibraryNodeRepository;
import com.ouroboros.documentmanagement.repository.LibraryRepository;

/**
 * 档案库持久化自动配置
 *
 * <p>自动扫描实体类并注册数据模型，配置 Repository Bean
 */
@Configuration
@DataModelScan
@Import({
    TypedDataModelAutoConfiguration.class,
    DocumentManagementPersistenceAutoConfiguration.PendingContentCleanupRepositoryConfiguration.class,
    DocumentManagementPersistenceAutoConfiguration.LibraryRepositoryConfiguration.class,
    DocumentManagementPersistenceAutoConfiguration.LibraryNodeRepositoryConfiguration.class,
    DocumentManagementPersistenceAutoConfiguration.AclEntryRepositoryConfiguration.class
})
public class DocumentManagementPersistenceAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(com.ouroboros.documentmanagement.repository.PendingContentCleanupRepository.class)
  @Import(com.ouroboros.documentmanagement.persistence.repository.PendingContentCleanupRepositoryImpl.class)
  static class PendingContentCleanupRepositoryConfiguration { }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(LibraryRepository.class)
  @Import(LibraryRepositoryImpl.class)
  static class LibraryRepositoryConfiguration {
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(LibraryNodeRepository.class)
  @Import(LibraryNodeRepositoryImpl.class)
  static class LibraryNodeRepositoryConfiguration {
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(AclEntryRepository.class)
  @Import(AclEntryRepositoryImpl.class)
  static class AclEntryRepositoryConfiguration {
  }
}
