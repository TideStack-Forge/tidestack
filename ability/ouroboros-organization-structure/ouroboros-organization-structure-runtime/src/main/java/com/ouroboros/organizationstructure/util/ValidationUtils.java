package com.ouroboros.organizationstructure.util;

import com.ouroboros.exception.BusinessException;

public class ValidationUtils {

  public static void validateNotBlank(String value, String fieldName) {
    if (value == null || value.trim().isEmpty()) {
      throw new BusinessException(fieldName + "不能为空");
    }
  }

  public static void validateNotNull(Object value, String fieldName) {
    if (value == null) {
      throw new BusinessException(fieldName + "不能为空");
    }
  }

  public static void validateUserLinkInfo(com.ouroboros.organizationstructure.dto.request.UserLinkInfo info) {
    if (info == null) {
      return;
    }

    validateNotBlank(info.getMode(), "用户关联模式");

    switch (info.getMode()) {
      case "create":
        validateNotBlank(info.getUsername(), "用户名");
        validateNotBlank(info.getPassword(), "密码");
        break;
      case "link":
        validateNotBlank(info.getUserId(), "用户ID");
        break;
      case "none":
        break;
      default:
        throw new BusinessException("无效的用户关联模式: " + info.getMode());
    }
  }
}
