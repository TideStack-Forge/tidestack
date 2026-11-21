package com.ouroboros.businessmodel.validation;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.BusinessModelMetaParser;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineRelationError;

/**
 * Relation validator for default business model.
 * Validates current-format relation definitions; legacy documents retain their existing projection rules.
 */
public class RelationValidator implements ModelDetailValidator<DefaultBusinessModel> {
  @Override
  public void doValidate(ValidationContext<DefaultBusinessModel> context) {
    var model = context.getModel();
    if (!BusinessModelMetaParser.isCurrent(model.getMeta())) {
      return;
    }
    var names = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    for (var relation : model.getRelations()) {
      String path = "relations." + relation.getName();
      if (relation.getName() == null || relation.getName().isBlank()) {
        error(context, "relations.name-required");
        continue;
      }
      if (!names.add(relation.getName())) {
        error(context, path + ".duplicate");
      }
      if (model.getFields().stream().anyMatch(field -> relation.getName().equalsIgnoreCase(field.getName()))) {
        error(context, path + ".field-name-conflict");
      }
      String type = relation.getType();
      if (!"child".equalsIgnoreCase(type) && !"master".equalsIgnoreCase(type) && !"reference".equalsIgnoreCase(type)) {
        error(context, path + ".type-invalid");
        continue;
      }
      var target = relation.getRelatedModel();
      if (target == null || !Boolean.TRUE.equals(target.isEnabled())) {
        error(context, path + ".target-model-unavailable");
        continue;
      }
      String key = relation.getReferenceKey();
      if ("child".equalsIgnoreCase(type)) {
        if (key == null || key.isBlank()) {
          long matches = target.getRelations().stream().filter(back ->
              "master".equalsIgnoreCase(back.getType()) && model.getFullName().equalsIgnoreCase(back.getRelatedModelName())).count();
          if (matches != 1) {
            error(context, path + ".referenceKey-ambiguous");
          }
        } else if (target.getField(key).isEmpty() && target.getRelation(key).isEmpty()) {
          error(context, path + ".referenceKey-not-found");
        }
      } else {
        key = key == null || key.isBlank() ? "id" : key;
        var field = target.getField(key);
        if (field.isEmpty()) {
          error(context, path + ".referenceKey-not-found");
        } else if (!Boolean.TRUE.equals(field.get().getIsPrimaryKey()) && !Boolean.TRUE.equals(field.get().getIsUnique())) {
          error(context, path + ".referenceKey-not-unique");
        }
      }
    }
  }

  private static void error(ValidationContext<DefaultBusinessModel> context, String code) {
    context.addError(new BusinessDefineRelationError(context.getModel().getFullName() + ": " + code));
  }
}
