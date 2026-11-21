package com.ouroboros.data.runtime.adapter;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataModelCenterScriptContextContributorTest {
  @Test
  void exposesDataModelCenterContext() {
    var contributor = new DataModelCenterScriptContextContributor();
    var context = contributor.getBindings();

    assertTrue(context.containsKey("DataModelCenter"));
    var dataModelCenter = assertInstanceOf(
        DataModelCenterScriptContextContributor.DataModelCenterContext.class,
        context.get("DataModelCenter"));
    assertNull(dataModelCenter.get("missing"));
  }
}
