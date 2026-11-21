package com.ouroboros.ability.auth.oidc;

import java.util.ArrayList;
import java.util.List;

/** The canonical configuration value shared by management and the protocol repository. */
public class OidcClientSpec {
  public enum ClientType { PUBLIC, CONFIDENTIAL }
  public enum ConsentMode { EXPLICIT, ADMIN_APPROVED }
  private String clientId;
  private String registrationId;
  private String clientName;
  private String applicationType = "web";
  public String getApplicationType() { return applicationType; }
  public void setApplicationType(String value) { applicationType = value; }
  private List<String> grantTypes;
  private Registration registration;
  public record Registration(String source, long createdAt, String initialAccessTokenId) { }
  public List<String> getGrantTypes() { return grantTypes; }
  public void setGrantTypes(List<String> value) { grantTypes = value; }
  public Registration getRegistration() { return registration; }
  public void setRegistration(Registration value) { registration = value; }
  @com.fasterxml.jackson.annotation.JsonIgnore
  public List<String> effectiveGrantTypes() {
    return grantTypes == null ? (clientType == ClientType.CONFIDENTIAL
        ? List.of("authorization_code", "refresh_token") : List.of("authorization_code")) : grantTypes;
  }
  private ClientType clientType = ClientType.PUBLIC;
  private String clientSecretHash;
  private List<String> introspectionResources = new ArrayList<>();
  private boolean enabled = true;
  private List<String> redirectUris = new ArrayList<>();
  private IdentitySettings oidc = new IdentitySettings();
  private ApiSettings apiAuthorization = new ApiSettings();
  private ConsentPolicy consentPolicy = new ConsentPolicy();

  public String getClientId() { return clientId; }
  public void setClientId(String value) { clientId = value; }
  public String getRegistrationId() { return registrationId; }
  public void setRegistrationId(String value) { registrationId = value; }
  public String getClientName() { return clientName; }
  public void setClientName(String value) { clientName = value; }
  public ClientType getClientType() { return clientType; }
  public void setClientType(ClientType value) { clientType = value; }
  public String getClientSecretHash() { return clientSecretHash; }
  public void setClientSecretHash(String value) { clientSecretHash = value; }
  public List<String> getIntrospectionResources() { return introspectionResources; }
  public void setIntrospectionResources(List<String> value) { introspectionResources = value; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean value) { enabled = value; }
  public List<String> getRedirectUris() { return redirectUris; }
  public void setRedirectUris(List<String> value) { redirectUris = value; }
  public IdentitySettings getOidc() { return oidc; }
  public void setOidc(IdentitySettings value) { oidc = value; }
  public ApiSettings getApiAuthorization() { return apiAuthorization; }
  public void setApiAuthorization(ApiSettings value) { apiAuthorization = value; }
  public ConsentPolicy getConsentPolicy() { return consentPolicy; }
  public void setConsentPolicy(ConsentPolicy value) { consentPolicy = value; }

  public static class IdentitySettings {
    private boolean enabled = true;
    private List<String> scopes = new ArrayList<>(List.of("openid", "profile", "email"));
    private List<String> postLogoutRedirectUris = new ArrayList<>();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> value) { scopes = value; }
    public List<String> getPostLogoutRedirectUris() { return postLogoutRedirectUris; }
    public void setPostLogoutRedirectUris(List<String> value) { postLogoutRedirectUris = value; }
  }

  public static class ApiSettings {
    private boolean enabled;
    private PermissionBoundary allowedBoundary = new PermissionBoundary();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public PermissionBoundary getAllowedBoundary() { return allowedBoundary; }
    public void setAllowedBoundary(PermissionBoundary value) { allowedBoundary = value; }
  }

  /** Permissions only: user role data constraints are evaluated by the original security path. */
  public static class PermissionBoundary {
    private List<String> permissions = new ArrayList<>();
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> value) { permissions = value; }
  }

  public static class ConsentPolicy {
    private ConsentMode mode = ConsentMode.EXPLICIT;
    public ConsentMode getMode() { return mode; }
    public void setMode(ConsentMode value) { mode = value; }
  }
}
