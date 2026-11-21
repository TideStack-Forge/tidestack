package com.ouroboros.logicflow.nodes;

import com.ouroboros.data.model.DataModel;

public final class DataModelReferences {
  public static final NodeReferenceKind<DataModel> KIND =
      new NodeReferenceKind<>("data-model", DataModel.class);

  private DataModelReferences() {
  }
}
