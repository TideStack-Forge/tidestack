package com.ouroboros.workflow.approval.external.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.external.ExternalUserIdentityMapper;

/**
 * M9 入站身份解析测试：首个命中者胜、全部未命中返回空。
 */
class ExternalUserIdentityResolverTest {

  @Test
  void firstMatchingMapperWins() {
    ExternalUserIdentityMapper first = mapper("platform-1");
    ExternalUserIdentityMapper second = mapper("platform-2");
    ExternalUserIdentityResolver resolver = new ExternalUserIdentityResolver(List.of(first, second));

    assertThat(resolver.toPlatformUser("ext-1", "adapter", "conn"))
        .contains("platform-1");
  }

  @Test
  void emptyResultWhenNoMapperMatches() {
    ExternalUserIdentityMapper first = mock(ExternalUserIdentityMapper.class);
    ExternalUserIdentityMapper second = mock(ExternalUserIdentityMapper.class);
    when(first.toPlatformUser("ext-1", "adapter", "conn")).thenReturn(Optional.empty());
    when(second.toPlatformUser("ext-1", "adapter", "conn")).thenReturn(Optional.empty());
    ExternalUserIdentityResolver resolver = new ExternalUserIdentityResolver(List.of(first, second));

    assertThat(resolver.toPlatformUser("ext-1", "adapter", "conn")).isEmpty();
  }

  @Test
  void emptyResultWithoutMappers() {
    ExternalUserIdentityResolver resolver = new ExternalUserIdentityResolver(List.of());

    assertThat(resolver.toPlatformUser("ext-1", "adapter", "conn")).isEmpty();
  }

  private ExternalUserIdentityMapper mapper(String platformUserId) {
    ExternalUserIdentityMapper mapper = mock(ExternalUserIdentityMapper.class);
    when(mapper.toPlatformUser(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Optional.empty());
    when(mapper.toPlatformUser("ext-1", "adapter", "conn")).thenReturn(Optional.of(platformUserId));
    return mapper;
  }
}
