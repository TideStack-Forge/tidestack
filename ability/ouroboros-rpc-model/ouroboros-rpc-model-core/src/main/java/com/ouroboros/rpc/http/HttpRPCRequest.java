package com.ouroboros.rpc.http;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.ouroboros.rpc.RPCRequest;

public class HttpRPCRequest extends RPCRequest {
  public HttpRPCRequest() {
    super();
  }

  public HttpRPCRequest(RPCRequest request) {
    super(request);
  }

  public Map<String, Object> getPart(HttpRPCRequestParameterDefine.InScope inScope) {
    Map<String, Object> part = getPart(inScope.getValue());
    return Objects.isNull(part) ? Collections.emptyMap() : part;
  }

  public RPCRequest setToPart(HttpRPCRequestParameterDefine.InScope inScope, String key, Object value) {
    return this.setToPart(inScope.getValue(), key, value);
  }

  public RPCRequest header(String key, String value) {
    return this.setToPart(HttpRPCRequestParameterDefine.InScope.HEADER, key, value);
  }

  public RPCRequest removeHeader(String key) {
    return this.removeFromPart(HttpRPCRequestParameterDefine.InScope.HEADER.getValue(), key);
  }

  public RPCRequest body(String key, Object value) {
    return this.setToPart(HttpRPCRequestParameterDefine.InScope.BODY, key, value);
  }

  public RPCRequest removeBody(String key) {
    return this.removeFromPart(HttpRPCRequestParameterDefine.InScope.BODY.getValue(), key);
  }

  public RPCRequest query(String key, String value) {
    return this.setToPart(HttpRPCRequestParameterDefine.InScope.QUERY, key, value);
  }

  public RPCRequest removeQuery(String key) {
    return removeFromPart(HttpRPCRequestParameterDefine.InScope.QUERY.getValue(), key);
  }

  public RPCRequest cookie(String key, String value) {
    return this.setToPart(HttpRPCRequestParameterDefine.InScope.COOKIE, key, value);
  }

  public RPCRequest removeCookie(String key) {
    return removeFromPart(HttpRPCRequestParameterDefine.InScope.COOKIE.getValue(), key);
  }

  public RPCRequest pathVariable(String key, String value) {
    return this.setToPart(HttpRPCRequestParameterDefine.InScope.PATH, key, value);
  }

  public RPCRequest removePathVariable(String key) {
    return removeFromPart(HttpRPCRequestParameterDefine.InScope.PATH.getValue(), key);
  }

  public RPCRequest setMethod(String method) {
    return this.setAttribute("method", method);
  }

  public String getMethod() {
    return (String) this.getAttribute("method");
  }

  public String getUrl() {
    return (String) this.getAttribute("url");
  }

  public RPCRequest setUrl(String url) {
    return this.setAttribute("url", url);
  }

  public Map<String, String> getPartAsStringMap(HttpRPCRequestParameterDefine.InScope inScope) {
    return getPart(inScope)
        .entrySet()
        .stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> Objects.isNull(entry.getValue()) ? "" : entry.getValue().toString(),
            (v1, v2) -> v2));
  }

  public Map<String, String> getHeaders() {
    return getPartAsStringMap(HttpRPCRequestParameterDefine.InScope.HEADER);
  }

  public Map<String, Object> getBody() {
    return getPart(HttpRPCRequestParameterDefine.InScope.BODY);
  }

  public Map<String, String> getQuery() {
    return getPartAsStringMap(HttpRPCRequestParameterDefine.InScope.QUERY);
  }

  public Map<String, String> getCookies() {
    return getPartAsStringMap(HttpRPCRequestParameterDefine.InScope.COOKIE);
  }

  public Map<String, String> getPathVariables() {
    return getPartAsStringMap(HttpRPCRequestParameterDefine.InScope.PATH);
  }
}
