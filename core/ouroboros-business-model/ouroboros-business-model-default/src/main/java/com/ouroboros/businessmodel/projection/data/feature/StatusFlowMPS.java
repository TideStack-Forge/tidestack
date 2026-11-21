package com.ouroboros.businessmodel.projection.data.feature;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;
import com.ouroboros.data.model.DataModelFieldMeta;

/**
 * 为业务模型添加状态流相关字段
 */
@Priority(6)
public class StatusFlowMPS extends AbstractDataMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var isEnabledStatusFlow = context.getSourceModel().getExtraPropDeep(Boolean.class, "statusFlow", "isEnabled").orElse(false);
    if (!isEnabledStatusFlow) {
      return;
    }
    addFieldsToMainDataModel(context, getStatusFlowFieldMetas());
  }

  private List<DataModelFieldMeta> getStatusFlowFieldMetas() {
    // TODO: 优化命名
    final var fieldMetaList = new ArrayList<DataModelFieldMeta>();
    var statusFlowFieldMeta = new DataModelFieldMeta();
    statusFlowFieldMeta.setName("statusFlow");
    statusFlowFieldMeta.setLabel("状态");
    statusFlowFieldMeta.setType("String");
    fieldMetaList.add(statusFlowFieldMeta);
    var statusFlowHistoryFieldMeta = new DataModelFieldMeta();
    statusFlowHistoryFieldMeta.setName("statusFlowHistory");
    statusFlowHistoryFieldMeta.setLabel("状态流历史");
    statusFlowHistoryFieldMeta.setType("String");
    fieldMetaList.add(statusFlowHistoryFieldMeta);
    var statusFlowLastModifiedByFieldMeta = new DataModelFieldMeta();
    statusFlowLastModifiedByFieldMeta.setName("statusFlowLastModifiedBy");
    statusFlowLastModifiedByFieldMeta.setLabel("状态流最后修改人");
    statusFlowLastModifiedByFieldMeta.setType("String");
    fieldMetaList.add(statusFlowLastModifiedByFieldMeta);
    var statusFlowLastModifiedTimeFieldMeta = new DataModelFieldMeta();
    statusFlowLastModifiedTimeFieldMeta.setName("statusFlowLastModifiedTime");
    statusFlowLastModifiedTimeFieldMeta.setLabel("状态流最后修改时间");
    statusFlowLastModifiedTimeFieldMeta.setType("DateTime");
    fieldMetaList.add(statusFlowLastModifiedTimeFieldMeta);
    return fieldMetaList;
  }

}
