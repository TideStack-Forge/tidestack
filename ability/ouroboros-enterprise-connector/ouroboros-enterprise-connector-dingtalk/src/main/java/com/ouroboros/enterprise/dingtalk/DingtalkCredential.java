package com.ouroboros.enterprise.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.EnterpriseCredential;

/**
 * DingTalk credential resolved from credentialRef. Values are never exposed by toString().
 */
public record DingtalkCredential(
    String appKey,
    String appSecret,
    String suiteKey,
    String suiteSecret,
    String suiteTicket,
    String callbackToken,
    String encodingAesKey) implements EnterpriseCredential {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static DingtalkCredential internalApp(
      String appKey,
      String appSecret,
      String callbackToken,
      String encodingAesKey) {
    return new DingtalkCredential(appKey, appSecret, null, null, null, callbackToken, encodingAesKey);
  }

  public static DingtalkCredential thirdPartyEnterprise(
      String suiteKey,
      String suiteSecret,
      String suiteTicket,
      String callbackToken,
      String encodingAesKey) {
    return new DingtalkCredential(null, null, suiteKey, suiteSecret, suiteTicket, callbackToken, encodingAesKey);
  }

  public static DingtalkCredential fromSecretPayload(String payload) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(payload);
      String appKey = optional(root, "appKey");
      String appSecret = optional(root, "appSecret");
      String suiteKey = optional(root, "suiteKey");
      String suiteSecret = optional(root, "suiteSecret");
      String suiteTicket = optional(root, "suiteTicket");
      boolean internalSelected = hasAny(appKey, appSecret);
      boolean thirdPartySelected = hasAny(suiteKey, suiteSecret, suiteTicket);
      if (internalSelected == thirdPartySelected) {
        throw new IllegalArgumentException(
            "DingTalk credential requires exactly one application credential shape");
      }
      if (internalSelected && !hasInternalCredential(appKey, appSecret)) {
        throw new IllegalArgumentException(
            "DingTalk internal application credential requires appKey/appSecret");
      }
      if (thirdPartySelected
          && !hasThirdPartyEnterpriseCredential(suiteKey, suiteSecret, suiteTicket)) {
        throw new IllegalArgumentException(
            "DingTalk third-party enterprise credential requires suiteKey/suiteSecret/suiteTicket");
      }
      return new DingtalkCredential(
          appKey,
          appSecret,
          suiteKey,
          suiteSecret,
          suiteTicket,
          optional(root, "callbackToken"),
          optional(root, "encodingAesKey"));
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("DingTalk credential payload must be a JSON object", ex);
    }
  }

  /** 应用形态由凭据形状决定，不再由普通连接配置重复声明。 */
  public boolean isThirdPartyEnterprise() {
    return suiteKey != null && !suiteKey.isBlank()
        && suiteSecret != null && !suiteSecret.isBlank()
        && suiteTicket != null && !suiteTicket.isBlank();
  }

  @Override
  public java.util.Map<String, String> applicationIdentity() {
    return isThirdPartyEnterprise() ? java.util.Map.of("suiteKey", suiteKey) : java.util.Map.of("appKey", appKey);
  }

  @Override
  public String toString() {
    return "DingtalkCredential[appKey=****, appSecret=****, suiteKey=****, suiteSecret=****, "
        + "suiteTicket=****, callbackToken=****, encodingAesKey=****]";
  }



  private static String optional(JsonNode root, String field) {
    String value = root.path(field).asText(null);
    return value == null || value.isBlank() ? null : value;
  }

  private static boolean hasInternalCredential(String appKey, String appSecret) {
    return appKey != null && appSecret != null;
  }

  private static boolean hasAny(String... values) {
    for (String value : values) {
      if (value != null) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasThirdPartyEnterpriseCredential(
      String suiteKey,
      String suiteSecret,
      String suiteTicket) {
    return suiteKey != null && suiteSecret != null && suiteTicket != null;
  }
}
