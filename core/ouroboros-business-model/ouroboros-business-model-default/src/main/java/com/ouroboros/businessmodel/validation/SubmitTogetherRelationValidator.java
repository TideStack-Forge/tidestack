package com.ouroboros.businessmodel.validation;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.BusinessModelRelation;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.logicflow.childcollection.ChildCollectionSyncPlanCompiler;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineRelationError;

public class SubmitTogetherRelationValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    validateModel(context, context.getModel(), new LinkedHashSet<>(), new LinkedHashSet<>());
  }

  private void validateModel(ValidationContext<DefaultBusinessModel> context,
                             DefaultBusinessModel ownerModel,
                             Set<String> modelPath,
                             Set<String> relationPath) {
    if (!modelPath.add(ownerModel.getFullName())) {
      context.addError(new BusinessDefineRelationError("submitTogether 关系环: " + String.join(" -> ", relationPath)));
      return;
    }
    try {
      for (var relation : ownerModel.getRelations()) {
        if (Boolean.TRUE.equals(relation.getSubmitTogether())) {
          relationPath.add(relation.getName());
          validateRelation(context, ownerModel, relation, modelPath, relationPath);
          relationPath.remove(relation.getName());
        }
      }
    } finally {
      modelPath.remove(ownerModel.getFullName());
    }
  }

  private void validateRelation(ValidationContext<DefaultBusinessModel> context,
                                DefaultBusinessModel ownerModel,
                                BusinessModelRelation relation,
                                Set<String> modelPath,
                                Set<String> relationPath) {
    var childModel = relation.getRelatedModel();
    if (childModel == null) {
      context.addError(new BusinessDefineRelationError("submitTogether 关系 " + relation.getName()
          + " 的子模型不存在: " + relation.getRelatedModelName()));
      return;
    }
    if (modelPath.contains(childModel.getFullName())) {
      var cyclePath = new LinkedHashSet<String>(relationPath);
      cyclePath.add(relation.getName());
      context.addError(new BusinessDefineRelationError("submitTogether 关系环: " + String.join(" -> ", cyclePath)));
      return;
    }

    var ownerPrimaryKeys = ChildCollectionSyncPlanCompiler.primaryKeys(ownerModel);
    if (ownerPrimaryKeys.size() != 1) {
      context.addError(new BusinessDefineRelationError("submitTogether 关系 " + relation.getName()
          + " 的 owner 模型必须有单列主键"));
      return;
    }
    var childPrimaryKeys = ChildCollectionSyncPlanCompiler.primaryKeys(childModel);
    if (childPrimaryKeys.size() != 1) {
      context.addError(new BusinessDefineRelationError("submitTogether 关系 " + relation.getName()
          + " 的 child 模型必须有单列主键"));
    }

    var ownerPrimaryKey = ownerPrimaryKeys.get(0);
    var ownerKey = ChildCollectionSyncPlanCompiler.resolveOwnerKeyField(relation, ownerPrimaryKey);
    if (!ChildCollectionSyncPlanCompiler.hasBusinessField(ownerModel, ownerKey)) {
      context.addError(new BusinessDefineRelationError("submitTogether 关系 " + relation.getName()
          + " 的 owner key 必须是 owner 模型上的字段: " + ownerKey));
    }

    var childReferenceField = ChildCollectionSyncPlanCompiler.resolveChildReferenceField(relation).orElse("");
    if (StringUtils.isBlank(childReferenceField)
        || !ChildCollectionSyncPlanCompiler.hasBusinessFieldOrRelation(childModel, childReferenceField)) {
      context.addError(new BusinessDefineRelationError("submitTogether 关系 " + relation.getName()
          + " 的 child reference field 不存在: " + childReferenceField));
    } else if (!ChildCollectionSyncPlanCompiler.isBusinessTypeCompatible(ownerModel, ownerKey, childModel, childReferenceField)) {
      context.addError(new BusinessDefineRelationError("submitTogether 关系 " + relation.getName()
          + " 的 owner key 与 child reference field 类型不兼容"));
    }

    validateModel(context, childModel, modelPath, relationPath);
  }
}
