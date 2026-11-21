package com.ouroboros.businessmodel.projection.data.basic;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.businessmodel.BusinessModelRelation;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;
import com.ouroboros.data.model.DataModelFieldMeta;

/**
 * 为业务模型添加关联字段
 */
@Priority(2)
public class RelationMPS extends AbstractDataMPS {

  private final static Logger logger = LoggerFactory.getLogger(RelationMPS.class);

  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    final var model = context.getSourceModel();
    addFieldsToMainDataModel(context, getRelationFields(model));
  }

  private Boolean isChildRelation(BusinessModelRelation relation) {
    return "child".equalsIgnoreCase(relation.getType());
  }

  private Boolean isMasterOrReferenceRelation(BusinessModelRelation relation) {
    return "reference".equalsIgnoreCase(relation.getType())
        || "master".equalsIgnoreCase(relation.getType());
  }

  public List<DataModelFieldMeta> getRelationFields(DefaultBusinessModel model) {
    final var relations = model.getRelations();
    return Stream.concat(
        // 子表
        relations.stream()
            .filter(this::isChildRelation)
            .map(this::buildCollectionFieldMeta),
        // 主表和引用
        relations
            .stream()
            .filter(this::isMasterOrReferenceRelation)
            .map(this::buildModelFieldMeta)
    ).filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
  }

  /**
   * 构建关联字段
   *
   * @param relation
   * @param type
   * @return
   */
  private DataModelFieldMeta buildDataModelFieldMeta(BusinessModelRelation relation, String type, String referenceKey) {
    var relationField = new DataModelFieldMeta();
    relationField.setName(relation.getName());
    relationField.setLabel(relation.getTitle());
    relationField.setIsNullable(!relation.getIsRequired());
    relationField.setType(type);
    relationField.setExtraProp("model", relation.getRelatedModel().getFullName());
    relationField.setExtraProp("referenceKey", referenceKey);
    relationField.setExtraProp("key", relation.getKey());

    // rawFieldName
    return relationField;
  }

  private DataModelFieldMeta buildCollectionFieldMeta(BusinessModelRelation relation) {
    // 获取关联字段
    String referenceKey = relation.getReferenceKey();
    if (StringUtils.isEmpty(referenceKey)) {
      // 没有指定 referenceKey，自动推导
      // 找到与自己相关的模型中，把自己作为主表的关联关系
      var model = relation.getModel();
      List<BusinessModelRelation> referencedRelations = relation.getRelatedModel()
          .getRelations()
          .stream()
          .filter(rrs -> model.getFullName().equalsIgnoreCase(rrs.getRelatedModelName()))
          .filter(rrs -> "master".equalsIgnoreCase(rrs.getType()))
          .collect(Collectors.toList());

      // 如果不止一个关系，警告
      if (ObjectUtils.isNotEmpty(referencedRelations) && referencedRelations.size() > 1) {
        logger.warn("模型 {} 关联字段 {} 未指定 referenceKey 自动推导可能会有问题！", model.getFullName(), relation.getName());
      }

      if (ObjectUtils.isNotEmpty(referencedRelations)) {
        referenceKey = referencedRelations.get(0).getName();
      } else {
        // 指定默认的关联字段名
        // 目前这块逻辑不会执行，因为目前默认的业务模型编辑器在创建子表关系的时候要求子表中必须有相关关系字段
        // 未来可能会放松这个限制，所以这块逻辑保留
        referenceKey = buildReferenceKey(relation.getModel());
      }
    }

    var fieldMeta = buildDataModelFieldMeta(relation, "Collection", referenceKey);
    return fieldMeta;
  }

  private DataModelFieldMeta buildModelFieldMeta(BusinessModelRelation relation) {
    var referenceKey = StringUtils.isBlank(relation.getReferenceKey()) ? "id" : relation.getReferenceKey();
    var relatedModel = relation.getRelatedModel().getField(referenceKey);
    var fieldMeta = buildDataModelFieldMeta(relation, "Model", referenceKey);
    relation.getExtraProp(String.class, "rawFieldName")
        .filter(StringUtils::isNotBlank)
        .ifPresent(fieldMeta::setRawName);

    return fieldMeta;
  }
}
