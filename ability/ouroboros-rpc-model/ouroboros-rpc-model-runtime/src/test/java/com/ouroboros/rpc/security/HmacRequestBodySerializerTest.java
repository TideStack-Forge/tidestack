package com.ouroboros.rpc.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.hmac.HmacRequestBodySerializer;
import com.ouroboros.rpc.security.hmac.SerializedBody;

class HmacRequestBodySerializerTest {
  private final HmacRequestBodySerializer serializer = new HmacRequestBodySerializer();

  @Test
  void serializeBody_sortsJsonKeysBeforeSigning() {
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put("z", 1);
    body.put("a", true);

    SerializedBody serialized = serializer.serialize(requestWithJsonBody(body));

    assertEquals("{\"a\":true,\"z\":1}", serialized.getBodyText());
    assertEquals("application/json", serialized.getContentType());
  }

  @Test
  void serializeBody_returnsEmptyTextWhenBodyMissing() {
    SerializedBody serialized = serializer.serialize(new HttpRPCRequest());

    assertEquals("", serialized.getBodyText());
    assertEquals(null, serialized.getContentType());
  }

  @Test
  void serializeBody_marksStructuredJsonWhenContentTypeMissing() {
    HttpRPCRequest request = new HttpRPCRequest();
    request.body("z", 1);
    request.body("a", true);

    SerializedBody serialized = serializer.serialize(request);

    assertEquals("{\"a\":true,\"z\":1}", serialized.getBodyText());
    assertEquals("application/json", serialized.getContentType());
  }

  @Test
  void serializeBody_supportsNestedJsonArrayValues() {
    SerializedBody serialized = serializer.serialize(requestWithJsonBody(mapBody("items", new int[] {2, 1, 3})));

    assertEquals("{\"items\":[2,1,3]}", serialized.getBodyText());
    assertEquals("application/json", serialized.getContentType());
  }

  @Test
  void serializeBody_rejectsMultipartFormData() {
    AuthenticateException exception = assertThrows(
        AuthenticateException.class,
        () -> serializer.serialize(requestWithContentType("multipart/form-data", mapBody("fileName", "demo.txt")))
    );

    assertTrue(exception.getMessage().contains("multipart/form-data"));
  }

  @Test
  void serializeBody_rejectsFormUrlEncodedBody() {
    AuthenticateException exception = assertThrows(
        AuthenticateException.class,
        () -> serializer.serialize(requestWithContentType("application/x-www-form-urlencoded", mapBody("name", "alice")))
    );

    assertTrue(exception.getMessage().contains("application/x-www-form-urlencoded"));
  }

  @Test
  void serializeBody_rejectsNonJsonObjectWithoutSupportedContentType() {
    AuthenticateException exception = assertThrows(
        AuthenticateException.class,
        () -> serializer.serialize(requestWithContentType("text/plain", mapBody("enabled", true)))
    );

    assertTrue(exception.getMessage().contains("text/plain"));
  }

  @Test
  void serializeBody_rejectsNestedBinaryPayloadInsideJsonObject() {
    Map<String, Object> nested = new LinkedHashMap<String, Object>();
    nested.put("bytes", new byte[] {1, 2, 3});

    AuthenticateException exception = assertThrows(
        AuthenticateException.class,
        () -> serializer.serialize(requestWithJsonBody(mapBody("payload", nested)))
    );

    assertTrue(exception.getMessage().contains("binary"));
  }

  @Test
  void serializeBody_rejectsNestedFilePayloadInsideJsonList() {
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("file", new java.io.File("demo.txt"));

    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put("items", java.util.Collections.<Object>singletonList(item));

    AuthenticateException exception = assertThrows(
        AuthenticateException.class,
        () -> serializer.serialize(requestWithJsonBody(body))
    );

    assertTrue(exception.getMessage().contains("binary"));
  }

  @Test
  void serializeBody_rejectsNestedPojoPayloadInsideJsonObject() {
    AuthenticateException exception = assertThrows(
        AuthenticateException.class,
        () -> serializer.serialize(requestWithJsonBody(mapBody("payload", new DemoPayload("alice"))))
    );

    assertTrue(exception.getMessage().contains("unsupported"));
  }

  private HttpRPCRequest requestWithJsonBody(Map<String, Object> body) {
    return requestWithContentType("application/json", body);
  }

  private HttpRPCRequest requestWithContentType(String contentType, Map<String, Object> body) {
    HttpRPCRequest request = new HttpRPCRequest();
    request.header("Content-Type", contentType);
    for (Map.Entry<String, Object> entry : body.entrySet()) {
      request.body(entry.getKey(), entry.getValue());
    }
    return request;
  }

  private Map<String, Object> mapBody(String key, Object value) {
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put(key, value);
    return body;
  }

  private static class DemoPayload {
    private final String name;

    private DemoPayload(String name) {
      this.name = name;
    }

    @SuppressWarnings("unused")
    public String getName() {
      return name;
    }
  }
}
