package com.ouroboros.notification.template;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.notification.service.model.NotificationTemplateModel;
import com.ouroboros.util.Json;

/** Revisions describe complete templates; a deleted and recreated override has a different token. */
public final class NotificationTemplateRevision {
  public static final String NONE = "NONE";

  private NotificationTemplateRevision() { }

  public static String nameKey(String name) {
    if (name == null || name.isBlank() || name.length() > 255) {
      throw new BusinessException(400, "通知模板名称必须为 1 至 255 个字符");
    }
    var folded = new StringBuilder();
    name.codePoints().forEach(point -> folded.appendCodePoint(Character.toLowerCase(Character.toUpperCase(point))));
    return digest(folded.toString());
  }

  public static String defaultRevision(NotificationTemplate template) {
    return defaultRevision(snapshot(template));
  }

  public static String defaultRevision(Map<String, Object> snapshot) {
    return "sha256:" + digest(json(canonical(snapshot)));
  }

  public static String overrideRevision(NotificationTemplateModel template) {
    if (template == null) { return NONE; }
    if (template.getRevision() == null || template.getRevision() < 1) {
      throw new BusinessException(503, "[NOTIFICATION_TEMPLATE_MIGRATION_REQUIRED] 通知模板修订号尚未回填");
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(template.getId().getBytes(StandardCharsets.UTF_8))
        + ":" + template.getRevision();
  }

  /** Detaches nested channel/schema objects from Provider instances before management edits. */
  public static Map<String, Object> snapshot(NotificationTemplate template) {
    var result = new LinkedHashMap<String, Object>();
    result.put("name", template.getName()); result.put("title", template.getTitle());
    // The original Map storage and dispatcher both treat null/empty payload contracts as absent.
    result.put("payloadSchema", template.getPayloadSchema() == null ? Map.of() : template.getPayloadSchema());
    result.put("channel", template.getChannel());
    result.put("isEnabled", template.isEnabled());
    return Json.tryToMap(json(result)).getOrElseThrow(error -> new BusinessException(400, "通知模板不是有效 JSON 对象", error));
  }

  private static String json(Object value) {
    return Json.tryToJsonString(value).getOrElseThrow(error -> new BusinessException(400, "通知模板不是有效 JSON", error));
  }

  private static Object canonical(Object value) {
    if (value instanceof Map<?, ?> map) {
      var sorted = new TreeMap<String, Object>();
      map.forEach((key, nested) -> sorted.put(String.valueOf(key), canonical(nested)));
      return sorted;
    }
    if (value instanceof Collection<?> list) { return list.stream().map(NotificationTemplateRevision::canonical).toList(); }
    return value;
  }

  private static String digest(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new InternalException("无法计算通知模板修订号", error);
    }
  }
}
