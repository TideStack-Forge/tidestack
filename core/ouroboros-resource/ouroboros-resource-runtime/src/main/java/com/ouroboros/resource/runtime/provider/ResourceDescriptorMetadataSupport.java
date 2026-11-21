package com.ouroboros.resource.runtime.provider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.ouroboros.resource.model.ResourceDescriptor;

final class ResourceDescriptorMetadataSupport {
  private static final Logger logger = LoggerFactory.getLogger(ResourceDescriptorMetadataSupport.class);

  private ResourceDescriptorMetadataSupport() {
  }

  static ResourceDescriptor enrichFromFile(ResourceDescriptor descriptor, Path filePath) {
    if (descriptor == null || filePath == null) {
      return descriptor;
    }
    if (isBlank(descriptor.getContentType())) {
      descriptor.setContentType(detectContentType(descriptor.getFileName(), filePath));
    }
    if (descriptor.getSize() == null) {
      try {
        descriptor.setSize(Files.size(filePath));
      } catch (IOException error) {
        logger.warn("Failed to get file size for {}: {}", filePath, error.getMessage());
      }
    }
    if (isBlank(descriptor.getChecksum())) {
      descriptor.setChecksum(calculateChecksum(new InputStreamSupplier() {
        @Override
        public InputStream get() throws IOException {
          return Files.newInputStream(filePath);
        }
      }));
    }
    if (descriptor.getLastModifiedAt() == null) {
      try {
        descriptor.setLastModifiedAt(Instant.ofEpochMilli(Files.getLastModifiedTime(filePath).toMillis()));
      } catch (IOException error) {
        logger.warn("Failed to get last modified time for {}: {}", filePath, error.getMessage());
      }
    }
    return descriptor;
  }

  static ResourceDescriptor enrichFromClasspath(ResourceDescriptor descriptor, ClassPathResource resource) {
    if (descriptor == null || resource == null) {
      return descriptor;
    }
    if (isBlank(descriptor.getContentType())) {
      descriptor.setContentType(detectContentType(descriptor.getFileName(), null));
    }
    if (descriptor.getSize() == null) {
      try {
        descriptor.setSize(resource.contentLength());
      } catch (IOException error) {
        logger.warn("Failed to get content length for classpath resource {}: {}", resource.getPath(), error.getMessage());
      }
    }
    if (isBlank(descriptor.getChecksum())) {
      descriptor.setChecksum(calculateChecksum(new InputStreamSupplier() {
        @Override
        public InputStream get() throws IOException {
          return resource.getInputStream();
        }
      }));
    }
    return descriptor;
  }

  private static String detectContentType(String fileName, Path filePath) {
    String guessed = fileName == null ? null : URLConnection.guessContentTypeFromName(fileName);
    if (!isBlank(guessed)) {
      return guessed;
    }
    if (filePath != null) {
      try {
        String probed = Files.probeContentType(filePath);
        if (!isBlank(probed)) {
          return probed;
        }
      } catch (IOException error) {
        logger.warn("Failed to probe content type for {}: {}", filePath, error.getMessage());
      }
    }
    return "application/octet-stream";
  }

  private static String calculateChecksum(InputStreamSupplier supplier) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[4096];
      try (InputStream inputStream = supplier.get()) {
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
          digest.update(buffer, 0, read);
        }
      }
      byte[] hash = digest.digest();
      StringBuilder builder = new StringBuilder();
      for (byte value : hash) {
        builder.append(String.format("%02x", value));
      }
      return "sha256:" + builder;
    } catch (Exception error) {
      logger.warn("Failed to calculate checksum: {}", error.getMessage());
      return null;
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private interface InputStreamSupplier {
    InputStream get() throws IOException;
  }
}
