package com.ouroboros.security.externalauth.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration spec for CAS protocol providers.
 * Plain data class, no Spring dependency.
 */
public class CasProviderSpec extends ProviderSpec {

    private String casBaseUrl;
    private String validatePath = "/serviceValidate";
    private String externalIdField;
    private Map<String, String> attributeMapping = new HashMap<>();

    public String getCasBaseUrl() { return casBaseUrl; }
    public void setCasBaseUrl(String casBaseUrl) { this.casBaseUrl = casBaseUrl; }

    public String getValidatePath() { return validatePath; }
    public void setValidatePath(String validatePath) { this.validatePath = validatePath; }

    public String getExternalIdField() { return externalIdField; }
    public void setExternalIdField(String externalIdField) { this.externalIdField = externalIdField; }

    public Map<String, String> getAttributeMapping() { return attributeMapping; }
    public void setAttributeMapping(Map<String, String> attributeMapping) { this.attributeMapping = attributeMapping; }
}
