package com.ouroboros.enterprise.wecom;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.callback.EnterpriseCallbackProtocol;
import com.ouroboros.enterprise.callback.EnterpriseCallbackRequest;
import com.ouroboros.enterprise.callback.VerifiedEnterpriseEvent;
import com.ouroboros.exception.ValidationException;

/** Provider-only validation/decryption; no approval or directory model appears in this module. */
public final class WecomCallbackProtocol implements EnterpriseCallbackProtocol {
  @Override public String provider() { return "wecom"; }

  @Override
  public VerifiedCallback verify(ResolvedEnterpriseConnection connection, EnterpriseCallbackRequest request) {
    try {
      if ((!"GET".equals(request.method()) && !"POST".equals(request.method()))
          || !provider().equals(connection.provider()) || !(connection.credential() instanceof WecomCredential credential)
          || credential.callbackToken() == null || credential.callbackToken().isBlank()
          || credential.encodingAesKey() == null || credential.encodingAesKey().isBlank()) { throw invalid(); }
      String signature = request.parameters().get("msg_signature");
      String timestamp = request.parameters().get("timestamp");
      String nonce = request.parameters().get("nonce");
      if (signature == null || timestamp == null || nonce == null || nonce.isBlank()) { throw invalid(); }
      long eventTime = Long.parseLong(timestamp);
      long now = Instant.now().getEpochSecond();
      if (eventTime < now - 300L || eventTime > now + 300L) { throw invalid(); }
      String encrypted = "GET".equals(request.method()) ? request.parameters().get("echostr")
          : text(parseXml(request.body()), "Encrypt");
      String expected = WecomCrypto.sha1Signature(credential.callbackToken(), timestamp, nonce, encrypted);
      if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII))) {
        throw invalid();
      }
      String owner = credential.isSelfBuilt() ? String.valueOf(connection.config().get("corpId")) : credential.suiteId();
      String plain = WecomCrypto.decrypt(credential.encodingAesKey(), encrypted, owner);
      if ("GET".equals(request.method())) {
        return new VerifiedCallback(List.of(), new Response("text/plain;charset=UTF-8", plain));
      }
      Document root = parseXml(plain);
      String recipient = text(root, "ToUserName");
      if (recipient != null && !recipient.equals(connection.config().get("corpId"))) { throw invalid(); }
      String agent = text(root, "AgentID");
      if (agent != null && credential.isSelfBuilt() && !agent.equals(credential.agentId())) { throw invalid(); }
      String type = text(root, "Event");
      if (type == null) { throw invalid(); }
      String hash = VerifiedEnterpriseEvent.hash(plain);
      String id = text(root, "MsgId");
      var event = new VerifiedEnterpriseEvent(provider(), connection.connectionId(), id == null ? hash : id,
          type, text(root, "CreateTime"), hash, plain);
      return new VerifiedCallback(List.of(event), new Response("text/plain;charset=UTF-8", "success"));
    } catch (ValidationException ex) { throw ex; }
    catch (Exception ex) { var rejected = invalid(); rejected.addSuppressed(ex); throw rejected; }
  }

  public static Document parseXml(String xml) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    } catch (ValidationException ex) { throw ex; }
    catch (Exception ex) { var rejected = invalid(); rejected.addSuppressed(ex); throw rejected; }
  }

  public static String text(Document document, String name) {
    var nodes = document.getElementsByTagName(name);
    String value = nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static ValidationException invalid() { return new ValidationException("企业微信回调验证失败"); }
}
