package com.ouroboros.businessmodel.projection.data.basic;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.fieldtype.BusinessModelFieldTypeSupport;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.model.PluginDescriptor;

/**
 * 添加基本的数据模型
 */
@Priority(0)
public class BasicMPS extends AbstractDataMPS {
  final static Logger logger = LoggerFactory.getLogger(BasicMPS.class);

  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var modelName = model.getName();
    var modelNamespace = model.getNamespace();
    var modelTitle = model.getTitle();
    var modelDesc = model.getDescription();
    var modelFields = model.getFields();
    var migrationStrategy = model.getExtraPropDeep(String.class, "migrationStrategy").orElse("auto");
    var dataStation = model.getExtraPropDeep(String.class, "dataStation");

    var dataModelMeta = new DataModelMeta();

    if (modelFields.isEmpty()) {
      return;
    }

    dataModelMeta.setName(modelName);
    dataModelMeta.setNamespace(modelNamespace);
    dataModelMeta.setLabel(modelTitle);
    dataModelMeta.setDescription(modelDesc);
    dataModelMeta.setMigrationStrategy(MigrationStrategy.fromValue(migrationStrategy));
    dataModelMeta.setFields(mapFields(modelFields));
    dataModelMeta.setPluginDescriptors(buildBasicPluginDescriptor());
    dataStation.ifPresent(dataModelMeta::setDataStation);

    // RawName
    model.getExtraProp(String.class, "rawTableName")
        .filter(rawName -> !rawName.trim().isEmpty())
        .ifPresent(dataModelMeta::setRawName);

    context.putModel(DataModelMeta.class, model.getFullName(), dataModelMeta);
    context.setAttribute("hasDataModel", true);
  }

  public List<DataModelFieldMeta> mapFields(List<BusinessModelField> fields) {
    return fields.stream()
        .map(field -> {
          var fieldMeta = BusinessModelFieldTypeSupport.compileRuntime(field.getType(), field.getExtraProps())
              .map(compiledField -> compiledField.toDataFieldMeta())
              // TODO: 未知的字段类型，使用默认的 String 类型，将来如何处理？
              .orElseGet(() -> {
                logger.warn("将业务模型 {} 的 {} 字段映射为数据模型字段时，发现未知的字段类型：{}，已通过容错机制回退为 String 类型", field.getModel().getFullName(), field.getName(), field.getType());
                var fm = new DataModelFieldMeta();
                fm.setType("String");
                return fm;
              });
          fieldMeta.setName(field.getName());
          fieldMeta.setLabel(field.getTitle());
          fieldMeta.setDescription(field.getDescription());
          fieldMeta.setIsNullable(!field.getIsRequired());
          fieldMeta.setDefaultValue(field.getDefaultValue());
          fieldMeta.setRules(field.getValidators());
          fieldMeta.setExtraProps(field.getExtraProps());
          fieldMeta.setRawType(field.getExtraProp(String.class, "rawType").orElse(null));
          fieldMeta.setIsUnsigned(field.getExtraProp(Boolean.class, "unsigned").orElse(null));
          fieldMeta.setIsAutoIncrement(field.getExtraProp(Boolean.class, "autoIncrement").orElse(null));
          fieldMeta.setIsUnique(field.getIsUnique());

          // 原始字段名
          field.getExtraProp(String.class, "rawFieldName")
              .filter(rawName -> !rawName.trim().isEmpty())
              .ifPresent(fieldMeta::setRawName);

          return fieldMeta;
        })
        .collect(Collectors.toList());
  }

  /**
   * 构建基础插件描述符列表
   * 该方法创建并返回一个包含基本插件描述符的列表
   *
   * @return 包含基本插件描述符的List集合
   */
  private List<PluginDescriptor> buildBasicPluginDescriptor() {
    // 创建一个新的ArrayList用于存储PluginDescriptor对象
    var pluginDescriptors = new ArrayList<PluginDescriptor>();
    // 向列表中添加"NormalizeWhere"插件描述符
    pluginDescriptors.add(new PluginDescriptor("NormalizeWhere"));
    // 向列表中添加"DuplicateDataChecker"插件描述符
    pluginDescriptors.add(new PluginDescriptor("DuplicateDataChecker"));
    // 向列表中添加"UpdateWhenDataChange"插件描述符
    pluginDescriptors.add(new PluginDescriptor("UpdateWhenDataChange"));
    // 返回包含所有基本插件描述符的列表
    return pluginDescriptors;
  }
}
