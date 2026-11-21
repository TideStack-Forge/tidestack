package com.ouroboros.businessmodel.projection.data.basic;

import java.util.Collections;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.fieldtype.BusinessModelFieldTypeSupport;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;

@Priority(1)
public class PrimaryKeyMPS extends AbstractDataMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    final var primaryKeyField = model.getFields().stream()
        .filter(BusinessModelField::getIsPrimaryKey)
        .findFirst();
    primaryKeyField.ifPresent(field -> {
      modifyMainDataModel(context, dataModel -> {
        // 将主键信息保存到字段的 extraProps 中，由 PrimaryKeyDecorator 处理
        dataModel.getFields().stream()
            .filter(f -> f.getName().equals(field.getName()))
            .findFirst()
            .ifPresent(f -> {
              // 标记为主键
              f.setExtraProp("isPrimaryKey", true);

              // 获取主键生成器配置
              var primaryKeyGenerator = BusinessModelFieldTypeSupport.compileRuntime(field.getType(), field.getExtraProps())
                  .map(compiledField -> compiledField.getPrimaryKeyGenerator());

              if (primaryKeyGenerator.isPresent()) {
                String generator = primaryKeyGenerator.get();
                if ("autoIncrement".equalsIgnoreCase(generator)) {
                  f.setIsAutoIncrement(true);
                } else {
                  // 保存其他类型的主键生成器
                  f.setExtraProp("primaryKeyGenerator", generator);
                }
              }
            });
      });
    });
  }
}
