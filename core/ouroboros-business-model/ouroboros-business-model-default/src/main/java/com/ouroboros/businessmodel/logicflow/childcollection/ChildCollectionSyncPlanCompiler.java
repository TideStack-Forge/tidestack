package com.ouroboros.businessmodel.logicflow.childcollection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.BusinessModelRelation;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.fieldtype.BusinessModelFieldTypeSupport;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;

public class ChildCollectionSyncPlanCompiler {

  public List<ChildCollectionSyncPlan> compile(DefaultBusinessModel ownerModel, ModelBundle bundle) {
    return compile(ownerModel, modelName -> bundle.getModel(DataModelMeta.class, modelName));
  }

  public List<ChildCollectionSyncPlan> compile(DefaultBusinessModel ownerModel,
                                               Function<String, Optional<DataModelMeta>> dataModelResolver) {
    return compile(ownerModel, dataModelResolver, new LinkedHashSet<>());
  }

  public static String fingerprint(DataModelMeta meta, String primaryKeyField, String relationField) {
    var fieldsByName = fieldsByName(meta);
    var relevantFieldNames = new ArrayList<String>();
    relevantFieldNames.add(primaryKeyField);
    relevantFieldNames.add(relationField);
    var fieldFingerprint = relevantFieldNames.stream()
        .filter(StringUtils::isNotBlank)
        .distinct()
        .sorted(String.CASE_INSENSITIVE_ORDER)
        .map(fieldName -> {
          var field = fieldsByName.get(normalize(fieldName));
          return fieldName + ":" + fieldSignature(field);
        })
        .collect(Collectors.joining("|"));
    return sha256(meta.getFullName() + "|" + fieldFingerprint);
  }

  public static Optional<String> singlePrimaryKey(DefaultBusinessModel model) {
    var primaryKeys = model.getFields().stream()
        .filter(field -> Boolean.TRUE.equals(field.getIsPrimaryKey()))
        .map(field -> field.getName())
        .collect(Collectors.toList());
    return primaryKeys.size() == 1 ? Optional.of(primaryKeys.get(0)) : Optional.empty();
  }

  public static List<String> primaryKeys(DefaultBusinessModel model) {
    return model.getFields().stream()
        .filter(field -> Boolean.TRUE.equals(field.getIsPrimaryKey()))
        .map(field -> field.getName())
        .collect(Collectors.toList());
  }

  public static Optional<String> resolveChildReferenceField(BusinessModelRelation relation) {
    if (StringUtils.isNotBlank(relation.getReferenceKey())) {
      return Optional.of(relation.getReferenceKey());
    }
    var ownerModel = relation.getModel();
    var childModel = relation.getRelatedModel();
    if (childModel == null) {
      return Optional.empty();
    }
    var candidates = childModel.getRelations().stream()
        .filter(childRelation -> "master".equalsIgnoreCase(childRelation.getType()))
        .filter(childRelation -> Objects.equals(childRelation.getRelatedModelName(), ownerModel.getFullName()))
        .map(BusinessModelRelation::getName)
        .collect(Collectors.toList());
    return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
  }

  public static String resolveOwnerKeyField(BusinessModelRelation relation, String ownerPrimaryKey) {
    return Optional.ofNullable(relation.getKey())
        .filter(StringUtils::isNotBlank)
        .orElse(ownerPrimaryKey);
  }

  public static boolean hasBusinessField(DefaultBusinessModel model, String fieldName) {
    return model.getField(fieldName).isPresent();
  }

  public static boolean hasBusinessFieldOrRelation(DefaultBusinessModel model, String fieldName) {
    return model.getField(fieldName).isPresent() || model.getRelation(fieldName).isPresent();
  }

  public static boolean isBusinessTypeCompatible(DefaultBusinessModel ownerModel,
                                                 String ownerKeyField,
                                                 DefaultBusinessModel childModel,
                                                 String childReferenceField) {
    var ownerField = ownerModel.getField(ownerKeyField).orElse(null);
    var childField = childModel.getField(childReferenceField).orElse(null);
    if (ownerField != null && childField != null) {
      var childType = businessDataType(childField.getType(), childField.getExtraProps());
      return StringUtils.equalsIgnoreCase(childType, "String")
          || StringUtils.equalsIgnoreCase(ownerField.getType(), childField.getType());
    }
    var childRelation = childModel.getRelation(childReferenceField).orElse(null);
    if (ownerField != null && childRelation != null) {
      return Objects.equals(childRelation.getRelatedModelName(), ownerModel.getFullName())
          && StringUtils.equalsIgnoreCase(
          childRelation.getReferenceKey() == null ? ownerKeyField : childRelation.getReferenceKey(),
          ownerKeyField);
    }
    return false;
  }

  private static String businessDataType(String fieldType, Map<String, Object> fieldProps) {
    return BusinessModelFieldTypeSupport.compileRuntime(fieldType, fieldProps)
        .map(compiledFieldType -> compiledFieldType.toDataFieldMeta())
        .map(DataModelFieldMeta::getType)
        .orElse(fieldType);
  }

  private List<ChildCollectionSyncPlan> compile(DefaultBusinessModel ownerModel,
                                                Function<String, Optional<DataModelMeta>> dataModelResolver,
                                                Set<String> modelPath) {
    if (!modelPath.add(ownerModel.getFullName())) {
      throw new IllegalArgumentException("submitTogether 关系环: " + String.join(" -> ", modelPath) + " -> " + ownerModel.getFullName());
    }
    try {
      var plans = new ArrayList<ChildCollectionSyncPlan>();
      for (var relation : ownerModel.getRelations()) {
        if (Boolean.TRUE.equals(relation.getSubmitTogether())) {
          plans.add(compileRelation(ownerModel, relation, dataModelResolver, modelPath));
        }
      }
      return plans;
    } finally {
      modelPath.remove(ownerModel.getFullName());
    }
  }

  private ChildCollectionSyncPlan compileRelation(DefaultBusinessModel ownerModel,
                                                  BusinessModelRelation relation,
                                                  Function<String, Optional<DataModelMeta>> dataModelResolver,
                                                  Set<String> modelPath) {
    var childModel = relation.getRelatedModel();
    if (childModel == null) {
      throw new IllegalArgumentException("submitTogether 关系 " + relation.getName() + " 的子模型不存在: " + relation.getRelatedModelName());
    }

    var ownerDataModel = dataModel(dataModelResolver, ownerModel.getFullName());
    var childDataModel = dataModel(dataModelResolver, childModel.getFullName());
    var ownerPrimaryKey = singlePrimaryKey(ownerDataModel)
        .orElseThrow(() -> new IllegalArgumentException(ownerModel.getFullName() + " 必须有单列主键"));
    var childPrimaryKey = singlePrimaryKey(childDataModel)
        .orElseThrow(() -> new IllegalArgumentException(childModel.getFullName() + " 必须有单列主键"));
    var ownerKeyField = resolveOwnerKeyField(relation, ownerPrimaryKey);
    if (StringUtils.isBlank(ownerKeyField)
        || !fieldsByName(ownerDataModel).containsKey(normalize(ownerKeyField))) {
      throw new IllegalArgumentException("submitTogether 关系 " + relation.getName() + " 的 owner key 必须是 owner 数据模型上的字段: " + ownerKeyField);
    }
    var childReferenceField = resolveChildReferenceField(relation)
        .orElseThrow(() -> new IllegalArgumentException("submitTogether 关系 " + relation.getName() + " 缺少 child reference field"));
    if (!fieldsByName(childDataModel).containsKey(normalize(childReferenceField))) {
      throw new IllegalArgumentException("submitTogether 关系 " + relation.getName() + " 的 child reference field 不存在: " + childReferenceField);
    }
    if (!isDataTypeCompatible(ownerDataModel, ownerKeyField, childDataModel, childReferenceField)) {
      throw new IllegalArgumentException("submitTogether 关系 " + relation.getName() + " 的 owner key 与 child reference field 类型不兼容");
    }

    return new ChildCollectionSyncPlan(
        relation.getName(),
        ownerDataModel.getFullName(),
        childDataModel.getFullName(),
        ownerKeyField,
        childReferenceField,
        childPrimaryKey,
        fingerprint(ownerDataModel, ownerPrimaryKey, ownerKeyField),
        fingerprint(childDataModel, childPrimaryKey, childReferenceField),
        compile(childModel, dataModelResolver, modelPath)
    );
  }

  private static DataModelMeta dataModel(Function<String, Optional<DataModelMeta>> dataModelResolver, String modelName) {
    return dataModelResolver.apply(modelName)
        .orElseThrow(() -> new IllegalArgumentException("缺少 DataModelMeta: " + modelName));
  }

  private static Optional<String> singlePrimaryKey(DataModelMeta meta) {
    var primaryKeys = meta.getPrimaryKeys();
    if (primaryKeys.size() == 1) {
      return Optional.of(primaryKeys.get(0));
    }
    if (!primaryKeys.isEmpty()) {
      return Optional.empty();
    }
    var markedPrimaryKeys = meta.getFields().stream()
        .filter(field -> field.getExtraProp(Boolean.class, "isPrimaryKey").orElse(false))
        .map(DataModelFieldMeta::getName)
        .collect(Collectors.toList());
    return markedPrimaryKeys.size() == 1 ? Optional.of(markedPrimaryKeys.get(0)) : Optional.empty();
  }

  private static boolean isDataTypeCompatible(DataModelMeta ownerMeta,
                                              String ownerKeyField,
                                              DataModelMeta childMeta,
                                              String childReferenceField) {
    var ownerField = fieldsByName(ownerMeta).get(normalize(ownerKeyField));
    var childField = fieldsByName(childMeta).get(normalize(childReferenceField));
    if (ownerField == null || childField == null) {
      return false;
    }
    if ("Model".equalsIgnoreCase(childField.getType())) {
      var referenceModel = childField.getExtraProp(String.class, "model").orElse("");
      var referenceKey = childField.getExtraProp(String.class, "referenceKey")
          .or(() -> childField.getExtraProp(String.class, "via"))
          .orElse(ownerKeyField);
      return StringUtils.equalsIgnoreCase(referenceModel, ownerMeta.getFullName())
          && StringUtils.equalsIgnoreCase(referenceKey, ownerKeyField);
    }
    return StringUtils.equalsIgnoreCase(childField.getType(), "String")
        || StringUtils.equalsIgnoreCase(ownerField.getType(), childField.getType());
  }

  private static Map<String, DataModelFieldMeta> fieldsByName(DataModelMeta meta) {
    return meta.getFields().stream()
        .sorted(Comparator.comparing(DataModelFieldMeta::getName, String.CASE_INSENSITIVE_ORDER))
        .collect(Collectors.toMap(
            field -> normalize(field.getName()),
            Function.identity(),
            (left, right) -> left
        ));
  }

  private static String fieldSignature(DataModelFieldMeta field) {
    if (field == null) {
      return "<missing>";
    }
    var relationProps = List.of("model", "referenceKey", "via", "key").stream()
        .map(key -> key + "=" + field.getExtraProp(Object.class, key).map(Object::toString).orElse(""))
        .collect(Collectors.joining(","));
    return field.getName() + ":" + field.getType() + ":" + relationProps;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private static String sha256(String value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      var builder = new StringBuilder(digest.length * 2);
      for (byte item : digest) {
        builder.append(String.format("%02x", item));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
