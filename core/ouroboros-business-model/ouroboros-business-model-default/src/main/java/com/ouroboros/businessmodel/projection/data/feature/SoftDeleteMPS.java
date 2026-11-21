package com.ouroboros.businessmodel.projection.data.feature;

import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.config.BasicAuditFieldsConfiguration;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;

/**
 * 为业务模型添加软删除标记
 */
@Priority(5)
public class SoftDeleteMPS extends AbstractDataMPS {

  @Override
  /**
   * 处理模型投影管道的上下文方法
   * @param context 模型投影管道的上下文，包含 DefaultBusinessModel 类型的数据
   */
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    // 获取基础审计字段的配置
    var config = BasicAuditFieldsConfiguration.getConfiguration();
    // 从源模型的额外属性中获取软删除功能是否启用的配置
    if (!com.ouroboros.businessmodel.plugins.SoftDeleteFeaturePlugin.isEnabled(context.getSourceModel().getMeta())) {
      return;
    }

    // 将软删除配置保存到数据模型的 extraProps 中，供装饰器使用
    modifyMainDataModel(context, dataModel -> {
      dataModel.setExtraProp("enableSoftDelete", true);
      // 创建软删除配置映射
      Map<String, Object> softDeleteConfig = new HashMap<>();
      if (StringUtils.isNotEmpty(config.getIsDeleted().getRawName())) {
        softDeleteConfig.put("isDeletedRawName", config.getIsDeleted().getRawName());
      }
      if (config.getDeletedBy().isDisabled()) {
        softDeleteConfig.put("deletedByDisabled", true);
      } else if (StringUtils.isNotEmpty(config.getDeletedBy().getRawName())) {
        softDeleteConfig.put("deletedByDisabled", false);
        softDeleteConfig.put("deletedByRawName", config.getDeletedBy().getRawName());
      }

      if (config.getDeletedAt().isDisabled()) {
        softDeleteConfig.put("deletedAtDisabled", true);
      } else if (StringUtils.isNotEmpty(config.getDeletedAt().getRawName())) {
        softDeleteConfig.put("deletedAtDisabled", false);
        softDeleteConfig.put("deletedAtRawName", config.getDeletedAt().getRawName());
      }
      dataModel.setExtraProp("softDeleteConfig", softDeleteConfig);
    });
  }

}
