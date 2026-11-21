package com.ouroboros.enterprise.dingtalk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.callback.EnterpriseCallbackProtocol;
import com.ouroboros.enterprise.callback.EnterpriseCallbackRequest;
import com.ouroboros.enterprise.callback.VerifiedEnterpriseEvent;
import com.ouroboros.exception.ValidationException;

/** HTTP event subscription and legacy corporate callback share the same verified ingress. */
public final class DingtalkCallbackProtocol implements EnterpriseCallbackProtocol {
  private static final ObjectMapper JSON = new ObjectMapper();
  @Override public String provider() { return "dingtalk"; }

  @Override
  public VerifiedCallback verify(ResolvedEnterpriseConnection connection, EnterpriseCallbackRequest request) {
    try {
      if ((!"GET".equals(request.method()) && !"POST".equals(request.method()))
          || !provider().equals(connection.provider()) || !(connection.credential() instanceof DingtalkCredential credential)
          || credential.callbackToken() == null || credential.callbackToken().isBlank()
          || credential.encodingAesKey() == null || credential.encodingAesKey().isBlank()) {
        throw invalid();
      }
      String timestamp = request.parameters().get("timestamp");
      String nonce = request.parameters().get("nonce");
      String signature = request.parameters().get("msg_signature");
      String legacy = request.parameters().get("signature");
      if (signature != null && legacy != null && !signature.equals(legacy)) { throw invalid(); }
      if (signature == null) { signature = legacy; }
      if (timestamp == null || nonce == null || nonce.isBlank() || signature == null) { throw invalid(); }
      long rawTime = Long.parseLong(timestamp);
      long eventTime = rawTime > 100_000_000_000L ? rawTime / 1000L : rawTime;
      long now = Instant.now().getEpochSecond();
      if (eventTime < now - 300L || eventTime > now + 300L) { throw invalid(); }
      String encrypted = "GET".equals(request.method()) ? request.parameters().get("echostr")
          : JSON.readTree(request.body()).path("encrypt").asText(null);
      if (encrypted == null || encrypted.isBlank()) { throw invalid(); }
      String expected = DingtalkCrypto.sha1Signature(credential.callbackToken(), timestamp, nonce, encrypted);
      if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII))) {
        throw invalid();
      }
      String owner = owner(connection, credential);
      String plain = DingtalkCrypto.decrypt(credential.encodingAesKey(), encrypted, owner);
      if ("GET".equals(request.method())) {
        return new VerifiedCallback(List.of(), new Response("text/plain;charset=UTF-8", plain));
      }
      JsonNode root = JSON.readTree(plain);
      List<VerifiedEnterpriseEvent> events = new ArrayList<>();
      if (root.isArray()) {
        for (JsonNode item : root) { addEvent(connection, item, events); }
      } else { addEvent(connection, root, events); }
      String responseTime = String.valueOf(System.currentTimeMillis());
      String responseNonce = java.util.UUID.randomUUID().toString();
      String ack = DingtalkCrypto.encrypt(credential.encodingAesKey(), "success", owner);
      String ackSignature = DingtalkCrypto.sha1Signature(credential.callbackToken(), responseTime, responseNonce, ack);
      return new VerifiedCallback(events, new Response("application/json", JSON.writeValueAsString(Map.of(
          "msg_signature", ackSignature, "encrypt", ack, "timeStamp", responseTime, "nonce", responseNonce))));
    } catch (ValidationException ex) { throw ex; }
    catch (Exception ex) { var rejected = invalid(); rejected.addSuppressed(ex); throw rejected; }
  }

  private void addEvent(ResolvedEnterpriseConnection connection, JsonNode item, List<VerifiedEnterpriseEvent> events) {
    String type = item.path("EventType").asText(null);
    if (type == null || type.isBlank()) { throw invalid(); }
    String actualCorp = item.path("corpId").asText(item.path("CorpId").asText(null));
    if (actualCorp != null && !actualCorp.equals(connection.config().get("corpId"))) { throw invalid(); }
    if ("check_url".equals(type)) { return; }
    String payload = item.toString();
    String hash = VerifiedEnterpriseEvent.hash(payload);
    String id = item.path("EventId").asText(item.path("eventId").asText(hash));
    events.add(new VerifiedEnterpriseEvent(provider(), connection.connectionId(), id, type,
        item.path("TimeStamp").asText(null), hash, payload));
  }

  private String owner(ResolvedEnterpriseConnection connection, DingtalkCredential credential) {
    if (credential.isThirdPartyEnterprise()) { return credential.suiteKey(); }
    return "APPLICATION".equals(connection.config().get("callbackMode"))
        ? credential.appKey() : String.valueOf(connection.config().get("corpId"));
  }

  private static ValidationException invalid() { return new ValidationException("钉钉回调验证失败"); }
}
