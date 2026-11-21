package com.ouroboros.security.externalauth.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LDAP/AD provider configuration.
 */
public class LdapProviderSpec extends ProviderSpec {

  private List<String> urls = new ArrayList<>();
  private String baseDn;
  private String mode = "search";
  private String searchBindDn;
  private String searchBindPassword;
  private String searchBindPasswordRef;
  private String searchFilter;
  private String domain;
  private String loginIdMode = "AUTO";
  private String externalIdAttribute;
  private Map<String, String> attributeMapping = new HashMap<>();

  public List<String> getUrls() { return urls; }
  public void setUrls(List<String> urls) { this.urls = urls; }

  public String getBaseDn() { return baseDn; }
  public void setBaseDn(String baseDn) { this.baseDn = baseDn; }

  public String getMode() { return mode; }
  public void setMode(String mode) { this.mode = mode; }

  public String getSearchBindDn() { return searchBindDn; }
  public void setSearchBindDn(String searchBindDn) { this.searchBindDn = searchBindDn; }

  public String getSearchBindPassword() { return searchBindPassword; }
  public void setSearchBindPassword(String searchBindPassword) { this.searchBindPassword = searchBindPassword; }

  public String getSearchBindPasswordRef() { return searchBindPasswordRef; }
  public void setSearchBindPasswordRef(String searchBindPasswordRef) { this.searchBindPasswordRef = searchBindPasswordRef; }

  public String getSearchFilter() { return searchFilter; }
  public void setSearchFilter(String searchFilter) { this.searchFilter = searchFilter; }

  public String getDomain() { return domain; }
  public void setDomain(String domain) { this.domain = domain; }

  public String getLoginIdMode() { return loginIdMode; }
  public void setLoginIdMode(String loginIdMode) { this.loginIdMode = loginIdMode; }

  public String getExternalIdAttribute() { return externalIdAttribute; }
  public void setExternalIdAttribute(String externalIdAttribute) { this.externalIdAttribute = externalIdAttribute; }

  public Map<String, String> getAttributeMapping() { return attributeMapping; }
  public void setAttributeMapping(Map<String, String> attributeMapping) { this.attributeMapping = attributeMapping; }
}
