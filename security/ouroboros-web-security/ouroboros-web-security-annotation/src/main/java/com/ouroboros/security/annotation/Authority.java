package com.ouroboros.security.annotation;

import java.lang.annotation.*;

/**
 * 绑定权限定义
 *
 * @author Song Mingxu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Authority {

  /**
   * 权限定义全名，支持单值与多值写法：
   * <pre>
   *   &#64;Authority("system.user.list")
   *   &#64;Authority({"system.user.list", "system.user.export"})
   * </pre>
   *
   * @return 权限定义全名列表
   */
  String[] value();
}
