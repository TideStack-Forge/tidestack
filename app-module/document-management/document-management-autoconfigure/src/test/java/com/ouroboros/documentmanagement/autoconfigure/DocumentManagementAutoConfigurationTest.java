package com.ouroboros.documentmanagement.autoconfigure;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import com.ouroboros.data.model.DeferredModelRequestRegistry;
import com.ouroboros.documentmanagement.repository.*;
import com.ouroboros.documentmanagement.service.*;
import com.ouroboros.documentmanagement.storage.*;
import static org.junit.jupiter.api.Assertions.*;

/** Context behavior verifies the extension points, bean identities and incomplete dependency cases. */
class DocumentManagementAutoConfigurationTest {
  @Test void bootTransactionAutoConfigurationActivatesContentServices() {
    new org.springframework.boot.test.context.runner.ApplicationContextRunner()
        // The deployed platform enables this for the existing DBCodingSequencer self injection.
        .withAllowCircularReferences(true)
        .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
            DocumentManagementAutoConfiguration.class,
            org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class))
        .withBean(javax.sql.DataSource.class, () -> new org.springframework.jdbc.datasource.DriverManagerDataSource())
        .withPropertyValues("ouroboros.document-management.enable-web-api=false",
            "ouroboros.document-management.storage.filesystem.base-path=target/document-autoconfigure-boot-storage")
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertNotNull(context.getBean(PlatformTransactionManager.class));
          assertNotNull(context.getBean(LibraryContentService.class));
          assertNotNull(context.getBean(DocumentLibraryContentFacade.class));
        });
  }
  @Test void defaultsAreRegisteredWithOriginalDomainBeanNames() {
    try (var context = context(Services.class)) {
      assertSame(context.getBean(LibraryContentService.class), context.getBean("libraryContentService"));
      assertSame(context.getBean(ACLService.class), context.getBean("aclService"));
      assertNotNull(context.getBean(DocumentLibraryService.class));
      assertNotNull(context.getBean(DocumentLibraryManagementFacade.class));
      assertNotNull(context.getBean(DocumentLibraryContentFacade.class));
      assertNotNull(context.getBean(DocumentLibraryAdminFacade.class));
    }
  }
  @Test void missingStorageSkipsContentAndFacades() {
    try (var context = context(Repositories.class)) {
      assertEquals(0, context.getBeanNamesForType(LibraryContentService.class).length);
      assertEquals(0, context.getBeanNamesForType(DocumentLibraryService.class).length);
      assertEquals(0, context.getBeanNamesForType(DocumentLibraryContentFacade.class).length);
    }
  }
  @Test void missingTransactionManagerSkipsDefaultsThatRequireTransactionalContent() {
    try (var context = context(RepositoriesAndStorage.class)) {
      assertEquals(0, context.getBeanNamesForType(LibraryContentService.class).length);
      assertEquals(0, context.getBeanNamesForType(DocumentLibraryService.class).length);
    }
  }
  @Test void missingRepositoriesSkipsServicesWithoutFailingContext() {
    try (var context = context(StorageOnly.class)) {
      assertEquals(0, context.getBeanNamesForType(LibraryContentService.class).length);
      assertEquals(0, context.getBeanNamesForType(ACLService.class).length);
    }
  }
  @Test void customContentServiceIsRetained() {
    try (var context = context(CustomContent.class)) {
      assertSame(context.getBean("customContent"), context.getBean(LibraryContentService.class));
      assertEquals(1, context.getBeanNamesForType(LibraryContentService.class).length);
    }
  }
  @Test void customCoreDependenciesAllowFacadesWithoutDefaultStorage() {
    try (var context = context(CustomCore.class)) {
      assertNotNull(context.getBean(DocumentLibraryManagementFacade.class));
      assertNotNull(context.getBean(DocumentLibraryContentFacade.class));
      assertNotNull(context.getBean(DocumentLibraryAdminFacade.class));
    }
  }
  @Test void mainConfigurationRegistersCanonicalPersistenceAndStorage() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
          "ouroboros.document-management.enable-web-api", "false",
          "ouroboros.document-management.storage.filesystem.base-path", "target/document-autoconfigure-storage")));
      context.register(TransactionInfrastructure.class, DocumentManagementAutoConfiguration.class); context.refresh();
      assertNotNull(context.getBean(DeferredModelRequestRegistry.class));
      assertSame(context.getBean(LibraryNodeRepository.class), context.getBean("libraryNodeRepository"));
      assertNotNull(context.getBean(PendingContentCleanupRepository.class));
      assertSame(context.getBean(StorageService.class), context.getBean("storageService"));
      assertNotNull(context.getBean(LibraryContentService.class));
    }
  }
  private AnnotationConfigApplicationContext context(Class<?> configuration) {
    var context = new AnnotationConfigApplicationContext();
    context.register(configuration, DocumentManagementServiceAutoConfiguration.class); context.refresh(); return context;
  }
  @Configuration static class Repositories {
    @Bean LibraryRepository libraryRepository() { return proxy(LibraryRepository.class); }
    @Bean LibraryNodeRepository libraryNodeRepository() { return proxy(LibraryNodeRepository.class); }
    @Bean AclEntryRepository aclEntryRepository() { return proxy(AclEntryRepository.class); }
  }
  @Configuration static class StorageOnly { @Bean StorageService storageService() { return proxy(StorageService.class); } }
  @Configuration static class RepositoriesAndStorage extends Repositories {
    @Bean StorageService storageService() { return proxy(StorageService.class); }
  }
  @Configuration static class TransactionInfrastructure {
    @Bean PlatformTransactionManager transactionManager() { return proxy(PlatformTransactionManager.class); }
  }
  @Configuration static class Services extends RepositoriesAndStorage {
    @Bean PendingContentCleanupRepository pendingContentCleanupRepository() { return proxy(PendingContentCleanupRepository.class); }
    @Bean PlatformTransactionManager transactionManager() { return proxy(PlatformTransactionManager.class); }
  }
  @Configuration static class CustomContent extends Services {
    @Bean LibraryContentService customContent() { return proxy(LibraryContentService.class); }
  }
  @Configuration static class CustomCore extends Repositories {
    @Bean LibraryContentService customContent() { return proxy(LibraryContentService.class); }
    @Bean ACLService customAclService() { return proxy(ACLService.class); }
    @Bean PermissionAdminService customPermissionAdminService() { return proxy(PermissionAdminService.class); }
    @Bean DocumentLibraryService customDocumentLibraryService() { return proxy(DocumentLibraryService.class); }
  }
  private static <T> T proxy(Class<T> type) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
      if (method.getDeclaringClass() == Object.class) {
        return switch (method.getName()) {
          case "toString" -> type.getName(); case "hashCode" -> System.identityHashCode(instance);
          case "equals" -> instance == args[0]; default -> throw new UnsupportedOperationException(method.getName());
        };
      }
      if (method.getReturnType() == List.class) { return List.of(); }
      return null;
    }));
  }
}
