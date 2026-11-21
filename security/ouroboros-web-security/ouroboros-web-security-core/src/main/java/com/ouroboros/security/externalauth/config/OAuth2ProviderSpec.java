package com.ouroboros.security.externalauth.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * OAuth2/OIDC provider configuration.
 * Plain data object, no Spring dependency.
 */
public class OAuth2ProviderSpec extends ProviderSpec {

  private String authorizationUri;
  private String tokenUri;
  private String userInfoUri;
  /** Optional fixed object field containing the identity payload in a userinfo response. */
  private String userInfoDataField;
  private String jwksUri;
  private String issuer;
  private String clientId;
  private String clientIdRef;
  private String clientSecret;
  private String clientSecretRef;
  private List<String> scopes = new ArrayList<>(Arrays.asList("openid", "profile", "email"));
  private String externalIdField = "sub";
  /**
   * 身份响应中承载"企业内成员标识"的字段名；仅当平台确认成员标识与登录身份同源时配置
   * （飞书 {@code open_id}）。留空表示不写入成员标识，由 provider 自己的桥接负责。
   */
  private String memberIdField;
  private boolean pkceEnabled = false;
  private String pkceMethod = "S256";

  public String getAuthorizationUri() { return authorizationUri; }
  public void setAuthorizationUri(String authorizationUri) { this.authorizationUri = authorizationUri; }

  public String getTokenUri() { return tokenUri; }
  public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }

  public String getUserInfoUri() { return userInfoUri; }
  public void setUserInfoUri(String userInfoUri) { this.userInfoUri = userInfoUri; }

  public String getUserInfoDataField() { return userInfoDataField; }
  public void setUserInfoDataField(String userInfoDataField) {
    this.userInfoDataField = userInfoDataField;
  }

  public String getJwksUri() { return jwksUri; }
  public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }

  public String getIssuer() { return issuer; }
  public void setIssuer(String issuer) { this.issuer = issuer; }

  public String getClientId() { return clientId; }
  public void setClientId(String clientId) { this.clientId = clientId; }

  public String getClientIdRef() { return clientIdRef; }
  public void setClientIdRef(String clientIdRef) { this.clientIdRef = clientIdRef; }

  public String getClientSecret() { return clientSecret; }
  public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

  public String getClientSecretRef() { return clientSecretRef; }
  public void setClientSecretRef(String clientSecretRef) { this.clientSecretRef = clientSecretRef; }

  public List<String> getScopes() { return scopes; }
  public void setScopes(List<String> scopes) { this.scopes = scopes; }

  public String getExternalIdField() { return externalIdField; }
  public void setExternalIdField(String externalIdField) { this.externalIdField = externalIdField; }

  public String getMemberIdField() { return memberIdField; }
  public void setMemberIdField(String memberIdField) { this.memberIdField = memberIdField; }

  public boolean isPkceEnabled() { return pkceEnabled; }
  public void setPkceEnabled(boolean pkceEnabled) { this.pkceEnabled = pkceEnabled; }

  public String getPkceMethod() { return pkceMethod; }
  public void setPkceMethod(String pkceMethod) { this.pkceMethod = pkceMethod; }

}
