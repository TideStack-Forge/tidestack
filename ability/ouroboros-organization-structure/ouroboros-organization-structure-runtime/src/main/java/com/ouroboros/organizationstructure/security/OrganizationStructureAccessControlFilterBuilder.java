package com.ouroboros.organizationstructure.security;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.organizationstructure.*;
import com.ouroboros.organizationstructure.context.OrganizationContext;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.organizationstructure.enums.OrganizationNodeType;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.RowConstraint;
import com.ouroboros.security.exception.AuthorizationDeniedException;
import com.ouroboros.security.authority.accesscontrol.filter.AccessControlFilterBuilder;
import com.ouroboros.security.authority.accesscontrol.filter.RowScopeResolver;

/**
 * @author liansz
 */
@Order(0)
@Component
public class OrganizationStructureAccessControlFilterBuilder {

  @Autowired
  private OrganizationAggregator organizationAggregator;

  public OrganizationStructureAccessControlFilterBuilder() {
    SingletonHolder.INSTANCE = this;
  }

  public Map<String, Object> build(Map<String, Object> params, AccessControlDefine define) {
    var rows = resolve(params, define);
    if (rows.mode() == RowConstraint.Mode.NONE) {
      throw new AuthorizationDeniedException("当前组织上下文没有可访问的数据行");
    }
    return rows.condition();
  }

  public RowConstraint resolve(Map<String, Object> params, AccessControlDefine define) {
    RowConstraint combined = RowConstraint.all();
    Set<String> names = define.getFilterDefine().stream().map(filter -> filter.getName()).collect(Collectors.toSet());
    if (names.contains("$byEmployee")) {
      combined = combined.and(resolveDimension(params, "$byEmployee", "createdByEmployee", "currentEmployee", null,
          OrganizationContextHolder.getContext().flatMap(OrganizationContext::getEmployee).map(Employee::getId), null));
    }
    if (names.contains("$byDepartment")) {
      combined = combined.and(resolveDimension(params, "$byDepartment", "createdByDepartment", "currentDepartment", "currentChildrenDepartment",
          OrganizationContextHolder.getContext().flatMap(OrganizationContext::getDepartment).map(Department::getId), OrganizationNodeType.DEPARTMENT));
    }
    if (names.contains("$byCompany")) {
      combined = combined.and(resolveDimension(params, "$byCompany", "createdByCompany", "currentCompany", "currentChildrenCompany",
          OrganizationContextHolder.getContext().flatMap(OrganizationContext::getCompany).map(Company::getId), OrganizationNodeType.COMPANY));
    }
    return combined;
  }

  private RowConstraint resolveDimension(Map<String, Object> params, String key, String field, String currentFlag,
      String childrenFlag, Optional<String> currentId, OrganizationNodeType type) {
    Object selection = params.remove(key);
    boolean requested = false;
    Set<String> ids = new LinkedHashSet<>();
    if (selection != null) {
      if (!(selection instanceof Map<?, ?> options)) { throw new AuthorizationDeniedException("组织范围格式无效"); }
      Set<String> supported = childrenFlag == null ? Set.of(currentFlag) : Set.of(currentFlag, childrenFlag);
      if (options.entrySet().stream().anyMatch(entry -> !supported.contains(entry.getKey()) || !(entry.getValue() instanceof Boolean))) {
        throw new AuthorizationDeniedException("组织范围选项无效");
      }
      if (Boolean.TRUE.equals(options.get(currentFlag))) {
        requested = true;
        currentId.ifPresent(ids::add);
      }
      if (childrenFlag != null && Boolean.TRUE.equals(options.get(childrenFlag))) {
        requested = true;
        currentId.ifPresent(id -> organizationAggregator.getDescendants(id).stream()
            .filter(node -> type.getType().equalsIgnoreCase(node.getType())).map(OrganizationNode::getId).forEach(ids::add));
      }
    }
    if (params.containsKey(field)) {
      Object custom = params.remove(field);
      if (!(custom instanceof List<?> values) || values.stream().anyMatch(value -> !(value instanceof String id) || id.isBlank())) {
        throw new AuthorizationDeniedException("组织范围必须为明确标识列表");
      }
      requested |= !values.isEmpty();
      values.stream().map(String.class::cast).forEach(ids::add);
    }
    if (!requested) { return RowConstraint.all(); }
    return ids.isEmpty() ? RowConstraint.none() : RowConstraint.filter(Map.of(field, List.copyOf(ids)));
  }

  public static class SingletonHolder implements AccessControlFilterBuilder, RowScopeResolver {
    private static OrganizationStructureAccessControlFilterBuilder INSTANCE;

    @Override
    public Map<String, Object> build(Map<String, Object> params, AccessControlDefine acDefine) {
      return INSTANCE.build(params, acDefine);
    }

    @Override
    public RowConstraint resolve(Map<String, Object> params, AccessControlDefine define) {
      if (INSTANCE == null) { throw new AuthorizationDeniedException("组织范围解析器未就绪"); }
      return INSTANCE.resolve(params, define);
    }
  }
}
