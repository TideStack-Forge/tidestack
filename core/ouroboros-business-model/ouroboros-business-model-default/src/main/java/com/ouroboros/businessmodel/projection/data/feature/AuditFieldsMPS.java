package com.ouroboros.businessmodel.projection.data.feature;

import java.util.HashMap;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.config.BasicAuditFieldsConfiguration;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;

/**
 * 为业务模型添加审计字段
 */
@Priority(4)
public class AuditFieldsMPS extends AbstractDataMPS {
  public static com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlan prepare(com.ouroboros.businessmodel.meta.BusinessModelMeta meta) {
    return new com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlan("auditFields", java.util.List.of(), context -> { },
        "features.isEnabledAuditFields", isEnabled(meta)
            ? "实际写入本模型时按原审计插件维护创建人、更新人及时间；不另产生审计业务事件" : null);
  }

  private static boolean isEnabled(com.ouroboros.businessmodel.meta.BusinessModelMeta meta) {
    return meta.getExtraPropDeep(Boolean.class, "features", "isEnabledAuditFields").orElse(false);
  }

  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    if (!isEnabled(context.getSourceModel().getMeta())) {
      return;
    }
    var auditFieldsConfiguration = BasicAuditFieldsConfiguration.getConfiguration();

    // 将审计字段配置保存到数据模型的 extraProps 中，供装饰器使用
    modifyMainDataModel(context, dataModel -> {
      dataModel.setExtraProp("enableAuditFields", true);
      var auditFieldConfig = new HashMap<String, Object>();
      var createdAtRawName = auditFieldsConfiguration.getCreatedAt().getRawName();
      var createdByRawName = auditFieldsConfiguration.getCreatedBy().getRawName();
      var updatedAtRawName = auditFieldsConfiguration.getUpdatedAt().getRawName();
      var updatedByRawName = auditFieldsConfiguration.getUpdatedBy().getRawName();
      if (StringUtils.isNotEmpty(createdAtRawName)) {
        auditFieldConfig.put("createdAtRawName", createdAtRawName);
      }
      if (StringUtils.isNotEmpty(createdByRawName)) {
        auditFieldConfig.put("createdByRawName", createdByRawName);
      }
      if (StringUtils.isNotEmpty(updatedAtRawName)) {
        auditFieldConfig.put("updatedAtRawName", updatedAtRawName);
      }
      if (StringUtils.isNotEmpty(updatedByRawName)) {
        auditFieldConfig.put("updatedByRawName", updatedByRawName);
      }
      dataModel.setExtraProp("basicAuditConfig", auditFieldConfig);
    });

    // 注意：字段和插件的添加现在完全由 AuditFieldsDecorator 处理
  }

}
