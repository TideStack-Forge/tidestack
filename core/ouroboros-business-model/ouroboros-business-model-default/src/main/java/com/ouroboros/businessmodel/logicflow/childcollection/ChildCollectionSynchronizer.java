package com.ouroboros.businessmodel.logicflow.childcollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

import io.vavr.control.Try;

import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;

public class ChildCollectionSynchronizer {
  private final Function<String, Optional<DataModel>> dataModelResolver;
  private final int ownerQueryChunkSize;

  public ChildCollectionSynchronizer(Function<String, Optional<DataModel>> dataModelResolver) {
    this(dataModelResolver, 1_000);
  }

  ChildCollectionSynchronizer(Function<String, Optional<DataModel>> dataModelResolver, int ownerQueryChunkSize) {
    this.dataModelResolver = dataModelResolver;
    if (ownerQueryChunkSize <= 0) {
      throw new IllegalArgumentException("ownerQueryChunkSize must be positive");
    }
    this.ownerQueryChunkSize = ownerQueryChunkSize;
  }

  public Map<String, Object> sync(
      ChildCollectionSyncMode mode,
      Map<String, Object> persistedOwner,
      Map<String, Object> submittedOwner,
      List<ChildCollectionSyncPlan> plans
  ) {
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(persistedOwner, "persistedOwner");
    Objects.requireNonNull(submittedOwner, "submittedOwner");

    var rootProvenance = mode == ChildCollectionSyncMode.CREATE ? OwnerProvenance.NEW : OwnerProvenance.EXISTING;
    var rootOwner = new OwnerSyncInput(copyRecord(persistedOwner), copyRecord(submittedOwner), rootProvenance);
    var result = new LinkedHashMap<String, Object>();
    for (var plan : plans == null ? List.<ChildCollectionSyncPlan>of() : plans) {
      var ownerKey = normalizeKey(rootOwner.persistedOwner().get(plan.ownerKeyField()));
      var synced = syncPlan(List.of(rootOwner), plan);
      if (synced.containsKey(ownerKey)) {
        result.put(plan.relationName(), synced.get(ownerKey));
      }
    }
    return result;
  }

  private Map<Object, List<Map<String, Object>>> syncPlan(List<OwnerSyncInput> owners, ChildCollectionSyncPlan plan) {
    var ownerModel = resolveModel(plan.ownerDataModelName());
    var childModel = resolveModel(plan.childDataModelName());
    validateFingerprint(plan, ownerModel, childModel);

    var nestedRelationNames = plan.children().stream()
        .map(ChildCollectionSyncPlan::relationName)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    var activeOwners = collectActiveOwners(owners, plan);
    if (activeOwners.isEmpty()) {
      return Collections.emptyMap();
    }

    var existingOwnerKeys = activeOwners.stream()
        .filter(owner -> owner.provenance() == OwnerProvenance.EXISTING)
        .map(ActiveOwner::ownerKey)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    var currentRowsByOwner = queryByOwnerKeys(childModel, plan.childReferenceField(), existingOwnerKeys);

    var deleteIds = new ArrayList<>();
    var updates = new ArrayList<UpdateSlot>();
    var inserts = new ArrayList<Map<String, Object>>();
    var insertSlots = new ArrayList<InsertSlot>();
    var submittedSlotsByOwner = new LinkedHashMap<Object, List<SubmittedChildSlot>>();
    var nestedOwners = new ArrayList<OwnerSyncInput>();
    var seenSubmittedChildIds = new LinkedHashSet<>();

    for (var owner : activeOwners) {
      var currentRows = currentRowsByOwner.getOrDefault(owner.ownerKey(), List.of());
      var currentByPrimaryKey = indexBy(currentRows, plan.childPrimaryKeyField());
      var submittedPrimaryKeys = new LinkedHashSet<>();
      var submittedSlots = new ArrayList<SubmittedChildSlot>();
      submittedSlotsByOwner.put(owner.ownerKey(), submittedSlots);

      for (var submittedChild : owner.submittedChildren()) {
        var childPrimaryKey = submittedChild.get(plan.childPrimaryKeyField());
        if (!isBlankValue(childPrimaryKey) && !seenSubmittedChildIds.add(normalizeKey(childPrimaryKey))) {
          throw new IllegalArgumentException("同批提交中存在重复子表主键: " + childPrimaryKey);
        }

        var writeData = toChildWriteData(submittedChild, childModel, plan, nestedRelationNames);
        writeData.put(plan.childReferenceField(), owner.ownerKey());

        if (owner.provenance() == OwnerProvenance.NEW || isBlankValue(childPrimaryKey)) {
          inserts.add(writeData);
          var slot = new SubmittedChildSlot(copyRecord(submittedChild));
          submittedSlots.add(slot);
          insertSlots.add(new InsertSlot(slot));
          continue;
        }

        var current = currentByPrimaryKey.get(normalizeKey(childPrimaryKey));
        if (current == null) {
          throw new IllegalArgumentException("submitted child ID 不属于当前 owner scope: " + childPrimaryKey);
        }
        submittedPrimaryKeys.add(normalizeKey(childPrimaryKey));

        var slot = new SubmittedChildSlot(copyRecord(submittedChild));
        slot.setPrimaryKey(childPrimaryKey);
        submittedSlots.add(slot);
        nestedOwners.add(new OwnerSyncInput(copyRecord(current), copyRecord(submittedChild), OwnerProvenance.EXISTING));

        var patch = toUpdatePatch(submittedChild, current, childModel, plan, nestedRelationNames);
        if (!patch.isEmpty()) {
          updates.add(new UpdateSlot(ownerScopedPrimaryKey(plan, owner.ownerKey(), childPrimaryKey), patch));
        }
      }

      for (var current : currentRows) {
        var currentPrimaryKey = current.get(plan.childPrimaryKeyField());
        if (!submittedPrimaryKeys.contains(normalizeKey(currentPrimaryKey))) {
          deleteIds.add(currentPrimaryKey);
        }
      }
    }

    executeDeleteWithCascade(childModel, plan, deleteIds);
    executeBatchUpdate(childModel, updates);
    var insertedRecords = executeBatchInsert(childModel, inserts);
    for (int index = 0; index < insertSlots.size(); index++) {
      var inserted = insertedRecords.get(index);
      var primaryKey = inserted.get(plan.childPrimaryKeyField());
      insertSlots.get(index).slot().setPrimaryKey(primaryKey);
      nestedOwners.add(new OwnerSyncInput(copyRecord(inserted), insertSlots.get(index).slot().submittedChild(), OwnerProvenance.NEW));
    }

    var nestedResults = new LinkedHashMap<String, Map<Object, List<Map<String, Object>>>>();
    for (var childPlan : plan.children()) {
      nestedResults.put(childPlan.relationName(), syncPlan(nestedOwners, childPlan));
    }

    var finalRowsByOwner = updates.isEmpty() && deleteIds.isEmpty()
        ? rowsWithoutReload(currentRowsByOwner, insertedRecords, plan.childReferenceField())
        : queryByOwnerKeys(
            childModel,
            plan.childReferenceField(),
            activeOwners.stream().map(ActiveOwner::ownerKey).collect(Collectors.toCollection(LinkedHashSet::new))
        );
    return orderAndMergeFinalRows(plan, submittedSlotsByOwner, finalRowsByOwner, nestedResults);
  }

  private Map<Object, List<Map<String, Object>>> rowsWithoutReload(
      Map<Object, List<Map<String, Object>>> currentRowsByOwner,
      List<Map<String, Object>> insertedRecords,
      String referenceField
  ) {
    var rowsByOwner = new LinkedHashMap<Object, List<Map<String, Object>>>();
    currentRowsByOwner.forEach((ownerKey, rows) -> rowsByOwner.put(ownerKey, copyDataList(rows)));
    for (var inserted : insertedRecords) {
      var ownerKey = normalizeKey(inserted.get(referenceField));
      rowsByOwner.computeIfAbsent(ownerKey, ignored -> new ArrayList<>()).add(copyRecord(inserted));
    }
    return rowsByOwner;
  }

  private List<ActiveOwner> collectActiveOwners(List<OwnerSyncInput> owners, ChildCollectionSyncPlan plan) {
    var activeOwners = new ArrayList<ActiveOwner>();
    for (var owner : owners) {
      if (!owner.submittedOwner().containsKey(plan.relationName())) {
        continue;
      }
      var ownerKey = owner.persistedOwner().get(plan.ownerKeyField());
      if (isBlankValue(ownerKey)) {
        throw new IllegalArgumentException("owner key 不能为空: " + plan.ownerKeyField());
      }
      var submittedValue = owner.submittedOwner().get(plan.relationName());
      if (!(submittedValue instanceof Collection<?> collection)) {
        throw new IllegalArgumentException("submitTogether 字段必须是集合: " + plan.relationName());
      }
      var submittedChildren = collection.stream()
          .map(item -> {
            if (!(item instanceof Map<?, ?> map)) {
              throw new IllegalArgumentException("submitTogether 集合项必须是对象: " + plan.relationName());
            }
            return copyRecord(map);
          })
          .collect(Collectors.toList());
      activeOwners.add(new ActiveOwner(normalizeKey(ownerKey), owner.provenance(), submittedChildren));
    }
    return activeOwners;
  }

  private void executeDeleteWithCascade(DataModel childModel, ChildCollectionSyncPlan plan, List<Object> deleteIds) {
    if (deleteIds.isEmpty()) {
      return;
    }
    for (var childPlan : plan.children()) {
      cascadeDelete(childPlan, deleteIds);
    }
    unwrap(childModel.delete(deleteIds), "删除子表数据失败: " + childModel.getFullName());
  }

  private void cascadeDelete(ChildCollectionSyncPlan plan, Collection<Object> ownerKeys) {
    if (ownerKeys == null || ownerKeys.isEmpty()) {
      return;
    }
    var ownerModel = resolveModel(plan.ownerDataModelName());
    var childModel = resolveModel(plan.childDataModelName());
    validateFingerprint(plan, ownerModel, childModel);

    var descendantsByOwner = queryByOwnerKeys(childModel, plan.childReferenceField(), ownerKeys);
    var descendantIds = descendantsByOwner.values().stream()
        .flatMap(Collection::stream)
        .map(row -> row.get(plan.childPrimaryKeyField()))
        .filter(value -> !isBlankValue(value))
        .collect(Collectors.toList());
    if (descendantIds.isEmpty()) {
      return;
    }
    for (var childPlan : plan.children()) {
      cascadeDelete(childPlan, descendantIds);
    }
    unwrap(childModel.delete(descendantIds), "级联删除子表数据失败: " + childModel.getFullName());
  }

  private void executeBatchUpdate(DataModel childModel, List<UpdateSlot> updates) {
    if (updates.isEmpty()) {
      return;
    }
    for (var update : updates) {
      unwrap(childModel.update(update.where(), update.patch()), "更新子表数据失败: " + childModel.getFullName());
    }
  }

  private List<Map<String, Object>> executeBatchInsert(DataModel childModel, List<Map<String, Object>> inserts) {
    if (inserts.isEmpty()) {
      return List.of();
    }
    var inserted = unwrap(childModel.batchInsert(inserts), "批量插入子表数据失败: " + childModel.getFullName())
        .stream()
        .map(ChildCollectionSynchronizer::copyRecord)
        .collect(Collectors.toList());
    if (inserted.size() != inserts.size()) {
      throw new IllegalStateException("批量插入返回记录数量与输入不一致: " + childModel.getFullName());
    }
    return inserted;
  }

  private Map<Object, List<Map<String, Object>>> queryByOwnerKeys(
      DataModel model,
      String referenceField,
      Collection<Object> ownerKeys
  ) {
    if (ownerKeys == null || ownerKeys.isEmpty()) {
      return Collections.emptyMap();
    }
    var keys = ownerKeys.stream()
        .filter(value -> !isBlankValue(value))
        .collect(Collectors.toCollection(LinkedHashSet::new));
    if (keys.isEmpty()) {
      return Collections.emptyMap();
    }
    var result = new LinkedHashMap<Object, List<Map<String, Object>>>();
    var keyList = new ArrayList<>(keys);
    for (int offset = 0; offset < keyList.size(); offset += ownerQueryChunkSize) {
      var chunk = keyList.subList(offset, Math.min(offset + ownerQueryChunkSize, keyList.size()));
      var statement = QueryStatement.builder()
          .from(model.getFullName(), model.getRawName())
          .where(SExpression.create(
              Operators.IN,
              SExpression.field(referenceField),
              SExpression.constant(List.copyOf(chunk))
          ))
          .build();
      var records = unwrap(model.query(statement), "查询子表数据失败: " + model.getFullName());
      for (var row : records) {
        var copied = copyRecord(row);
        result.computeIfAbsent(normalizeKey(copied.get(referenceField)), ignored -> new ArrayList<>()).add(copied);
      }
    }
    return result;
  }

  private Map<Object, List<Map<String, Object>>> orderAndMergeFinalRows(
      ChildCollectionSyncPlan plan,
      Map<Object, List<SubmittedChildSlot>> submittedSlotsByOwner,
      Map<Object, List<Map<String, Object>>> finalRowsByOwner,
      Map<String, Map<Object, List<Map<String, Object>>>> nestedResults
  ) {
    var result = new LinkedHashMap<Object, List<Map<String, Object>>>();
    for (var entry : submittedSlotsByOwner.entrySet()) {
      var ownerKey = entry.getKey();
      var finalRowsByPrimaryKey = indexBy(finalRowsByOwner.getOrDefault(ownerKey, List.of()), plan.childPrimaryKeyField());
      var orderedRows = new ArrayList<Map<String, Object>>();
      var emittedPrimaryKeys = new LinkedHashSet<>();
      for (var slot : entry.getValue()) {
        // 提交主键（JSON 数字可能是 Integer）与库中主键（bigint 是 Long）类型不同，
        // 统一按字符串比较，否则既定位不到已更新的行、也会在下面的补录循环里重复输出。
        var primaryKey = normalizeKey(slot.primaryKey());
        if (isBlankValue(primaryKey) || !emittedPrimaryKeys.add(primaryKey)) {
          continue;
        }
        var finalRow = finalRowsByPrimaryKey.get(primaryKey);
        if (finalRow != null) {
          orderedRows.add(mergeNestedResults(finalRow, plan, nestedResults));
        }
      }
      for (var finalRow : finalRowsByOwner.getOrDefault(ownerKey, List.of())) {
        var primaryKey = normalizeKey(finalRow.get(plan.childPrimaryKeyField()));
        if (emittedPrimaryKeys.add(primaryKey)) {
          orderedRows.add(mergeNestedResults(finalRow, plan, nestedResults));
        }
      }
      result.put(ownerKey, orderedRows);
    }
    return result;
  }

  private Map<String, Object> mergeNestedResults(
      Map<String, Object> finalRow,
      ChildCollectionSyncPlan plan,
      Map<String, Map<Object, List<Map<String, Object>>>> nestedResults
  ) {
    var merged = copyRecord(finalRow);
    var primaryKey = finalRow.get(plan.childPrimaryKeyField());
    for (var childPlan : plan.children()) {
      var childResult = nestedResults.getOrDefault(childPlan.relationName(), Collections.emptyMap());
      if (childResult.containsKey(normalizeKey(primaryKey))) {
        merged.put(childPlan.relationName(), childResult.get(normalizeKey(primaryKey)));
      }
    }
    return merged;
  }

  private static Map<Object, Map<String, Object>> indexBy(List<Map<String, Object>> rows, String field) {
    var result = new LinkedHashMap<Object, Map<String, Object>>();
    for (var row : rows) {
      var key = row.get(field);
      if (!isBlankValue(key)) {
        // 主键统一按字符串索引：库里是 varchar 时取回来是 String，而请求体里可能是 JSON 数字，
        // 直接 get 会因类型不同而查不到（表现为"submitted child ID 不属于当前 owner scope"）。
        result.put(normalizeKey(key), row);
      }
    }
    return result;
  }

  /** 主键比较统一走字符串，避免 String / Integer / Long 之间对不上。 */
  private static String normalizeKey(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static Map<String, Object> toChildWriteData(
      Map<String, Object> submittedChild,
      DataModel childModel,
      ChildCollectionSyncPlan plan,
      Set<String> nestedRelationNames
  ) {
    var fieldNames = fieldNames(childModel);
    var data = new LinkedHashMap<String, Object>();
    submittedChild.forEach((key, value) -> {
      if (!nestedRelationNames.contains(key) && fieldNames.contains(normalize(key))) {
        data.put(key, value);
      }
    });
    data.remove(plan.childReferenceField());
    return data;
  }

  private static Map<String, Object> toUpdatePatch(
      Map<String, Object> submittedChild,
      Map<String, Object> current,
      DataModel childModel,
      ChildCollectionSyncPlan plan,
      Set<String> nestedRelationNames
  ) {
    var fieldNames = fieldNames(childModel);
    var patch = new LinkedHashMap<String, Object>();
    submittedChild.forEach((key, value) -> {
      if (StringUtils.equalsIgnoreCase(key, plan.childPrimaryKeyField())
          || StringUtils.equalsIgnoreCase(key, plan.childReferenceField())
          || nestedRelationNames.contains(key)
          || !fieldNames.contains(normalize(key))) {
        return;
      }
      if (!Objects.equals(value, current.get(key))) {
        patch.put(key, value);
      }
    });
    return patch;
  }

  private static SExpression<Boolean> ownerScopedPrimaryKey(
      ChildCollectionSyncPlan plan,
      Object ownerKey,
      Object childPrimaryKey
  ) {
    return SExpression.create(
        Operators.AND,
        SExpression.create(Operators.EQ, SExpression.field(plan.childPrimaryKeyField()), SExpression.constant(childPrimaryKey)),
        SExpression.create(Operators.EQ, SExpression.field(plan.childReferenceField()), SExpression.constant(ownerKey))
    );
  }

  private void validateFingerprint(ChildCollectionSyncPlan plan, DataModel ownerModel, DataModel childModel) {
    var ownerPrimaryKey = singlePrimaryKey(ownerModel)
        .orElseThrow(() -> new IllegalStateException(plan.ownerDataModelName() + " 必须有单列主键"));
    var childPrimaryKey = singlePrimaryKey(childModel)
        .orElseThrow(() -> new IllegalStateException(plan.childDataModelName() + " 必须有单列主键"));
    var ownerFingerprint = ChildCollectionSyncPlanCompiler.fingerprint(toMeta(ownerModel), ownerPrimaryKey, plan.ownerKeyField());
    var childFingerprint = ChildCollectionSyncPlanCompiler.fingerprint(toMeta(childModel), childPrimaryKey, plan.childReferenceField());
    if (!Objects.equals(plan.ownerDataModelFingerprint(), ownerFingerprint)
        || !Objects.equals(plan.childDataModelFingerprint(), childFingerprint)
        || !StringUtils.equalsIgnoreCase(plan.childPrimaryKeyField(), childPrimaryKey)) {
      throw new IllegalStateException("子表同步计划/模型漂移: " + plan.relationName());
    }
  }

  private DataModel resolveModel(String modelName) {
    return dataModelResolver.apply(modelName)
        .orElseThrow(() -> new IllegalStateException("缺少 DataModel: " + modelName));
  }

  private static Optional<String> singlePrimaryKey(DataModel model) {
    var primaryKeys = model.getPrimaryKeys().stream()
        .map(DataModelField::getName)
        .collect(Collectors.toList());
    return primaryKeys.size() == 1 ? Optional.of(primaryKeys.get(0)) : Optional.empty();
  }

  private static DataModelMeta toMeta(DataModel model) {
    var meta = new DataModelMeta();
    meta.setNamespace(model.getNamespace());
    meta.setName(model.getName());
    meta.setPrimaryKeys(model.getPrimaryKeys().stream().map(DataModelField::getName).collect(Collectors.toList()));
    meta.setFields(model.getFields().stream()
        .sorted(Comparator.comparing(DataModelField::getName, String.CASE_INSENSITIVE_ORDER))
        .map(field -> {
          var fieldMeta = new DataModelFieldMeta();
          fieldMeta.setName(field.getName());
          fieldMeta.setType(field.getType());
          fieldMeta.setExtraProps(new LinkedHashMap<>(field.getExtraProps()));
          return fieldMeta;
        })
        .collect(Collectors.toList()));
    return meta;
  }

  private static Set<String> fieldNames(DataModel model) {
    return model.getFields().stream()
        .map(DataModelField::getName)
        .map(ChildCollectionSynchronizer::normalize)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static <T> T unwrap(Try<T> result, String message) {
    if (result.isFailure()) {
      throw new IllegalStateException(message, result.getCause());
    }
    return result.get();
  }

  private static Map<String, Object> copyRecord(Map<?, ?> source) {
    var result = new LinkedHashMap<String, Object>();
    source.forEach((key, value) -> result.put(String.valueOf(key), value));
    return result;
  }

  private static List<Map<String, Object>> copyDataList(Collection<Map<String, Object>> source) {
    return source.stream()
        .map(ChildCollectionSynchronizer::copyRecord)
        .collect(Collectors.toList());
  }

  private static boolean isBlankValue(Object value) {
    return value == null || value instanceof String string && StringUtils.isBlank(string);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private enum OwnerProvenance {
    NEW,
    EXISTING
  }

  private record OwnerSyncInput(
      Map<String, Object> persistedOwner,
      Map<String, Object> submittedOwner,
      OwnerProvenance provenance
  ) {
  }

  private record ActiveOwner(
      Object ownerKey,
      OwnerProvenance provenance,
      List<Map<String, Object>> submittedChildren
  ) {
  }

  private static final class SubmittedChildSlot {
    private final Map<String, Object> submittedChild;
    private Object primaryKey;

    private SubmittedChildSlot(Map<String, Object> submittedChild) {
      this.submittedChild = submittedChild;
    }

    private Map<String, Object> submittedChild() {
      return submittedChild;
    }

    private Object primaryKey() {
      return primaryKey;
    }

    private void setPrimaryKey(Object primaryKey) {
      this.primaryKey = primaryKey;
    }
  }

  private record InsertSlot(SubmittedChildSlot slot) {
  }

  private record UpdateSlot(
      SExpression<Boolean> where,
      Map<String, Object> patch
  ) {
  }
}
