package com.ouroboros.resource.util;

import java.util.regex.Pattern;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.resource.model.ResourceScope;

/**
 * Utility class for resource path operations
 */
public class ResourcePathUtils {

  private static final String BASE_PATH = "META-INF/ouroboros-resources/";
  private static final String FRONTEND_PATH = "frontend/";
  private static final String BACKEND_PATH = "backend/";
  private static final String SIDECAR_SUFFIX = ".resource.json";
  private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("^[A-Za-z]:($|/.*)");

  /**
   * Normalize path by removing leading/trailing slashes.
   *
   * @param path the path to normalize
   * @return normalized path
   */
  public static String normalize(String path) {
    if (path == null) {
      return "";
    }
    String normalized = path.trim().replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    validateRelativePath(normalized);
    return normalized;
  }

  private static void validateRelativePath(String path) {
    if (path == null || path.isEmpty()) {
      return;
    }
    if (WINDOWS_ABSOLUTE_PATH.matcher(path).matches()) {
      throw new BusinessException("Resource path cannot be absolute: " + path);
    }
    String[] segments = path.split("/");
    for (String segment : segments) {
      if (".".equals(segment) || "..".equals(segment)) {
        throw new BusinessException("Resource path contains illegal traversal segment: " + path);
      }
    }
  }

  /**
   * Build full classpath path.
   *
   * @param path resource path
   * @return full classpath path
   */
  public static String toClasspathPath(String path) {
    String normalized = normalize(path);
    return BASE_PATH + normalized;
  }

  public static boolean isFrontendPath(String path) {
    String normalized = normalize(path);
    return normalized.startsWith(FRONTEND_PATH);
  }

  public static boolean isBackendPath(String path) {
    String normalized = normalize(path);
    return normalized.startsWith(BACKEND_PATH);
  }

  public static String getScopeDirectory(ResourceScope scope) {
    return scope.getValue() + "/";
  }

  public static String toScopedResourcePath(ResourceScope scope, String key) {
    String normalizedKey = normalize(key);
    if (normalizedKey.isEmpty()) {
      return normalize(getScopeDirectory(scope));
    }
    return normalize(getScopeDirectory(scope) + normalizedKey);
  }

  public static String toSidecarPath(ResourceScope scope, String key) {
    return toScopedResourcePath(scope, key) + SIDECAR_SUFFIX;
  }

  public static String toResourceKey(String scopedPath) {
    String normalized = normalize(scopedPath);
    if (normalized.startsWith(FRONTEND_PATH)) {
      return normalized.substring(FRONTEND_PATH.length());
    }
    if (normalized.startsWith(BACKEND_PATH)) {
      return normalized.substring(BACKEND_PATH.length());
    }
    return normalized;
  }

  /**
   * Get directory path from file path.
   *
   * @param path file path
   * @return directory path
   */
  public static String getDirectory(String path) {
    String normalized = normalize(path);
    int lastSlash = normalized.lastIndexOf('/');
    if (lastSlash < 0) {
      return "";
    }
    return normalized.substring(0, lastSlash);
  }

  /**
   * Get file name from path.
   *
   * @param path file path
   * @return file name
   */
  public static String getFileName(String path) {
    String normalized = normalize(path);
    int lastSlash = normalized.lastIndexOf('/');
    if (lastSlash < 0) {
      return normalized;
    }
    return normalized.substring(lastSlash + 1);
  }

  /**
   * Build index.json path for directory.
   *
   * @param directory directory path
   * @return index.json path
   */
  public static String getIndexPath(String directory) {
    String normalized = normalize(directory);
    if (normalized.isEmpty()) {
      return "index.json";
    }
    return normalized + "/index.json";
  }

  /**
   * Get file extension from path (without dot).
   *
   * @param path file path
   * @return file extension, or empty string if no extension
   */
  public static String getFileExtension(String path) {
    String fileName = getFileName(path);
    int lastDot = fileName.lastIndexOf('.');
    if (lastDot < 0 || lastDot == fileName.length() - 1) {
      return "";
    }
    return fileName.substring(lastDot + 1);
  }
}
