package com.ouroboros.documentmanagement.storage;

import com.ouroboros.documentmanagement.storage.service.BlobDocumentStorageService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import com.ouroboros.documentmanagement.storage.filesystem.LocalFileSystemStorageProperties;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.storage.runtime.StorageBinding;
import com.ouroboros.storage.runtime.StorageConfiguration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({LocalFileSystemStorageProperties.class, DocumentContentProperties.class})
public class DocumentStorageAutoConfiguration {
  @Bean(name = "storageService", destroyMethod = "close")
  @ConditionalOnMissingBean(StorageService.class)
  public BlobDocumentStorageService storageService(Environment environment, LocalFileSystemStorageProperties legacy,
                                                   DocumentContentProperties content) {
    String oldPrefix = "ouroboros.document-management.storage.filesystem.";
    if (environment.containsProperty(oldPrefix + "path-strategy") && !environment.containsProperty("ouroboros.document-management.content.path-strategy")) {
      content.setPathStrategy(DocumentContentProperties.PathStrategy.valueOf(legacy.getPathStrategy().name()));
    }
    if (environment.containsProperty(oldPrefix + "calculate-checksum") && !environment.containsProperty("ouroboros.document-management.content.calculate-checksum")) {
      content.setCalculateChecksum(legacy.isCalculateChecksum());
    }
    var store = StorageBinding.create(environment, "ouroboros.document-management.storage", () -> {
      if (!environment.getProperty("ouroboros.document-management.storage-type", "filesystem").equalsIgnoreCase("filesystem")) {
        throw new ValidationException("Use document-management.storage.type=s3 for object storage");
      }
      return StorageConfiguration.filesystem(legacy.getBasePath());
    }, "ouroboros.document-management.storage-type", oldPrefix + "base-path");
    return new BlobDocumentStorageService(store, content);
  }
}
