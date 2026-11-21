package com.ouroboros.documentmanagement.autoconfigure;

import com.ouroboros.documentmanagement.persistence.DocumentManagementPersistenceAutoConfiguration;
import com.ouroboros.documentmanagement.repository.AclEntryRepository;
import com.ouroboros.documentmanagement.repository.LibraryNodeRepository;
import com.ouroboros.documentmanagement.repository.LibraryRepository;
import com.ouroboros.documentmanagement.repository.PendingContentCleanupRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.ouroboros.documentmanagement.service.ACLService;
import com.ouroboros.documentmanagement.service.DocumentLibraryAdminFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryManagementFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryService;
import com.ouroboros.documentmanagement.service.LibraryContentService;
import com.ouroboros.documentmanagement.service.PermissionAdminService;
import com.ouroboros.documentmanagement.service.impl.ACLServiceImpl;
import com.ouroboros.documentmanagement.service.impl.DocumentLibraryAdminFacadeImpl;
import com.ouroboros.documentmanagement.service.impl.DocumentLibraryContentFacadeImpl;
import com.ouroboros.documentmanagement.service.impl.DocumentLibraryManagementFacadeImpl;
import com.ouroboros.documentmanagement.service.impl.DocumentLibraryServiceImpl;
import com.ouroboros.documentmanagement.service.impl.LibraryContentServiceImpl;
import com.ouroboros.documentmanagement.service.impl.PermissionAdminServiceImpl;
import com.ouroboros.documentmanagement.storage.StorageService;
import com.ouroboros.documentmanagement.storage.DocumentStorageAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 文档管理服务自动配置
 *
 * <p>自动配置所有业务服务和门面服务
 *
 * <p>配置顺序：
 * <ol>
 *   <li>基础服务（LibraryContentService, PermissionAdminService, ACLService）</li>
 *   <li>DocumentLibraryService（依赖基础服务）</li>
 *   <li>门面服务（依赖所有核心服务）</li>
 * </ol>
 */
@Configuration
@AutoConfigureAfter({DocumentManagementPersistenceAutoConfiguration.class, DocumentStorageAutoConfiguration.class})
@Import({
    DocumentManagementServiceAutoConfiguration.LibraryContentServiceConfiguration.class,
    DocumentManagementServiceAutoConfiguration.PermissionAdminServiceConfiguration.class,
    DocumentManagementServiceAutoConfiguration.AclServiceConfiguration.class,
    DocumentManagementServiceAutoConfiguration.DocumentLibraryServiceConfiguration.class,
    DocumentManagementServiceAutoConfiguration.DocumentLibraryManagementFacadeConfiguration.class,
    DocumentManagementServiceAutoConfiguration.DocumentLibraryContentFacadeConfiguration.class,
    DocumentManagementServiceAutoConfiguration.DocumentLibraryAdminFacadeConfiguration.class
})
public class DocumentManagementServiceAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(LibraryContentService.class)
  @ConditionalOnBean({LibraryNodeRepository.class, StorageService.class, PendingContentCleanupRepository.class, PlatformTransactionManager.class})
  @org.springframework.transaction.annotation.EnableTransactionManagement(proxyTargetClass = true)
  @EnableScheduling
  @Import({LibraryContentServiceImpl.class, com.ouroboros.documentmanagement.service.impl.DocumentContentCleanupService.class})
  static class LibraryContentServiceConfiguration {
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(PermissionAdminService.class)
  @ConditionalOnBean(AclEntryRepository.class)
  @Import(PermissionAdminServiceImpl.class)
  static class PermissionAdminServiceConfiguration {
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(ACLService.class)
  @ConditionalOnBean({LibraryRepository.class, LibraryNodeRepository.class, AclEntryRepository.class})
  @Import(ACLServiceImpl.class)
  static class AclServiceConfiguration {
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(DocumentLibraryService.class)
  @Conditional(DocumentLibraryServiceDependenciesCondition.class)
  @Import(DocumentLibraryServiceImpl.class)
  static class DocumentLibraryServiceConfiguration {
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(DocumentLibraryManagementFacade.class)
  @Conditional(DocumentLibraryManagementFacadeDependenciesCondition.class)
  @Import(DocumentLibraryManagementFacadeImpl.class)
  static class DocumentLibraryManagementFacadeConfiguration {
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(DocumentLibraryContentFacade.class)
  @Conditional(DocumentLibraryContentFacadeDependenciesCondition.class)
  @Import(DocumentLibraryContentFacadeImpl.class)
  static class DocumentLibraryContentFacadeConfiguration {
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingBean(DocumentLibraryAdminFacade.class)
  @Conditional(DocumentLibraryAdminFacadeDependenciesCondition.class)
  @Import(DocumentLibraryAdminFacadeImpl.class)
  static class DocumentLibraryAdminFacadeConfiguration {
  }

  static class DocumentLibraryServiceDependenciesCondition extends AnyNestedCondition {

    DocumentLibraryServiceDependenciesCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnBean({LibraryRepository.class, LibraryNodeRepository.class, AclEntryRepository.class,
        LibraryContentService.class, ACLService.class})
    static class DirectDependencies {
    }

    @ConditionalOnBean({LibraryRepository.class, LibraryNodeRepository.class, AclEntryRepository.class,
        StorageService.class, PendingContentCleanupRepository.class, PlatformTransactionManager.class})
    static class DefaultInfrastructure {
    }
  }

  static class DocumentLibraryManagementFacadeDependenciesCondition extends AnyNestedCondition {

    DocumentLibraryManagementFacadeDependenciesCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnBean({DocumentLibraryService.class, ACLService.class, PermissionAdminService.class,
        LibraryNodeRepository.class})
    static class DirectDependencies {
    }

    @ConditionalOnBean({LibraryRepository.class, LibraryNodeRepository.class, AclEntryRepository.class,
        StorageService.class, PendingContentCleanupRepository.class, PlatformTransactionManager.class})
    static class DefaultInfrastructure {
    }
  }

  static class DocumentLibraryContentFacadeDependenciesCondition extends AnyNestedCondition {

    DocumentLibraryContentFacadeDependenciesCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnBean({LibraryContentService.class, ACLService.class, PermissionAdminService.class,
        DocumentLibraryService.class, LibraryNodeRepository.class, AclEntryRepository.class})
    static class DirectDependencies {
    }

    @ConditionalOnBean({LibraryRepository.class, LibraryNodeRepository.class, AclEntryRepository.class,
        StorageService.class, PendingContentCleanupRepository.class, PlatformTransactionManager.class})
    static class DefaultInfrastructure {
    }
  }

  static class DocumentLibraryAdminFacadeDependenciesCondition extends AnyNestedCondition {

    DocumentLibraryAdminFacadeDependenciesCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnBean({DocumentLibraryService.class, PermissionAdminService.class, LibraryRepository.class,
        LibraryNodeRepository.class, AclEntryRepository.class})
    static class DirectDependencies {
    }

    @ConditionalOnBean({LibraryRepository.class, LibraryNodeRepository.class, AclEntryRepository.class,
        StorageService.class, PendingContentCleanupRepository.class, PlatformTransactionManager.class})
    static class DefaultInfrastructure {
    }
  }
}
