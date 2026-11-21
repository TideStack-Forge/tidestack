package com.ouroboros.dev.app.service.runtime;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.exception.BusinessException;

class ProfileStorageKeyEncoder {

  String encode(String profileId) {
    if (StringUtils.isBlank(profileId)) {
      throw new BusinessException("profileId 不能为空");
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(profileId.getBytes(StandardCharsets.UTF_8));
  }
}
