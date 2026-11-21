package com.ouroboros.storage.s3;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.storage.*;

public final class S3BlobStoreFactory implements BlobStoreFactory {
  private static final Set<String> OPTIONS = Set.of("endpoint", "region", "bucket", "path-style-access",
      "credentials-mode", "access-key", "secret-key", "session-token", "key-prefix", "technical-prefix",
      "part-size-mib", "connection-timeout-seconds", "read-timeout-seconds");
  @Override public String type() { return "s3"; }
  @Override public BlobStore create(Map<String, String> options) {
    if (!OPTIONS.containsAll(options.keySet())) { throw new ValidationException("Unknown S3 storage option"); }
    String bucket = required(options, "bucket");
    String region = required(options, "region");
    String keyPrefix = prefix(options.getOrDefault("key-prefix", ""));
    String technicalPrefix = options.getOrDefault("technical-prefix", ".ouroboros-storage/s3/");
    if (!technicalPrefix.startsWith(ObjectKeys.TECHNICAL_PREFIX) || !technicalPrefix.endsWith("/")
        || technicalPrefix.contains("..") || technicalPrefix.contains("\\") || technicalPrefix.contains("//")) {
      throw new ValidationException("S3 technical-prefix must be inside the reserved technical namespace");
    }
    int partSize = integer(options, "part-size-mib", 8);
    if (partSize < 5 || partSize > 128) { throw new ValidationException("S3 part-size-mib must be between 5 and 128"); }
    String pathStyle = options.getOrDefault("path-style-access", "false");
    if (!pathStyle.equals("true") && !pathStyle.equals("false")) { throw new ValidationException("Invalid path-style-access"); }
    var builder = S3Client.builder().region(Region.of(region))
        .httpClientBuilder(UrlConnectionHttpClient.builder()
            .connectionTimeout(Duration.ofSeconds(integer(options, "connection-timeout-seconds", 10)))
            .socketTimeout(Duration.ofSeconds(integer(options, "read-timeout-seconds", 120))))
        .forcePathStyle(Boolean.parseBoolean(pathStyle))
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
    if (options.containsKey("endpoint")) {
      URI endpoint;
      try { endpoint = URI.create(required(options, "endpoint")); }
      catch (IllegalArgumentException e) { throw new ValidationException("Invalid S3 endpoint"); }
      if (!("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
          || endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) {
        throw new ValidationException("S3 endpoint must be an HTTP(S) service endpoint");
      }
      builder.endpointOverride(endpoint);
    }
    Optional<DefaultCredentialsProvider> defaultCredentials = Optional.empty();
    String mode = options.getOrDefault("credentials-mode", "default");
    if (mode.equals("static")) {
      String access = required(options, "access-key");
      String secret = required(options, "secret-key");
      builder.credentialsProvider(StaticCredentialsProvider.create(options.containsKey("session-token")
          ? AwsSessionCredentials.create(access, secret, required(options, "session-token")) : AwsBasicCredentials.create(access, secret)));
    } else if (!mode.equals("default") || options.containsKey("access-key") || options.containsKey("secret-key") || options.containsKey("session-token")) {
      throw new ValidationException("S3 credentials-mode conflicts with static credentials");
    }
    if (mode.equals("default")) {
      // The SDK static create() provider is shared; each consumer must own its credential lifecycle.
      var provider = DefaultCredentialsProvider.builder().build();
      defaultCredentials = Optional.of(provider); builder.credentialsProvider(provider);
    }
    try { return new S3BlobStore(builder.build(), bucket, keyPrefix, technicalPrefix, partSize * 1024 * 1024, defaultCredentials); }
    catch (RuntimeException e) { defaultCredentials.ifPresent(DefaultCredentialsProvider::close); throw e; }
  }
  private static String required(Map<String, String> options, String key) {
    String value = options.get(key);
    if (value == null || value.isBlank()) { throw new ValidationException("Missing S3 option: " + key); }
    return value;
  }
  private static int integer(Map<String, String> options, String key, int fallback) {
    try {
      int value = Integer.parseInt(options.getOrDefault(key, Integer.toString(fallback)));
      if (value <= 0) { throw new ValidationException("S3 numeric option must be positive: " + key); }
      return value;
    } catch (NumberFormatException e) { throw new ValidationException("Invalid S3 numeric option: " + key); }
  }
  private static String prefix(String prefix) {
    ObjectKeys.validatePrefix(prefix);
    if (!prefix.isEmpty() && !prefix.endsWith("/")) { throw new ValidationException("S3 key-prefix must end with /"); }
    return prefix;
  }
}
