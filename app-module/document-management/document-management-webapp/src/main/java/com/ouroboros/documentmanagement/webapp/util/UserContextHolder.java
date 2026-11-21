package com.ouroboros.documentmanagement.webapp.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 用户上下文工具类
 *
 * <p>从 Spring Security 上下文中获取当前登录用户信息
 */
public class UserContextHolder {

  /**
   * 获取当前登录用户ID
   *
   * @return 用户ID，如果未登录则返回 null
   */
  public static String getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }

    // 根据实际的 Authentication Principal 类型进行调整
    // 这里假设 Principal 是用户ID字符串
    Object principal = authentication.getPrincipal();

    if (principal instanceof String) {
      return (String) principal;
    }

    // 如果 Principal 是自定义的 UserDetails 对象，可以这样获取
    // if (principal instanceof UserDetails) {
    //   return ((UserDetails) principal).getUsername();
    // }

    return principal != null ? principal.toString() : null;
  }

  /**
   * 获取当前登录用户ID，如果未登录则抛出异常
   *
   * @return 用户ID
   * @throws IllegalStateException 如果用户未登录
   */
  public static String requireCurrentUserId() {
    String userId = getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("用户未登录");
    }
    return userId;
  }

  /**
   * 检查用户是否已登录
   *
   * @return 如果已登录返回 true
   */
  public static boolean isAuthenticated() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.isAuthenticated();
  }
}
