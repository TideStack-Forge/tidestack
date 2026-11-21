package com.ouroboros.rpc.security.hmac;

public final class SerializedBody {
  public static final String REQUEST_ATTRIBUTE = "rpc.serializedBody";
  private static final SerializedBody EMPTY = new SerializedBody("", null);

  private final String bodyText;
  private final String contentType;

  private SerializedBody(String bodyText, String contentType) {
    this.bodyText = bodyText == null ? "" : bodyText;
    this.contentType = contentType;
  }

  public static SerializedBody empty() {
    return EMPTY;
  }

  public static SerializedBody of(String bodyText) {
    return of(bodyText, null);
  }

  public static SerializedBody of(String bodyText, String contentType) {
    if (bodyText == null || bodyText.isEmpty()) {
      return EMPTY;
    }
    return new SerializedBody(bodyText, contentType);
  }

  public static SerializedBody json(String bodyText) {
    return of(bodyText, "application/json");
  }

  public String getBodyText() {
    return bodyText;
  }

  public String getContentType() {
    return contentType;
  }
}
