package com.ouroboros.businessmodel.logicflow.childcollection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.data.model.DataModel;

public class SubmitTogetherLifecycleInventoryBuilder {

  public Map<String, Object> build(
      Map<String, ? extends BusinessModelMeta> businessModels,
      Function<String, Optional<DataModel>> dataModelResolver,
      List<Map<String, Object>> sourceLedger) {
    List<Map<String, Object>> entries = new ArrayList<>();
    for (BusinessModelMeta businessModel : businessModels.values()) {
      if (businessModel instanceof DefaultBusinessModelMeta defaultModel) {
        collectEntries(defaultModel, dataModelResolver, entries);
      }
    }

    boolean sourceComplete = sourceLedger.stream()
        .noneMatch(source -> Boolean.FALSE.equals(source.get("sourceComplete"))
            || Boolean.FALSE.equals(source.get("enumerable")));

    Map<String, Object> inventory = new LinkedHashMap<>();
    inventory.put("schemaVersion", "submit-together-lifecycle-inventory/v1");
    inventory.put("sourceComplete", sourceComplete);
    inventory.put("sourceLedger", sourceLedger);
    inventory.put("entries", entries);
    return Collections.unmodifiableMap(inventory);
  }

  private void collectEntries(
      DefaultBusinessModelMeta ownerModel,
      Function<String, Optional<DataModel>> dataModelResolver,
      List<Map<String, Object>> entries) {
    for (BusinessModelRelationMeta relation : ownerModel.getRelations()) {
      if (!isSubmitTogetherChild(relation)) {
        continue;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      String childModelName = relation.getModel();
      String entryId = ownerModel.getFullName() + "." + relation.getName();
      entry.put("id", entryId);
      entry.put("ownerModel", ownerModel.getFullName());
      entry.put("relation", relation.getName());
      entry.put("relationPath", entryId);
      entry.put("childModel", childModelName);
      entry.put("referenceKey", relation.getReferenceKey());
      entry.put("issues", dataModelResolver.apply(childModelName).isPresent()
          ? Collections.emptyList()
          : missingDataModelIssue(entryId, childModelName));
      entries.add(Collections.unmodifiableMap(entry));
    }
  }

  private boolean isSubmitTogetherChild(BusinessModelRelationMeta relation) {
    return "child".equalsIgnoreCase(relation.getType()) && Boolean.TRUE.equals(relation.getSubmitTogether());
  }

  private List<Map<String, Object>> missingDataModelIssue(String entryId, String childModelName) {
    Map<String, Object> issue = new LinkedHashMap<>();
    issue.put("id", entryId + ":missing-data-model");
    issue.put("category", "source");
    issue.put("severity", "blocking");
    issue.put("resolution", "pending");
    issue.put("message", "submitTogether child DataModel not found: " + childModelName);
    return Collections.singletonList(Collections.unmodifiableMap(issue));
  }
}
