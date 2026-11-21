package com.ouroboros.businessmodel.data.decorators;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.DataModelMetaDecorator;
import com.ouroboros.data.model.PluginDescriptor;

/**
 * 权限字段装饰器
 * <p>
 * 根据访问控制配置自动添加权限相关字段和插件
 * 支持按员工、部门、公司等维度的权限控制
 */
public class AuthorityFieldsDecorator implements DataModelMetaDecorator {

  @Override
  public boolean supports(DataModelMeta originalMeta) {
    // 检查是否配置了访问控制
    return originalMeta.getExtraProps() != null &&
        originalMeta.getExtraProps().containsKey("accessControl") &&
        originalMeta.getExtraProps().get("accessControl") instanceof Map;
  }

  @Override
  public DataModelMeta decorate(DataModelMeta originalMeta) {
    // 创建新的元数据副本
    DataModelMeta decoratedMeta = originalMeta.deepCopy();

    if (!(originalMeta.getExtraProps().get("accessControl") instanceof Map<?, ?> accessControlMap)) {
      return decoratedMeta;
    }

    // 获取需要的权限过滤器
    List<String> filterNames = getAuthorityFilterNames(accessControlMap);

    if (!filterNames.isEmpty()) {
      // 添加权限字段
      List<DataModelFieldMeta> newFields = new ArrayList<>(decoratedMeta.getFields());
      newFields.addAll(buildAuthorityFields(filterNames));
      decoratedMeta.setFields(newFields);

    }

    // Every enabled operation needs the same authorization plugin, including field-only controls.
    if (Stream.of("add", "update", "delete", "search").anyMatch(operation -> enabled(accessControlMap, operation))) {
      List<PluginDescriptor> plugins = new ArrayList<>(decoratedMeta.getPluginDescriptors());
      plugins.addAll(buildAuthorityPlugins(filterNames));
      plugins.add(buildAccessControlPlugin(decoratedMeta.getFullName(), accessControlMap));
      decoratedMeta.setPluginDescriptors(plugins);
    }

    return decoratedMeta;
  }

  /**
   * 获取权限过滤器名称列表
   */
  private List<String> getAuthorityFilterNames(Map<?, ?> accessControlMap) {
    return Stream.of("add", "update", "delete", "search")
        .filter(accessControlMap::containsKey)
        .map(accessControlMap::get)
        .filter(Map.class::isInstance)
        .map(value -> (Map<?, ?>) value)
        .filter(accessControl -> Boolean.TRUE.equals(accessControl.get("isEnabled")))
        .map(accessControl -> accessControl.get("filters"))
        .filter(Map.class::isInstance)
        .map(value -> (Map<?, ?>) value)
        .flatMap(filtersMap -> filtersMap.entrySet().stream())
        .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
        .map(entry -> entry.getKey().toString())
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * 构建权限字段
   */
  private List<DataModelFieldMeta> buildAuthorityFields(List<String> filterNames) {
    List<DataModelFieldMeta> fields = new ArrayList<>();

    for (String filterName : filterNames) {
      DataModelFieldMeta field = buildAuthorityField(filterName);
      if (field != null) {
        fields.add(field);
      }
    }

    return fields;
  }

  /**
   * 根据过滤器名称构建权限字段
   */
  private DataModelFieldMeta buildAuthorityField(String filterName) {
    switch (filterName) {
      case "$byEmployee":
        return createField("createdByEmployee","创建员工", "Employee");
      case "$byDepartment":
        return createField("createdByDepartment", "创建部门", "Organization");
      case "$byCompany":
        return createField("createdByCompany", "创建公司", "Organization");
      default:
        // 未知的过滤器类型，返回null
        return null;
    }
  }

  /**
   * 创建字段元数据
   */
  private DataModelFieldMeta createField(String name, String label, String type) {
    DataModelFieldMeta field = new DataModelFieldMeta();
    field.setName(name);
    field.setLabel(label);
    field.setType(type);
    field.setIsNullable(true);
    return field;
  }

  /**
   * 构建权限插件
   */
  private List<PluginDescriptor> buildAuthorityPlugins(List<String> filterNames) {
    List<PluginDescriptor> plugins = new ArrayList<>();

    for (String filterName : filterNames) {
      PluginDescriptor plugin = buildAuthorityPlugin(filterName);
      if (plugin != null) {
        plugins.add(plugin);
      }
    }

    return plugins;
  }

  /**
   * 根据过滤器名称构建权限插件
   */
  private PluginDescriptor buildAuthorityPlugin(String filterName) {
    switch (filterName) {
      case "$byEmployee":
        return new PluginDescriptor("EmployeeSetter");
      case "$byDepartment":
        return new PluginDescriptor("DepartmentSetter");
      case "$byCompany":
        return new PluginDescriptor("CompanySetter");
      default:
        // 未知的过滤器类型，返回null
        return null;
    }
  }

  /**
   * 构建访问控制插件
   */
  private boolean enabled(Map<?, ?> config, String operation) {
    return config.get(operation) instanceof Map<?, ?> setting
        && (Boolean.TRUE.equals(setting.get("isEnabled")) || Boolean.TRUE.equals(setting.get("delegable")));
  }

  private PluginDescriptor buildAccessControlPlugin(String dataModelName, Map<?, ?> accessControl) {
    String authorityPrefix = dataModelName + ".";
    Map<String, Object> config = new HashMap<>();
    if (enabled(accessControl, "add")) { config.put("createAuthority", authorityPrefix + "add"); }
    if (enabled(accessControl, "delete")) { config.put("deleteAuthority", authorityPrefix + "delete"); }
    if (enabled(accessControl, "update")) { config.put("updateAuthority", authorityPrefix + "edit"); }
    if (enabled(accessControl, "search")) { config.put("readAuthority", authorityPrefix + "search"); }

    return new PluginDescriptor("AccessControl", config);
  }
}
