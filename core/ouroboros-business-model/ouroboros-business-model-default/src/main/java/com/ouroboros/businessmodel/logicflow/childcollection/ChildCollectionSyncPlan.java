package com.ouroboros.businessmodel.logicflow.childcollection;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record ChildCollectionSyncPlan(
    String relationName,
    String ownerDataModelName,
    String childDataModelName,
    String ownerKeyField,
    String childReferenceField,
    String childPrimaryKeyField,
    String ownerDataModelFingerprint,
    String childDataModelFingerprint,
    List<ChildCollectionSyncPlan> children
) {
  public ChildCollectionSyncPlan {
    children = List.copyOf(children == null ? List.of() : children);
  }
}
