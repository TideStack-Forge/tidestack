package com.ouroboros.security.authority.accesscontrol;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ouroboros.util.Maps;

/**
 * @author liansz
 */
public interface AccessControlParams {

  /**
   * 获取字段列表
   *
   * @return
   */
  List<String> getAllowedFields();

  List<String> getDisallowedFields();

  /**
   * 获取过滤器
   *
   * @return
   */
  Map<String, Object> getFilters();

  default FieldConstraint getFieldConstraint() {
    var allowed = getAllowedFields();
    return allowed.contains("*")
        ? new FieldConstraint(FieldConstraint.Mode.ALL, java.util.Set.of(), java.util.Set.copyOf(getDisallowedFields()))
        : new FieldConstraint(FieldConstraint.Mode.ONLY, java.util.Set.copyOf(allowed), java.util.Set.copyOf(getDisallowedFields()));
  }

  default RowConstraint getRowConstraint() {
    var filters = getFilters();
    return filters.isEmpty() ? RowConstraint.all() : RowConstraint.filter(filters);
  }

  static AccessControlParams or(AccessControlParams paramsA, AccessControlParams paramsB) {
    return new CombinedAccessControlParams(paramsA, paramsB, "OR");
  }

  static AccessControlParams and(AccessControlParams paramsA, AccessControlParams paramsB) {
    return new CombinedAccessControlParams(paramsA, paramsB, "AND");
  }

  static AccessControlParams merge(AccessControlParams paramsA, AccessControlParams paramsB) {
    return new MergedAccessControlParams(paramsA, paramsB);
  }

  static AccessControlParams empty() {
    return EmptyAccessControlParams.INSTANCE;
  }

  class CombinedAccessControlParams implements AccessControlParams {
    private final ResolvedAccessControlParams resolved;

    public CombinedAccessControlParams(AccessControlParams a, AccessControlParams b, String operator) {
      if (!"AND".equals(operator) && !"OR".equals(operator)) {
        throw new IllegalArgumentException("不支持的权限组合运算");
      }
      boolean intersection = "AND".equals(operator);
      resolved = new ResolvedAccessControlParams(
          intersection ? a.getFieldConstraint().and(b.getFieldConstraint()) : a.getFieldConstraint().or(b.getFieldConstraint()),
          intersection ? a.getRowConstraint().and(b.getRowConstraint()) : a.getRowConstraint().or(b.getRowConstraint()));
    }

    @Override
    public FieldConstraint getFieldConstraint() { return resolved.getFieldConstraint(); }

    @Override
    public RowConstraint getRowConstraint() { return resolved.getRowConstraint(); }

    @Override
    public List<String> getAllowedFields() { return resolved.getAllowedFields(); }

    @Override
    public List<String> getDisallowedFields() { return resolved.getDisallowedFields(); }

    @Override
    public Map<String, Object> getFilters() { return resolved.getFilters(); }
  }

  class MergedAccessControlParams implements AccessControlParams {
    private final List<String> allowedFields;
    private final List<String> disallowedFields;
    private final Map<String, Object> filters;

    public MergedAccessControlParams(AccessControlParams paramsA, AccessControlParams paramsB) {
      this.allowedFields = Stream.concat(paramsA.getAllowedFields().stream(), paramsB.getAllowedFields().stream())
          .distinct()
          .collect(Collectors.toList());
      this.disallowedFields = Stream.concat(paramsA.getDisallowedFields().stream(), paramsB.getDisallowedFields().stream())
          .filter(field -> !allowedFields.contains(field))
          .distinct()
          .collect(Collectors.toList());
      this.filters = Maps.deepMerge(paramsA.getFilters(), paramsB.getFilters());
    }

    @Override
    public List<String> getAllowedFields() {
      return allowedFields;
    }

    @Override
    public List<String> getDisallowedFields() {
      return disallowedFields;
    }

    @Override
    public Map<String, Object> getFilters() {
      return filters;
    }
  }

  /**
   * @author liansz
     */
  class EmptyAccessControlParams implements AccessControlParams {
    static final EmptyAccessControlParams INSTANCE = new EmptyAccessControlParams();

    @Override
    public FieldConstraint getFieldConstraint() { return FieldConstraint.none(); }

    @Override
    public RowConstraint getRowConstraint() { return RowConstraint.none(); }

    @Override
    public List<String> getAllowedFields() {
      throw new com.ouroboros.security.exception.AuthorizationDeniedException("没有可访问的业务字段");
    }

    @Override
    public List<String> getDisallowedFields() {
      throw new com.ouroboros.security.exception.AuthorizationDeniedException("没有可访问的业务字段");
    }

    @Override
    public Map<String, Object> getFilters() {
      throw new com.ouroboros.security.exception.AuthorizationDeniedException("没有可访问的数据行");
    }
  }
}
