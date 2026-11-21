package com.ouroboros.vcs.security;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

/**
 * 安全路径验证器，防止路径遍历攻击
 */
public class SecurePathValidator {
  private final Path basePath;
  private final List<String> blockedPatterns;
  private final Pattern dangerousCharsPattern;

  public SecurePathValidator(String repositoryPath) {
    this(repositoryPath, Arrays.asList("../", "..\\", "~", "..", "%2e%2e", "%2E%2E"));
  }

  public SecurePathValidator(String repositoryPath, List<String> blockedPatterns) {
    if (StringUtils.isBlank(repositoryPath)) {
      throw new IllegalArgumentException("Repository path cannot be null or empty");
    }

    this.basePath = Paths.get(repositoryPath).toAbsolutePath().normalize();
    this.blockedPatterns = blockedPatterns;

    // 编译危险字符的正则表达式
    String dangerousChars = "[\\.]{2,}|[~]|%2[eE]|%5[cC]|\\x00-\\x1f|\\x7f-\\x9f";
    this.dangerousCharsPattern = Pattern.compile(dangerousChars);
  }

  /**
   * 验证并解析相对路径
   *
   * @param relativePath 相对路径
   * @return 安全的绝对路径
   * @throws SecurityException        如果检测到路径遍历攻击
   * @throws IllegalArgumentException 如果路径为空或无效
   */
  public Path validateAndResolvePath(String relativePath) {
    if (StringUtils.isBlank(relativePath)) {
      throw new IllegalArgumentException("Path cannot be null or empty");
    }

    // 1. 检查URL编码的危险字符
    String decodedPath = decodeUrlPath(relativePath);

    // 2. 检查危险字符模式
    validateDangerousPatterns(decodedPath);

    // 3. 规范化路径并检查遍历攻击
    Path resolved = basePath.resolve(decodedPath).normalize();

    // 4. 验证解析后的路径仍在基础目录内
    if (!resolved.startsWith(basePath)) {
      throw new com.ouroboros.vcs.exception.VCSSecurityException(
          com.ouroboros.vcs.exception.VCSErrorCode.PATH_TRAVERSAL_ATTACK,
          String.format("Attempted path: %s, Base path: %s, Resolved path: %s", relativePath, basePath, resolved),
          "路径遍历攻击");
    }

    // 5. 检查路径长度（防止DoS攻击）
    if (resolved.toString().length() > 1000) {
      throw new com.ouroboros.vcs.exception.VCSSecurityException(
          com.ouroboros.vcs.exception.VCSErrorCode.SECURITY_VIOLATION,
          relativePath,
          "路径长度超限: " + resolved.toString().length());
    }

    return resolved;
  }

  /**
   * 验证路径是否安全（不抛出异常版本）
   *
   * @param relativePath 相对路径
   * @return 如果路径安全返回true，否则返回false
   */
  public boolean isPathSafe(String relativePath) {
    try {
      validateAndResolvePath(relativePath);
      return true;
    } catch (com.ouroboros.vcs.exception.VCSSecurityException | IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * 获取安全的相对路径（相对于基础路径）
   *
   * @param relativePath 输入的相对路径
   * @return 安全的相对路径字符串
   */
  public String getSafeRelativePath(String relativePath) {
    Path safePath = validateAndResolvePath(relativePath);
    return basePath.relativize(safePath).toString().replace('\\', '/');
  }

  private String decodeUrlPath(String path) {
    // 简单的URL解码，防止编码绕过
    return path.replace("%2e", ".")
        .replace("%2E", ".")
        .replace("%2f", "/")
        .replace("%2F", "/")
        .replace("%5c", "\\")
        .replace("%5C", "\\");
  }

  private void validateDangerousPatterns(String path) {
    // 检查预定义的危险模式
    for (String pattern : blockedPatterns) {
      if (path.contains(pattern)) {
        throw new com.ouroboros.vcs.exception.VCSSecurityException(
            com.ouroboros.vcs.exception.VCSErrorCode.PATH_VALIDATION_FAILED,
            path,
            "危险路径模式: " + pattern);
      }
    }

    // 检查正则表达式匹配的危险字符
    if (dangerousCharsPattern.matcher(path).find()) {
      throw new com.ouroboros.vcs.exception.VCSSecurityException(
          com.ouroboros.vcs.exception.VCSErrorCode.PATH_VALIDATION_FAILED,
          path,
          "包含危险字符");
    }

    // 检查绝对路径尝试
    if (path.startsWith("/") || (path.length() > 1 && path.charAt(1) == ':')) {
      throw new com.ouroboros.vcs.exception.VCSSecurityException(
          com.ouroboros.vcs.exception.VCSErrorCode.PATH_VALIDATION_FAILED,
          path,
          "不允许绝对路径");
    }
  }

  /**
   * 获取基础路径
   *
   * @return 基础路径
   */
  public Path getBasePath() {
    return basePath;
  }

  /**
   * 验证多个路径
   *
   * @param relativePaths 相对路径列表
   * @return 安全的绝对路径列表
   */
  public List<Path> validateAndResolvePaths(List<String> relativePaths) {
    if (relativePaths == null) {
      throw new IllegalArgumentException("Paths list cannot be null");
    }

    return relativePaths.stream()
        .map(this::validateAndResolvePath)
        .collect(Collectors.toList());
  }
}