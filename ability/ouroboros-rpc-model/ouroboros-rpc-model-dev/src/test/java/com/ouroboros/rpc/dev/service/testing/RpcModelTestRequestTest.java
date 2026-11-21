package com.ouroboros.rpc.dev.service.testing;

import java.util.Map;

import org.junit.jupiter.api.Test;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class RpcModelTestRequestTest {
  @Test
  void initializationAcceptsOnlyStringConfigurationOverrides() throws Exception {
    var mapper = Json.mapperCopy();
    assertThrows(IllegalArgumentException.class, () -> RpcModelTestInitRequest.parse(null));
    for (String body : new String[]{"[]", "{\"url\":\"http://other\"}", "{\"variables\":{}}",
        "{\"revision\":\"r\"}", "{\"configValues\":null}", "{\"configValues\":[]}", "{\"configValues\":{\"key\":1}}"}) {
      assertThrows(Exception.class, () -> mapper.readValue(body, RpcModelTestInitRequest.class), body);
    }
    assertTrue(mapper.readValue("{}", RpcModelTestInitRequest.class).configValues().isEmpty());
    assertEquals(Map.of("key", ""), mapper.readValue("{\"configValues\":{\"key\":\"\"}}", RpcModelTestInitRequest.class).configValues());
  }
  @Test
  void requestBindingRejectsTargetsOptionsUnknownFieldsAndWrongInputTypes() {
    var mapper = Json.mapperCopy();
    for (String body : new String[]{"[]", "{}", "{\"revision\":1}",
        "{\"revision\":\"r\",\"url\":\"http://other\"}", "{\"revision\":\"r\",\"options\":{}}",
        "{\"revision\":\"r\",\"variables\":[]}", "{\"revision\":\"r\",\"configValues\":null}",
        "{\"revision\":\"r\",\"configValues\":{\"a\":1}}", "{\"revision\":\"r\",\"configValues\":{\"a\":null}}"}) {
      assertThrows(Exception.class, () -> mapper.readValue(body, RpcModelTestRequest.class), body);
    }
  }

  @Test
  void requestBindingPreservesFalseZeroNullObjectsAndExplicitEmptyOverrides() throws Exception {
    var result = Json.mapperCopy().readValue("{\"revision\":\"r\",\"variables\":{\"flag\":false,\"page\":0,\"nothing\":null,\"order\":{\"id\":1}},\"configValues\":{\"empty\":\"\"}}",
        RpcModelTestRequest.class);
    assertEquals(false, result.variables().get("flag")); assertEquals(0, result.variables().get("page"));
    assertTrue(result.variables().containsKey("nothing")); assertNull(result.variables().get("nothing"));
    assertEquals(Map.of("id", 1), result.variables().get("order"));
    assertEquals("", result.configValues().get("empty"));
    var empty = Json.mapperCopy().readValue("{\"revision\":\"r\"}", RpcModelTestRequest.class);
    assertTrue(empty.variables().isEmpty()); assertTrue(empty.configValues().isEmpty());
  }
}
