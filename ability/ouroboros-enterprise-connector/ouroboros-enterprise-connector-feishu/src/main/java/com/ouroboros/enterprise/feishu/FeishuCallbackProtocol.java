package com.ouroboros.enterprise.feishu;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.callback.EnterpriseCallbackProtocol;
import com.ouroboros.enterprise.callback.EnterpriseCallbackRequest;
import com.ouroboros.enterprise.callback.VerifiedEnterpriseEvent;
import com.ouroboros.exception.ValidationException;

/** Verification of Feishu event subscriptions is independent of any business event translator. */
public final class FeishuCallbackProtocol implements EnterpriseCallbackProtocol {
  private static final ObjectMapper JSON = new ObjectMapper();
  @Override public String provider() { return "feishu"; }

  @Override
  public VerifiedCallback verify(ResolvedEnterpriseConnection connection, EnterpriseCallbackRequest request) {
    try {
      if (!"POST".equals(request.method()) || !provider().equals(connection.provider())
          || !(connection.credential() instanceof FeishuCredential credential)
          || credential.verificationToken() == null || credential.verificationToken().isBlank()) { throw invalid(); }
      JsonNode outer = JSON.readTree(request.body());
      String plain = credential.encryptKey() == null ? request.body()
          : FeishuCrypto.decrypt(credential.encryptKey(), outer.path("encrypt").asText());
      JsonNode root = JSON.readTree(plain);
      boolean challenge = "url_verification".equals(root.path("type").asText());
      if (credential.encryptKey() != null && !challenge) {
        String timestamp = request.header("X-Lark-Request-Timestamp");
        String nonce = request.header("X-Lark-Request-Nonce");
        String signature = request.header("X-Lark-Signature");
        if (timestamp == null || nonce == null || nonce.isBlank() || signature == null) { throw invalid(); }
        long time = Long.parseLong(timestamp);
        long now = Instant.now().getEpochSecond();
        if (time < now - 300L || time > now + 300L || !equal(signature,
            FeishuCrypto.signature(timestamp, nonce, credential.encryptKey(), request.body()))) { throw invalid(); }
      }
      JsonNode header = root.path("header");
      String token = root.path("token").asText(header.path("token").asText(null));
      if (!equal(credential.verificationToken(), token)) { throw invalid(); }
      String appId = header.path("app_id").asText(root.path("app_id").asText(null));
      if (appId != null && !credential.appId().equals(appId)) { throw invalid(); }
      if (challenge) {
        String value = root.path("challenge").asText(null);
        if (value == null || value.isBlank()) { throw invalid(); }
        return new VerifiedCallback(List.of(), new Response("application/json", JSON.writeValueAsString(Map.of("challenge", value))));
      }
      String type = header.path("event_type").asText(root.path("event").path("type").asText(root.path("type").asText(null)));
      if (type == null || type.isBlank() || type.startsWith("card.action.")) { throw invalid(); }
      String hash = VerifiedEnterpriseEvent.hash(plain);
      String id = header.path("event_id").asText(root.path("uuid").asText(hash));
      var event = new VerifiedEnterpriseEvent(provider(), connection.connectionId(), id, type,
          header.path("create_time").asText(root.path("ts").asText(null)), hash, plain);
      return new VerifiedCallback(List.of(event), new Response("application/json", "{\"success\":true}"));
    } catch (ValidationException ex) { throw ex; }
    catch (Exception ex) { var rejected = invalid(); rejected.addSuppressed(ex); throw rejected; }
  }

  private static boolean equal(String left, String right) {
    return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }

  private static ValidationException invalid() { return new ValidationException("飞书回调验证失败"); }
}
