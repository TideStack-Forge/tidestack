package com.ouroboros.businessmodel.projection.data.basic;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.BusinessModelRelation;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;
import com.ouroboros.data.model.DataModelFieldMeta;

/**
 * 为业务模型添加关联字段
 */
@Priority(2)
public class ReferenceRelationMPS extends AbstractDataMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    addFieldsToMainDataModel(context, getReferenceRelationFields(context, model));
  }

  // 当前模型作为从表的时候，需要在本模型中创建对方关联自己所需要的字段
  public List<DataModelFieldMeta> getReferenceRelationFields(ModelProjectionPipelineContext<DefaultBusinessModel> context,
                                                             DefaultBusinessModel model) {
    /*
     * ReferenceRelationMPS 关于 referenceKey 的处理逻辑：
     * 1. relation extraProps 中有 referenceKey
     * 1.1. 并且在当前模型的 relation.name == referenceKey 并且 relation.model 为 referenceModel.fullName 的，则跳过
     * 1.2. 在当前模型的relation中没有这个字段，若没有名字冲突（）则创建，否则抛异常
     * 2. relation extraProps 中没有 referenceKey
     * 2.1. 从当前relation中查找模型为对方模型的X对一关系
     * 2.1.1. 如果有且只有一个，忽略（不创建）（对方的Relation MPS阶段也要做相应的动作，确保DataModel中的referenceKey为这个relation的name）
     * 2.1.2. 如果存在多个，警告并忽略（不创建）（对方的Relation MPS阶段也要做相应的动作，确保DataModel中的referenceKey为这个relation的name）
     * 2.1.3. 如果没有，则按照「约定」创建一个 Model 字段
     *
     * 「约定」：name为fullName将.替换为空后，再加Id
     */

    final var referencedModels = model.getReferencedModels();

    // 从别的模型中找到关联到当前模型的关联关系
    final var relations = referencedModels
        .stream()
        .flatMap(referencedModel -> referencedModel.getRelations().stream())
        // 过滤出关联到本模型的关系
        .filter(buildIsRelatedToModelPredicate(model))
        // 在关联关系中过滤出以当前模型为从表的关系
        .filter(this::isChildRelation)
        .filter(referencedRelation -> {
          // 获取关联字段
          String referenceKey = referencedRelation.getReferenceKey();

          // 如果没有referenceKey，则返回 true，后面会按照约定创建一个
          if (ObjectUtils.isEmpty(referenceKey)) {
            // 检查当前模型是否存在关联字段
            return model.getRelations().stream()
                .filter(buildIsRelatedToModelPredicate(referencedRelation.getModel()))
                .noneMatch(this::isMasterRelation);
          }

          // 检查当前模型是否存在与referenceKey相匹配的关系，如果存在，则不需要自动创建关系字段，返回 false
          if (model.getRelations().stream()
              .filter(buildIsRelatedToModelPredicate(referencedRelation.getModel()))
              .filter(this::isMasterRelation)
              .anyMatch(r -> referenceKey.equalsIgnoreCase(r.getName()))) {
            return false;
          }

          // 显式 referenceKey 可以使用已声明的标量字段，不重复创建关系字段。
          if (model.getFields().stream().anyMatch(field -> referenceKey.equalsIgnoreCase(field.getName()))) {
            return false;
          }

          // 如果当前模型存在关系名与 referenceKey 相同的关系，但是类型不匹配，也抛出异常
          if (model.getRelations().stream().anyMatch(r -> referenceKey.equalsIgnoreCase(r.getName()))) {
            context.addError(new RuntimeException("模型 " + model.getFullName() + " 中已存在关联字段 " + referenceKey));
            return false;
          }

          return true;
        })
        .collect(Collectors.toList());

    // 自动创建关联字段
    // 目前这块逻辑不会执行，因为目前默认的业务模型编辑器在创建子表关系的时候要求子表中必须有相关关系字段
    // 未来可能会放松这个限制，所以这块逻辑保留
    return relations.stream().map(relation -> {
      final var relationField = new DataModelFieldMeta();
      final var relatedModel = relation.getModel();
      relationField.setName(buildReferenceKey(relation.getModel()));
      relationField.setLabel(relatedModel.getTitle());
      relationField.setType("Model");
      relationField.setExtraProp("model", relatedModel.getFullName());
      relationField.setExtraProp("referenceKey", relation.getExtraProp(String.class, "key", "id"));
      return relationField;
    }).collect(Collectors.toList());
  }

  private Boolean isChildRelation(BusinessModelRelation relation) {
    return "child".equalsIgnoreCase(relation.getType());
  }

  private Boolean isMasterRelation(BusinessModelRelation relation) {
    return "master".equalsIgnoreCase(relation.getType());
  }

  private Predicate<BusinessModelRelation> buildIsRelatedToModelPredicate(DefaultBusinessModel model) {
    return relation -> StringUtils.equalsIgnoreCase(model.getFullName(), relation.getRelatedModelName());
  }
}
