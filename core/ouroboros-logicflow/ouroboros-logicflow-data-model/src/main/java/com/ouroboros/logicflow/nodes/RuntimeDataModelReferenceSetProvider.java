package com.ouroboros.logicflow.nodes;

import com.ouroboros.data.model.DataModelCenter;

public final class RuntimeDataModelReferenceSetProvider implements NodeReferenceSetProvider {
  @Override
  public NodeReferenceSet<?> getReferenceSet() {
    var hasSnapshot = DataModelCenter.hasSnapshot();
    var dataModels = DataModelCenter.getDataModelMap();
    if (!hasSnapshot) {
      return NodeReferenceSet.lazyIndeterminate(
          DataModelReferences.KIND,
          dataModels::get,
          String::compareToIgnoreCase,
          NodeReferenceIndeterminateReason.NOT_READY
      );
    }
    return NodeReferenceSet.lazyComplete(
        DataModelReferences.KIND,
        dataModels::get,
        String::compareToIgnoreCase
    );
  }
}
