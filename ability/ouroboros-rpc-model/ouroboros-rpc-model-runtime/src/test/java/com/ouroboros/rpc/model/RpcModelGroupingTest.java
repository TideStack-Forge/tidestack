package com.ouroboros.rpc.model;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.rpc.RPCModelDefinition;
import com.ouroboros.rpc.metadata.RPCModelMetadata;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class RpcModelGroupingTest {
  @Test
  void fileMetadataAndProgrammaticMetadataProduceTheSameGroupedDefinition() {
    var fromFile = Json.toBean(RPCModelMetadata.class,
        """
        {"name":"lookup","title":"查询","type":"OpenAPI","group":" 钉钉 / 通讯录 "}
        """);
    var fromCode = new RPCModelMetadata();
    fromCode.setName("lookup");
    fromCode.setTitle("查询");
    fromCode.setType("OpenAPI");
    fromCode.setGroup("钉钉/通讯录");
    var builder = new OpenApiRpcModelDefinitionBuilder();
    assertEquals("钉钉/通讯录", builder.build(fromFile).getGroup());
    assertEquals(builder.build(fromCode).getGroup(), builder.build(fromFile).getGroup());
    assertEquals("lookup", builder.build(fromFile).getName());
    assertEquals("OpenAPI", builder.build(fromFile).getType());
  }

  @Test
  void ungroupedAndLiteralGroupsRemainValidAndChangingGroupPreservesInvocationIdentity() {
    var metadata = Json.toBean(RPCModelMetadata.class,
        "{\"name\":\"lookup\",\"title\":\"查询\",\"type\":\"OpenAPI\"}");
    var builder = new OpenApiRpcModelDefinitionBuilder();
    assertNull(builder.build(metadata).getGroup());
    metadata.setGroup("旧分组");
    assertEquals("旧分组", builder.build(metadata).getGroup());
    metadata.setGroup("业务/订单");
    assertEquals("lookup", builder.build(metadata).getName());
    metadata.setGroup("  ");
    assertNull(builder.build(metadata).getGroup());
    assertNull(new RPCModelDefinition.Simple("old", "旧模型", "OpenAPI", null, null).getGroup());
  }

  @Test
  void emptyPathSegmentsAreRejectedBeforeReplacingAValidGroup() {
    var metadata = new RPCModelMetadata();
    metadata.setGroup("业务/订单");
    for (var invalid : new String[]{"/业务", "业务/", "业务//订单", "业务/ /订单"}) {
      assertThrows(BusinessException.class, () -> metadata.setGroup(invalid));
      assertEquals("业务/订单", metadata.getGroup());
    }
  }
}
