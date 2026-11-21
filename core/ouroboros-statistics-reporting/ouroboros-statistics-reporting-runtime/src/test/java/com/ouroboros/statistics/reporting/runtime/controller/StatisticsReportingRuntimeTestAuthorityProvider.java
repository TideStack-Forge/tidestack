package com.ouroboros.statistics.reporting.runtime.controller;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.security.authority.DefaultAuthority;
import com.ouroboros.util.Json;
import com.ouroboros.util.ResourceUtil;

public class StatisticsReportingRuntimeTestAuthorityProvider implements AuthorityProvider {

  private static final String AUTHORITY_DIRECTORY = "META-INF/ouroboros/metadata/";
  private static final Pattern AUTHORITY_FILE_PATTERN = Pattern.compile(".*\\.authority\\.json");

  private final List<Authority> authorities = ResourceUtil.getResourceNameAndContentListFromDirectory(
      AUTHORITY_DIRECTORY,
      AUTHORITY_FILE_PATTERN).stream()
      .map(tuple -> Json.toBean(DefaultAuthority.class, tuple._2))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

  @Override
  public List<Authority> getAuthority(String fullName) {
    return authorities.stream()
        .filter(authority -> Objects.equals(authority.getFullName(), fullName))
        .collect(Collectors.toList());
  }

  @Override
  public List<Authority> getAllAuthorities() {
    return authorities;
  }
}
