package com.ouroboros.security.authority;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;

/**
 * @author liansz
 */
public class MergedAuthority implements Authority {

  private final Authority a;

  private final Authority b;

  public MergedAuthority(Authority a, Authority b) {
    this.a = a;
    this.b = b;
  }

  @Override
  public String getFullName() {
    return StringUtils.isNotEmpty(a.getFullName()) ? a.getFullName() : b.getFullName();
  }

  @Override
  public String getTitle() {
    return StringUtils.isNotEmpty(a.getTitle()) ? a.getTitle() : b.getTitle();
  }

  @Override
  public boolean isDelegable() { return a.isDelegable() && b.isDelegable(); }


  @Override
  public AccessControlDefine getAccessControlDefine() {
    return AccessControlDefine.merge(a.getAccessControlDefine(), b.getAccessControlDefine());
  }

  @Override
  public List<String> getApis() {
    return mergeList(a.getApis(), b.getApis());
  }

  @Override
  public List<String> getUiViews() {
    return mergeList(a.getUiViews(), b.getUiViews());
  }

  @Override
  public List<String> getMenuItems() {
    return mergeList(a.getMenuItems(), b.getMenuItems());
  }

  private List<String> mergeList(Collection<String> a, Collection<String> b) {
    return Stream.of(a, b).filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toList());
  }
}
