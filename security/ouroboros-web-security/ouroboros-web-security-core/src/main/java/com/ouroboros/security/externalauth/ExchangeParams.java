package com.ouroboros.security.externalauth;

/**
 * Parameters for the exchange flow (frontend-initiated).
 * Two mutually exclusive paths: code-based or idToken-based.
 */
public class ExchangeParams {

  private final String code;
  private final String ticket;
  private final String redirectUri;
  private final String codeVerifier;
  private final String idToken;
  private final String correlationId;

  private ExchangeParams(String code, String ticket, String redirectUri, String codeVerifier,
                         String idToken, String correlationId) {
    this.code = code;
    this.ticket = ticket;
    this.redirectUri = redirectUri;
    this.codeVerifier = codeVerifier;
    this.idToken = idToken;
    this.correlationId = correlationId;
  }

  public static ExchangeParams ofCode(String code, String redirectUri, String codeVerifier) {
    return new ExchangeParams(code, null, redirectUri, codeVerifier, null, null);
  }

  public static ExchangeParams ofCode(String code, String redirectUri, String codeVerifier, String correlationId) {
    return new ExchangeParams(code, null, redirectUri, codeVerifier, null, correlationId);
  }

  public static ExchangeParams ofTicket(String ticket, String correlationId) {
    return new ExchangeParams(null, ticket, null, null, null, correlationId);
  }

  public static ExchangeParams ofIdToken(String idToken) {
    return new ExchangeParams(null, null, null, null, idToken, null);
  }

  public static ExchangeParams ofIdToken(String idToken, String correlationId) {
    return new ExchangeParams(null, null, null, null, idToken, correlationId);
  }

  public boolean isCodeExchange() { return code != null || ticket != null; }

  public String getCode() { return code; }
  public String getTicket() { return ticket; }
  public String getTicketOrCode() { return ticket != null ? ticket : code; }
  public String getRedirectUri() { return redirectUri; }
  public String getCodeVerifier() { return codeVerifier; }
  public String getIdToken() { return idToken; }
  public String getCorrelationId() { return correlationId; }
}
