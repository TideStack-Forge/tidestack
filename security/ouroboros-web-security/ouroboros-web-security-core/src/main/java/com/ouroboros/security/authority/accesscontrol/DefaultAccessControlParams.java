package com.ouroboros.security.authority.accesscontrol;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ouroboros.security.authority.accesscontrol.filter.AccessControlFilterBuilder;
import com.ouroboros.util.Maps;
import com.ouroboros.util.SPI;

/**
 * @author liansz
 */
public class DefaultAccessControlParams implements AccessControlParams {
  private final static List<AccessControlFilterBuilder> FILTER_BUILDERS = SPI
      .getCachedSortedServiceStream(AccessControlFilterBuilder.class)
      .collect(Collectors.toList());
  private final AccessControlDefine acDefine;
  private final Map<String, Object> setting;

  public DefaultAccessControlParams(AccessControlDefine acDefine, Map<String, Object> setting) {
    this.acDefine = acDefine;
    this.setting = setting == null ? Map.of() : setting;
  }

  @Override
  public List<String> getAllowedFields() {
    //$fieldControl
    if (!acDefine.getCanControlFields()) {
      return Collections.singletonList("*");
    }

    // 无需控制的字段列表
    List<String> noControlFields = acDefine.getCommonFields().stream()
        .map(AccessControlDefine.Field::getName)
        .collect(Collectors.toList());
    // 受控字段列表
    List<String> controlledFields = acDefine.getControlledFields().stream()
        .map(AccessControlDefine.Field::getName)
        .collect(Collectors.toList());

    if (controlledFields.isEmpty() && noControlFields.isEmpty()) {
      return Collections.singletonList("*");
    }

    var configFields = Optional.ofNullable(setting.get("$fieldControl"))
        .filter(List.class::isInstance)
        .map(l -> (List<?>) l)
        .orElseGet(Collections::emptyList)
        .stream()
        .filter(Objects::nonNull)
        .map(Object::toString)
        .collect(Collectors.toList());

    if (configFields.isEmpty() && !acDefine.isFieldSelectionRequired()) {
      return Collections.singletonList("*");
    }

    return Stream.concat(configFields.stream().filter(controlledFields::contains),
            noControlFields.stream())
        .collect(Collectors.toList());
  }

  @Override
  public List<String> getDisallowedFields() {
    if (!acDefine.getCanControlFields()) {
      return Collections.emptyList();
    }
    if (acDefine.getControlledFields().isEmpty()) {
      return Collections.emptyList();
    }
    var allowedFields = getAllowedFields();
    if (allowedFields.contains("*")) {
      return Collections.emptyList();
    }
    return acDefine.getControlledFields().stream()
        .map(AccessControlDefine.Field::getName)
        .filter(name -> !allowedFields.contains(name))
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, Object> getFilters() {
    var rows = getRowConstraint();
    if (rows.mode() == RowConstraint.Mode.NONE) {
      throw new com.ouroboros.security.exception.AuthorizationDeniedException("没有可访问的数据行");
    }
    return rows.condition();
  }

  @Override
  public RowConstraint getRowConstraint() {
    var clonedParams = Maps.deepClone(setting);
    clonedParams.remove("$fieldControl");
    if (clonedParams.isEmpty()) {
      return RowConstraint.all();
    }
    RowConstraint combined = RowConstraint.all();
    for (var builder : FILTER_BUILDERS) {
      combined = combined.and(builder.resolve(clonedParams, acDefine));
    }
    if (!clonedParams.isEmpty()) {
      throw new com.ouroboros.security.exception.AuthorizationDeniedException("访问控制参数缺少有效解析器: " + clonedParams.keySet());
    }
    return combined;
  }
}
