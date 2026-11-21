package com.ouroboros.organizationstructure.directory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ouroboros.exception.InternalException;
import com.ouroboros.util.Json;

final class DirectoryJson {
  private static final com.fasterxml.jackson.databind.ObjectMapper JSON = Json.mapperCopy()
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  private DirectoryJson() { }
  static String encode(Object value) {
    try { return JSON.writeValueAsString(value); }
    catch (java.io.IOException error) { throw new InternalException("目录数据序列化失败", error); }
  }
  static <T> T decode(String value, Class<T> type) {
    try { return JSON.readValue(value, type); }
    catch (java.io.IOException error) { throw new InternalException("目录记录格式无效", error); }
  }
  static String digest(Object value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException error) { throw new InternalException("摘要算法不可用", error); }
  }
}
