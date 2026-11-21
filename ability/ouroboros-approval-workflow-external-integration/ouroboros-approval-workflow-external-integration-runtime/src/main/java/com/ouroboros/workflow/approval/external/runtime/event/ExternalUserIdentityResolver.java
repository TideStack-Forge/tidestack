package com.ouroboros.workflow.approval.external.runtime.event;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;

/**
 * 入站身份解析：聚合全部 {@link ExternalUserIdentityMapper} Bean，按注入顺序首个命中者胜。
 * 解析失败返回空，调用方不得伪造平台用户。
 */
@Component
public class ExternalUserIdentityResolver {

  private final List<ExternalUserIdentityMapper> mappers;

  @Autowired
  public ExternalUserIdentityResolver(
      @Autowired(required = false) List<ExternalUserIdentityMapper> mappers) {
    this.mappers = mappers == null ? List.of() : List.copyOf(mappers);
  }

  public Optional<String> toPlatformUser(String externalUserId, String adapterId, String connectionId) {
    if (externalUserId == null || externalUserId.isBlank()) {
      return Optional.empty();
    }
    for (ExternalUserIdentityMapper mapper : mappers) {
      Optional<String> mapped = mapper.toPlatformUser(externalUserId, adapterId, connectionId);
      if (mapped.isPresent()) {
        return mapped;
      }
    }
    return Optional.empty();
  }
}
