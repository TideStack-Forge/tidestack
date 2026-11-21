package com.ouroboros.rpc;

import java.util.Set;

/** Field languages shared by execution and authoring; resource identities and schemas are not templates. */
public final class RpcFieldSemantics {
  public enum Language { CONFIGURATION, TEMPLATE, JSON_PATH, NONE }

  private RpcFieldSemantics() {}

  public static Language language(String resource, String credentialType, String pointer) {
    if ("rpc-model".equals(resource)) {
      if (pointer.equals("/request/url") || pointer.matches("/request/parameters/\\d+/value")) return Language.TEMPLATE;
      if (pointer.startsWith("/request/") || Set.of("/authCredential", "/timeout", "/retry").contains(pointer)) return Language.CONFIGURATION;
      return Language.NONE;
    }
    if (!"rpc-auth".equals(resource)) return Language.NONE;
    return switch (credentialType == null ? "" : credentialType.toLowerCase(java.util.Locale.ROOT)) {
      case "httpbasic" -> pointer.matches("/httpBasic/(username|password)") ? Language.TEMPLATE : Language.NONE;
      case "plaincredentials" -> pointer.matches("/plainCredentials/credentials(?:/\\d+)?/value") ? Language.TEMPLATE
          : pointer.startsWith("/plainCredentials/") ? Language.CONFIGURATION : Language.NONE;
      case "accesstoken" -> pointer.matches("/accessToken/customPlacement(?:/\\d+)?/value") ? Language.TEMPLATE
          : Set.of("/accessToken/accessTokenPath", "/accessToken/expiresConfig/expiresPath").contains(pointer) ? Language.JSON_PATH
          : pointer.startsWith("/accessToken/") ? Language.CONFIGURATION : Language.NONE;
      case "hmacsignature" -> pointer.matches("/hmacSignature/(accessKey|secretKey|securityToken|region|service|action|version|nonce)") ? Language.TEMPLATE
          : pointer.startsWith("/hmacSignature/") ? Language.CONFIGURATION : Language.NONE;
      default -> Language.NONE;
    };
  }
}
