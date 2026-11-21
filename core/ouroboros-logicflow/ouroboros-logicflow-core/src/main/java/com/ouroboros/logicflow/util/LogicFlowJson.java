package com.ouroboros.logicflow.util;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vavr.control.Try;

public final class LogicFlowJson {
  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private LogicFlowJson() {
  }

  public static Map<String, Object> toMap(CharSequence json) {
    return tryToMap(json).getOrElse(Collections::emptyMap);
  }

  public static Try<Map<String, Object>> tryToMap(CharSequence json) {
    return Try.of(() -> OBJECT_MAPPER.readValue(json.toString(), Map.class));
  }

  public static List<Object> toList(CharSequence json) {
    return tryToList(json).getOrElse(Collections::emptyList);
  }

  public static Try<List<Object>> tryToList(CharSequence json) {
    return Try.of(() -> OBJECT_MAPPER.readValue(json.toString(), List.class));
  }

  public static <T> T toBean(Class<T> type, CharSequence json) {
    return tryToBean(type, json).getOrElse((T) null);
  }

  public static <T> Try<T> tryToBean(Class<T> type, CharSequence json) {
    return Try.of(() -> OBJECT_MAPPER.readValue(json.toString(), type));
  }

  public static <T> Try<T> tryToBean(Class<T> type, InputStream inputStream) {
    return Try.of(() -> OBJECT_MAPPER.readValue(inputStream, type));
  }
}
