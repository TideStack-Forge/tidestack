package com.ouroboros.dev.projectgeneration.catalog;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class CanonicalJson {

  private static final BigInteger MAX_SAFE_INTEGER = BigInteger.valueOf(9_007_199_254_740_991L);
  private static final BigInteger MIN_SAFE_INTEGER = MAX_SAFE_INTEGER.negate();
  private static final ObjectMapper MAPPER = JsonMapper.builder()
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
      .build();

  private CanonicalJson() {
  }

  public static byte[] requireCanonical(byte[] json) {
    byte[] canonical = canonicalize(json);
    if (!java.util.Arrays.equals(canonical, json)) {
      throw new IllegalArgumentException("JSON payload is not canonical JCS-1 bytes");
    }
    return canonical;
  }

  public static byte[] canonicalize(byte[] json) {
    try {
      JsonNode node = MAPPER.readTree(json);
      StringBuilder builder = new StringBuilder();
      appendCanonical(node, builder);
      return builder.toString().getBytes(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid JSON payload", e);
    }
  }

  public static JsonNode parseCanonical(byte[] json) {
    requireCanonical(json);
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid canonical JSON payload", e);
    }
  }

  private static void appendCanonical(JsonNode node, StringBuilder builder) {
    if (node == null || node.isNull()) {
      builder.append("null");
      return;
    }
    if (node.isObject()) {
      appendObject(node, builder);
      return;
    }
    if (node.isArray()) {
      appendArray(node, builder);
      return;
    }
    if (node.isTextual()) {
      appendQuoted(node.textValue(), builder);
      return;
    }
    if (node.isBoolean()) {
      builder.append(node.booleanValue() ? "true" : "false");
      return;
    }
    if (node.isIntegralNumber()) {
      appendInteger(node.bigIntegerValue(), builder);
      return;
    }
    throw new IllegalArgumentException("Only integer JSON numbers are allowed in signed payloads");
  }

  private static void appendObject(JsonNode node, StringBuilder builder) {
    builder.append('{');
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    names.sort(Comparator.naturalOrder());
    for (int i = 0; i < names.size(); i++) {
      if (i > 0) {
        builder.append(',');
      }
      String name = names.get(i);
      requireNormalized(name);
      appendQuoted(name, builder);
      builder.append(':');
      appendCanonical(node.get(name), builder);
    }
    builder.append('}');
  }

  private static void appendArray(JsonNode node, StringBuilder builder) {
    builder.append('[');
    for (int i = 0; i < node.size(); i++) {
      if (i > 0) {
        builder.append(',');
      }
      appendCanonical(node.get(i), builder);
    }
    builder.append(']');
  }

  private static void appendInteger(BigInteger value, StringBuilder builder) {
    if (value.compareTo(MIN_SAFE_INTEGER) < 0 || value.compareTo(MAX_SAFE_INTEGER) > 0) {
      throw new IllegalArgumentException("JSON integer exceeds JavaScript safe integer range");
    }
    builder.append(value);
  }

  private static void appendQuoted(String value, StringBuilder builder) {
    requireNormalized(value);
    builder.append('"');
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if (Character.isHighSurrogate(current)) {
        if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
          throw new IllegalArgumentException("Invalid UTF-16 surrogate in JSON string");
        }
        builder.append(current).append(value.charAt(i + 1));
        i++;
        continue;
      }
      if (Character.isLowSurrogate(current)) {
        throw new IllegalArgumentException("Invalid UTF-16 surrogate in JSON string");
      }
      switch (current) {
        case '"' -> builder.append("\\\"");
        case '\\' -> builder.append("\\\\");
        case '\b' -> builder.append("\\b");
        case '\t' -> builder.append("\\t");
        case '\n' -> builder.append("\\n");
        case '\f' -> builder.append("\\f");
        case '\r' -> builder.append("\\r");
        default -> {
          if (current < 0x20) {
            builder.append("\\u");
            String hex = Integer.toHexString(current);
            builder.append("0".repeat(4 - hex.length())).append(hex);
          } else {
            builder.append(current);
          }
        }
      }
    }
    builder.append('"');
  }

  private static void requireNormalized(String value) {
    if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
      throw new IllegalArgumentException("JSON strings must be Unicode NFC normalized");
    }
  }
}
