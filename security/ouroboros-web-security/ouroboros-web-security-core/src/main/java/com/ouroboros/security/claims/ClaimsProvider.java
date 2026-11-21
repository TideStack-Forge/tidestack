package com.ouroboros.security.claims;

import java.util.Set;

import jakarta.annotation.Priority;

import com.ouroboros.security.rbac.User;

/**
 * Claims 提供者 SPI
 * 
 * 优先级通过 @Priority 注解指定（数字越大优先级越高）：
 * - 300: 自定义扩展（可覆盖内置）
 * - 200: 业务模块
 * - 100: 系统内置
 * - 50: 基础属性
 * 
 * 未标注 @Priority 的 Provider 默认优先级为 0
 */
public interface ClaimsProvider {

  /**
   * 为指定用户提供 Claims
   */
  Claims provide(User user, ClaimsContext context);

  /**
   * 声明此 Provider 提供的 Claim 类型
   */
  Set<String> getProvidedClaimTypes();

  default String getName() {
    return getClass().getSimpleName();
  }
}
