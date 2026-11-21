package com.ouroboros.upload.runtime;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;
import com.ouroboros.upload.runtime.config.UploadProperties;

@ComponentScan
@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class FileUploadRuntimeAutoConfiguration {
  private static Logger logger = LoggerFactory.getLogger(FileUploadRuntimeAutoConfiguration.class);

  @PostConstruct
  void init() {
    logger.info("Ouroboros File Upload Runtime AutoConfiguration loaded");
  }

  @Bean(name = "uploadStorageService", destroyMethod = "close")
  public com.ouroboros.upload.service.BlobUploadStorageService uploadStorageService(
      UploadProperties properties, org.springframework.core.env.Environment environment) {
    var store = com.ouroboros.storage.runtime.StorageBinding.create(environment, "ouroboros.upload.storage", () -> {
      if (properties.getStorageType() != com.ouroboros.upload.runtime.config.FileStorageType.FILESYSTEM) {
        throw new com.ouroboros.exception.ValidationException("Use storage.type=s3 for object storage");
      }
      return com.ouroboros.storage.runtime.StorageConfiguration.filesystem(properties.getFileSystem().getRootPath().toString());
    }, "ouroboros.upload.storage-type", "ouroboros.upload.file-system.root-path");
    return new com.ouroboros.upload.service.BlobUploadStorageService(store, "runtime", new com.ouroboros.upload.TimerCleanUpScheduler());
  }

  @Bean
  public AnonymousApiProvider getFileAnonymousAccessApiProvider(UploadProperties uploadProperties) {
    if (uploadProperties.getAnonymousPrefixes().length > 0) {
      var list = Stream.of(uploadProperties.getAnonymousPrefixes())
          .map(s -> "GET /api/file/" + (s.endsWith("**") ? s : s + "/**"))
          .collect(Collectors.toList());
      return () -> list;
    }
    return Collections::emptyList;
  }
}
