package com.ouroboros.enterprise.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.EnterpriseCredential;

/** Feishu internal application credential resolved from credentialRef. */
public record FeishuCredential(
    String appId,
    String appSecret,
    String verificationToken,
    String encryptKey) implements EnterpriseCredential {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static FeishuCredential fromSecretPayload(String payload) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(payload);
      return new FeishuCredential(
          required(root, "appId"),
          required(root, "appSecret"),
          optional(root, "verificationToken"),
          optional(root, "encryptKey"));
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Feishu credential payload must be a JSON object", ex);
    }
  }

  @Override
  public java.util.Map<String, String> applicationIdentity() {
    return java.util.Map.of("appId", appId);
  }

  @Override
  public String toString() {
    return "FeishuCredential[appId=****, appSecret=****, verificationToken=****, encryptKey=****]";
  }

  private static String required(JsonNode root, String field) {
    String value = optional(root, field);
    if (value == null) {
      throw new IllegalArgumentException("Feishu credential missing field: " + field);
    }
    return value;
  }

  private static String optional(JsonNode root, String field) {
    String value = root.path(field).asText(null);
    return value == null || value.isBlank() ? null : value;
  }
}
