package com.ouroboros.enterprise.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.EnterpriseCredential;

/** Enterprise WeChat credentials. The credential shape selects the actual application protocol. */
public record WecomCredential(
    String suiteId,
    String suiteSecret,
    String suiteTicket,
    String permanentCode,
    String callbackToken,
    String encodingAesKey,
    String agentId,
    String secret) implements EnterpriseCredential {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public WecomCredential(String suiteId, String suiteSecret, String suiteTicket, String permanentCode,
      String callbackToken, String encodingAesKey) {
    this(suiteId, suiteSecret, suiteTicket, permanentCode, callbackToken, encodingAesKey, null, null);
  }

  public static WecomCredential selfBuilt(String agentId, String secret, String callbackToken, String encodingAesKey) {
    return new WecomCredential(null, null, null, null, callbackToken, encodingAesKey, agentId, secret);
  }

  public boolean isSelfBuilt() { return agentId != null && !agentId.isBlank(); }

  public static WecomCredential fromSecretPayload(String payload) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(payload);
      if (root == null || !root.isObject()) { throw new IllegalArgumentException("WeCom credential must be an object"); }
      boolean selfBuilt = optional(root, "agentId") != null || optional(root, "secret") != null;
      boolean thirdParty = optional(root, "suiteId") != null || optional(root, "suiteSecret") != null
          || optional(root, "suiteTicket") != null || optional(root, "permanentCode") != null;
      if (selfBuilt == thirdParty) { throw new IllegalArgumentException("WeCom requires exactly one application credential shape"); }
      if (selfBuilt) {
        String agentId = required(root, "agentId");
        if (!agentId.matches("[1-9][0-9]*")) { throw new IllegalArgumentException("WeCom agentId must be a positive integer"); }
        return selfBuilt(agentId, required(root, "secret"), optional(root, "callbackToken"), optional(root, "encodingAesKey"));
      }
      return new WecomCredential(
          required(root, "suiteId"),
          required(root, "suiteSecret"),
          required(root, "suiteTicket"),
          required(root, "permanentCode"),
          optional(root, "callbackToken"),
          optional(root, "encodingAesKey"));
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("WeCom credential payload must be a JSON object", ex);
    }
  }

  @Override
  public java.util.Map<String, String> applicationIdentity() {
    return isSelfBuilt() ? java.util.Map.of("agentId", agentId) : java.util.Map.of("suiteId", suiteId);
  }

  @Override
  public String toString() {
    return "WecomCredential[suiteId=****, suiteSecret=****, suiteTicket=****, "
        + "permanentCode=****, callbackToken=****, encodingAesKey=****, agentId=****, secret=****]";
  }

  private static String required(JsonNode root, String field) {
    String value = optional(root, field);
    if (value == null) {
      throw new IllegalArgumentException("WeCom credential missing field: " + field);
    }
    return value;
  }

  private static String optional(JsonNode root, String field) {
    String value = root.path(field).asText(null);
    return value == null || value.isBlank() ? null : value;
  }
}
