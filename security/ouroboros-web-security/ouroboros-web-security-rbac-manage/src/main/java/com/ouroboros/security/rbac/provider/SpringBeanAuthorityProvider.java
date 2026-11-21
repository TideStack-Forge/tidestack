package com.ouroboros.security.rbac.provider;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;

/**
 * @author liansz
 */
@Component
public class SpringBeanAuthorityProvider {

  @Autowired(required = false)
  private List<AuthorityProvider> authorityProviders;

  public SpringBeanAuthorityProvider() {
    SingletonHolder.AUTHORITY_PROVIDER = this;
  }

  public List<Authority> getAuthority(String fullName) {
    if (ObjectUtils.isEmpty(authorityProviders)) {
      return Collections.emptyList();
    }

    return authorityProviders.stream()
        .flatMap(provider -> provider.getAuthority(fullName).stream())
        .collect(Collectors.toList());
  }

  public List<Authority> getAllAuthority() {
    if (ObjectUtils.isEmpty(authorityProviders)) {
      return Collections.emptyList();
    }

    return authorityProviders.stream()
        .flatMap(provider -> provider.getAllAuthorities().stream())
        .collect(Collectors.toList());
  }

  /**
   * 代理Spring Bean中的AuthorityProvider
   */
  public static class SingletonHolder implements AuthorityProvider {

    private static SpringBeanAuthorityProvider AUTHORITY_PROVIDER;

    @Override
    public List<Authority> getAuthority(String fullName) {
      if (ObjectUtils.isEmpty(AUTHORITY_PROVIDER)) {
        return Collections.emptyList();
      }

      return AUTHORITY_PROVIDER.getAuthority(fullName);
    }

    @Override
    public List<Authority> getAllAuthorities() {
      if (ObjectUtils.isEmpty(AUTHORITY_PROVIDER)) {
        return Collections.emptyList();
      }

      return AUTHORITY_PROVIDER.getAllAuthority();
    }
  }
}
