package com.ouroboros.businessmodel.projection.authority;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.BusinessModelRelation;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.model.BusinessModelAuthority;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;

public abstract class AbstractAuthorityMPS extends AbstractDefaultBusinessModelProcessStep {

  @SuppressWarnings("unchecked")
  // TODO: 该机制需要更新设计，采用钩子的方式，而不是通过这种过耦合的方式
  protected Authority createAuthority(ModelProjectionPipelineContext<DefaultBusinessModel> context, String pathSuffix, String titleSuffix,
                                      Map<String, Object> accessControlMap,
                                      BiConsumer<BusinessModelAuthority, String> authoritySetter) {
    final var model = context.getSourceModel();
    final var fullName = model.getFullName() + pathSuffix;
    final var title = !titleSuffix.isEmpty() ? model.getTitle() + " - " + titleSuffix : model.getTitle();
    final var authority = new BusinessModelAuthority(fullName, title);

    // Delegation is an explicit capability of the original permission definition.
    authority.setDelegable(accessControlMap != null && Boolean.TRUE.equals(accessControlMap.get("delegable")));

    // Delegated grants need the same complete field definition even without configurable role filters.
    boolean controlled = accessControlMap != null && Boolean.TRUE.equals(accessControlMap.get("isEnabled"));
    if (controlled || authority.isDelegable()) {
      // 列级数据权限
      {
        // 可能需要受控的字段
        var controlledFields = Stream.concat(model.getFields()
                    .stream()
                    .filter(f -> !f.getIsPrimaryKey())
                    .map(this::buildField),
                model.getRelations().stream()
                    .filter(r -> Arrays.asList("master", "reference", "oneToOne", "manyToOne").contains(r.getType()))
                    .map(this::buildField))
            .collect(Collectors.toList());
        // 公共字段
        var commonFields = context.getModel(DataModelMeta.class, model.getFullName())
            .map(dataModelMeta -> dataModelMeta.getFields()
                .stream()
                .filter(fm -> !"Collection".equalsIgnoreCase(fm.getType()))
                .map(this::buildField)
                .filter(field -> !controlledFields.contains(field))
                .collect(Collectors.toList()))
            .orElseGet(Collections::emptyList);
        // 如果启用了字段控制，则添加受控字段
        if (controlled && Boolean.TRUE.equals(accessControlMap.get("fieldControl"))) {
          authority.setACCanControlFields(true);
          authority.addACControlledFields(controlledFields);
          authority.addACCommonFields(commonFields);
        }
        // 否则全部添加为公共字段
        else {
          authority.addACCommonFields(controlledFields);
          authority.addACCommonFields(commonFields);
        }
      }

      // 行级数据权限
      {
        Map<String, Boolean> fieldFilterMap = controlled
            ? (Map<String, Boolean>) accessControlMap.getOrDefault("filters", Collections.emptyMap()) : Collections.emptyMap();

        // 处理内置字段
        fieldFilterMap.forEach((filterName, filterEnabled) -> {
          if (!filterEnabled) {
            return;
          }
          // 内置字段
          if (filterName.startsWith("$")) {
            var define = buildPresetACFilterDefine(model.getFullName(), filterName);
            if (define != null) {
              authority.addACFilterDefine(define);
            }
            return;
          }
          // 自定义字段
          model.getField(filterName)
              .map(this::buildFieldACFilterDefine)
              .ifPresent(authority::addACFilterDefine);
        });
      }
    }

    authoritySetter.accept(authority, model.getFullName());
    return authority;
  }

  protected String buildWebApiPath(String method, String fullName, String suffix) {
    return method
        + " /api"
        + BusinessModelUtil.buildKebabCasePath(fullName)
        + (ObjectUtils.isEmpty(suffix) ? "" : "/" + suffix);
  }

  protected String buildUiViewPath(String fullName, String suffix) {
    return BusinessModelUtil.buildKebabCasePath(fullName)
        + (ObjectUtils.isEmpty(suffix) ? "" : "/" + suffix);
  }

  /**
   * 构建内部字段的访问控制字段定义
   *
   * @param filterName 过滤器名称
   * @return 访问控制字段定义
   */
  @SuppressWarnings("EnhancedSwitchMigration")
  private BusinessModelACFilterDefine buildPresetACFilterDefine(String modelName, String filterName) {
    switch (filterName) {
      case "$byEmployee":
        return new BusinessModelACFilterDefine(filterName, "按创建员工", "DataModel", modelName + ".createdByEmployee");

      case "$byDepartment":
        return new BusinessModelACFilterDefine(filterName, "按创建部门", "DataModel", modelName + ".createdByCompany");

      case "$byCompany":
        return new BusinessModelACFilterDefine(filterName, "按创建公司", "DataModel", modelName + ".createdByCompany");
      default:
        return null;
    }
  }

  private BusinessModelACFilterDefine buildFieldACFilterDefine(BusinessModelField field) {
    return new BusinessModelACFilterDefine(field.getName(), field.getTitle(), "BusinessModel", field.getModel().getFullName() + "." + field.getName());
  }

  private AccessControlDefine.Field buildField(BusinessModelField field) {
    return new AccessControlDefine.Field(field.getName(), field.getTitle(), field.getType(), field.getDescription());
  }

  private AccessControlDefine.Field buildField(BusinessModelRelation relation) {
    return new AccessControlDefine.Field(relation.getName(), relation.getTitle(), relation.getType(), relation.getDescription());
  }

  private AccessControlDefine.Field buildField(DataModelFieldMeta fieldMeta) {
    return new AccessControlDefine.Field(fieldMeta.getName(), fieldMeta.getLabel(), fieldMeta.getType(), fieldMeta.getDescription());
  }

  public static class ModelField {
    private final String label;
    private final String value;

    public ModelField(BusinessModelField field) {
      this.label = field.getTitle();
      this.value = field.getName();
    }

    public ModelField(BusinessModelRelation relation) {
      this.label = relation.getTitle();
      this.value = relation.getName();
    }

    public ModelField(DataModelFieldMeta fieldMeta) {
      this.label = fieldMeta.getLabel();
      this.value = fieldMeta.getName();
    }

    public String getLabel() {
      return label;
    }

    public String getValue() {
      return value;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (obj == this) {
        return true;
      }
      if (obj instanceof ModelField other) {
        return Objects.equals(this.value, other.value);
      }
      return false;
    }

    public Map<String, String> toMap() {
      var map = new HashMap<String, String>();
      map.put("label", label);
      map.put("value", value);
      return map;
    }
  }
}
