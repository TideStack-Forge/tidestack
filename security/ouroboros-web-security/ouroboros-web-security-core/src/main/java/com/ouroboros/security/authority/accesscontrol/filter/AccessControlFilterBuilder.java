package com.ouroboros.security.authority.accesscontrol.filter;

import java.util.Map;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.RowConstraint;

/**
 * @author liansz
 */
public interface AccessControlFilterBuilder {

  /**
   * 处理
   *
   * @param params 原始访问控制参数
   * @return 处理后的访问控制参数（用于数据库查询）
   */
  Map<String, Object> build(Map<String, Object> params, AccessControlDefine acDefine);

  default RowConstraint resolve(Map<String, Object> params, AccessControlDefine define) {
    Map<String, Object> condition = build(params, define);
    return condition.isEmpty() ? RowConstraint.all() : RowConstraint.filter(condition);
  }
}
