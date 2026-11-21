package com.ouroboros.rpc.security.hmac;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.rpc.http.HttpRPCRequest;

class HmacSigningSupportTest {
  @Test
  void canonicalizePath_preservesEncodedSlashWithinSegment() {
    URI uri = URI.create("https://example.com/a%2Fb/c");

    assertEquals("/a%2Fb/c", HmacSigningSupport.canonicalizePath(uri));
  }

  @Test
  void canonicalizeQuery_preservesEncodedReservedCharactersWithinValues() {
    URI uri = URI.create("https://example.com/api?z=a%26b&x=%2F");

    assertEquals("x=%2F&z=a%26b", HmacSigningSupport.canonicalizeQuery(uri, Collections.emptyMap()));
  }

  @Test
  void canonicalizeQuery_requestQueryOverridesSameNameInUrlQuery() {
    HttpRPCRequest request = new HttpRPCRequest();
    request.setUrl("https://example.com/api?keep=1&keyword=url-value&keyword=url-second");
    request.query("keyword", "request-value");
    request.query("page", "2");

    assertEquals("keep=1&keyword=request-value&page=2", HmacSigningSupport.canonicalizeQuery(request));
  }

  @Test
  void toUri_expandsPathVariablesBeforeUriCreation() {
    HttpRPCRequest request = new HttpRPCRequest();
    request.setUrl("https://example.com/api/users/{userId}");
    request.pathVariable("userId", "42");

    assertEquals(URI.create("https://example.com/api/users/42"), HmacSigningSupport.toUri(request));
  }

  @Test
  void toUri_preservesEncodedReservedCharactersInQuery() {
    HttpRPCRequest request = new HttpRPCRequest();
    request.setUrl("https://example.com/api?z=a%26b&x=%2F");

    assertEquals(URI.create("https://example.com/api?z=a%26b&x=%2F"), HmacSigningSupport.toUri(request));
  }
}
