package com.ouroboros.security.externalauth.secret;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves secrets from file system.
 * Reference format: "file:/path/to/secret"
 */
public class FileSecretResolver implements SecretResolver {

  private static final Logger logger = LoggerFactory.getLogger(FileSecretResolver.class);

  @Override
  public boolean supports(String scheme) {
    return "file".equals(scheme);
  }

  @Override
  public Optional<String> resolve(String reference) {
    if (reference == null || !reference.startsWith("file:")) {
      return Optional.empty();
    }
    String filePath = reference.substring("file:".length());
    try {
      byte[] bytes = Files.readAllBytes(Paths.get(filePath));
      return Optional.of(new String(bytes, StandardCharsets.UTF_8).trim());
    } catch (IOException e) {
      logger.warn("Failed to read secret file: {}", filePath, e);
      return Optional.empty();
    }
  }
}
