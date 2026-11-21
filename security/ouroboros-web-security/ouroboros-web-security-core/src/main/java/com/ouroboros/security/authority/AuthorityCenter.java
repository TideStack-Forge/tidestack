package com.ouroboros.security.authority;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.util.SPI;

/**
 * @author liansz
 */
public interface AuthorityCenter {

  /**
   * 获取授权
   *
   * @param fullName
   * @return
   */
  public static Optional<Authority> getAuthority(String fullName) {
    return SPI.getSortedServiceStream(AuthorityProvider.class)
        .flatMap(provider -> provider.getAuthority(fullName).stream())
        .filter(Objects::nonNull)
        .reduce(MergedAuthority::new);
  }

  /**
   * 获取全部授权
   *
   * @return
   */
  public static Map<String, Authority> getAllAuthority() {
    return SPI.getSortedServiceStream(AuthorityProvider.class)
        .flatMap(provider -> provider.getAllAuthorities().stream())
        .filter(Objects::nonNull)
        .map(DefaultAuthority::new)
        .collect(Collectors.toMap(Authority::getFullName, authority -> authority, MergedAuthority::new));
  }
}
