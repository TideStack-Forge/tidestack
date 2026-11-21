package com.ouroboros.businessmodel.util;

import java.util.Collections;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

import com.ouroboros.uischema.schema.SchemaNode;

public class TestPrototypeTemplate {
  @Test
  public void testProcess() {
    var obj = Collections.singletonMap("aaa", 1);
    var params = new HashMap<String, Object>();
//    params.put("operations", Collections.emptyList());
    params.put("exportExcel", false);
    var str = PrototypeTemplate.generate("ui-schema/crudTable", params, SchemaNode.class);
    Assert.assertNotNull(str);
  }
}
