package com.ouroboros.ability.auth.oidc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Issuer and signing material are deployment identity; relying parties are runtime configuration. */
@ConfigurationProperties("ouroboros.security.oidc")
public class OidcServerProperties {
  private boolean enabled;
  private String issuer;
  private String jwkSetRef;
  private Duration accessTokenTimeToLive = Duration.ofMinutes(5);
  private Duration refreshTokenTimeToLive = Duration.ofHours(8);
  private List<DeploymentClient> clients = new ArrayList<>();

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean value) { enabled = value; }
  public String getIssuer() { return issuer; }
  public void setIssuer(String issuer) { this.issuer = issuer; }
  public String getJwkSetRef() { return jwkSetRef; }
  public void setJwkSetRef(String jwkSetRef) { this.jwkSetRef = jwkSetRef; }
  public Duration getAccessTokenTimeToLive() { return accessTokenTimeToLive; }
  public void setAccessTokenTimeToLive(Duration value) { accessTokenTimeToLive = value; }
  public Duration getRefreshTokenTimeToLive() { return refreshTokenTimeToLive; }
  public void setRefreshTokenTimeToLive(Duration value) { refreshTokenTimeToLive = value; }
  public List<DeploymentClient> getClients() { return clients; }
  public void setClients(List<DeploymentClient> clients) { this.clients = clients; }

  /** Legacy Spring input is normalized once into the canonical unified-configuration value. */
  public static class DeploymentClient extends OidcClientSpec {
    private String clientSecretRef;
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getClientSecretRef() { return clientSecretRef; }
    public void setClientSecretRef(String value) { clientSecretRef = value; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<String> getScopes() { return getOidc().getScopes(); }
    public void setScopes(List<String> value) { getOidc().setScopes(value); }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<String> getPostLogoutRedirectUris() { return getOidc().getPostLogoutRedirectUris(); }
    public void setPostLogoutRedirectUris(List<String> value) { getOidc().setPostLogoutRedirectUris(value); }
  }
}
