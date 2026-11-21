package com.ouroboros.rpc;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.ouroboros.util.Maps;

public class RPCRequest {
  private Map<String, Map<String, Object>> parts;
  private Map<String, Object> attributes;

  public RPCRequest() {
    this.parts = new TreeMap<>(String::compareToIgnoreCase);
    this.attributes = new TreeMap<>(String::compareToIgnoreCase);
  }

  public RPCRequest(RPCRequest request) {
    this.parts = Maps.deepClone(request.parts, () -> new TreeMap<>(String::compareToIgnoreCase));
    this.attributes = Maps.deepClone(request.attributes, () -> new TreeMap<>(String::compareToIgnoreCase));
  }

  public Map<String, Object> getPart(String partName) {
    return this.parts.get(partName);
  }

  public RPCRequest setToPart(String partName, String key, Object value) {
    Map<String, Object> part = this.parts.get(partName);
    if (part == null) {
      part = new HashMap<>();
      this.parts.put(partName, part);
    }
    part.put(key, value);
    return this;
  }

  public RPCRequest removeFromPart(String partName, String key) {
    Map<String, Object> part = this.parts.get(partName);
    if (part != null) {
      part.remove(key);
    }
    return this;
  }

  public RPCRequest removePart(String partName) {
    this.parts.remove(partName);
    return this;
  }

  public RPCRequest setAttribute(String key, Object value) {
    this.attributes.put(key, value);
    return this;
  }

  public RPCRequest removeAttribute(String key) {
    this.attributes.remove(key);
    return this;
  }

  public Object getAttribute(String key) {
    return this.attributes.get(key);
  }

  public Object getAttribute(String key, Object defaultValue) {
    Object value = this.attributes.get(key);
    if (value == null) {
      setAttribute(key, defaultValue);
      return defaultValue;
    }
    return value;
  }
}
