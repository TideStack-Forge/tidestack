package com.ouroboros.security.rbac.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.authority.DefaultAuthority;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Json;

class AccessControlSettingSchemaDiscoveryTest {

  @Test
  void shouldDiscoverFieldControlAndEmbeddedFilterThroughRegisteredGenerators() {
    var authority = Json.tryToBean(DefaultAuthority.class, """
        {
          "fullName": "sales.Order.search",
          "title": "查询订单",
          "accessControlDefine": {
            "canControlFields": true,
            "controlledFields": [{"name": "amount", "label": "金额", "type": "Decimal"}],
            "filterDefine": [{
              "name": "$regionScope",
              "label": "区域",
              "settingSchema": [{
                "type": "select",
                "name": "accessControlParams.$regionScope",
                "multiple": true
              }]
            }]
          }
        }
        """).get();

    var schemas = AccessControlDefineUtils.generateAccessControlSettingSchema(authority);

    assertEquals(List.of("accessControlParams.$fieldControl", "accessControlParams.$regionScope"),
        schemas.stream().map(SchemaNode::getName).toList());
    assertEquals("select", schemas.getFirst().getType());
    assertEquals(true, schemas.getFirst().getProp("multiple"));
    assertEquals(authority.getAccessControlDefine().getControlledFields(), schemas.getFirst().getProp("options"));
  }
}
