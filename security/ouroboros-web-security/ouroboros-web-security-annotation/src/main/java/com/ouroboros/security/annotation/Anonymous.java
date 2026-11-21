package com.ouroboros.security.annotation;

import java.lang.annotation.*;

/**
 * 允许未认证请求访问；携带有效凭据时仍可取得对应的当前用户。
 *
 * @author Song Mingxu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Anonymous {
}
