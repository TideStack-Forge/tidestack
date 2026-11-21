package com.ouroboros.dev.app.model;

public class RuntimeAccessViewDTO {
  private AccessView webAccess;
  private AccessView apiAccess;
  private DebugCapabilityView debugCapability;
  private BackendApiCapabilityView backendApiCapability;

  public AccessView getWebAccess() {
    return webAccess;
  }

  public void setWebAccess(AccessView webAccess) {
    this.webAccess = webAccess;
  }

  public AccessView getApiAccess() {
    return apiAccess;
  }

  public void setApiAccess(AccessView apiAccess) {
    this.apiAccess = apiAccess;
  }

  public DebugCapabilityView getDebugCapability() {
    return debugCapability;
  }

  public void setDebugCapability(DebugCapabilityView debugCapability) {
    this.debugCapability = debugCapability;
  }

  public BackendApiCapabilityView getBackendApiCapability() {
    return backendApiCapability;
  }

  public void setBackendApiCapability(BackendApiCapabilityView backendApiCapability) {
    this.backendApiCapability = backendApiCapability;
  }

  public static class AccessView {
    private String strategy;
    private String endpoint;

    public String getStrategy() {
      return strategy;
    }

    public void setStrategy(String strategy) {
      this.strategy = strategy;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }
  }

  public static class DebugCapabilityView {
    private Boolean enabled;
    private String strategy;
    private String activeSessionId;
    private Boolean sessionOpenable;
    private String sessionStatus;
    private String endpoint;
    private String clientAccessHint;
    private String bindingScope;
    private String transportType;

    public Boolean getEnabled() {
      return enabled;
    }

    public void setEnabled(Boolean enabled) {
      this.enabled = enabled;
    }

    public String getStrategy() {
      return strategy;
    }

    public void setStrategy(String strategy) {
      this.strategy = strategy;
    }

    public String getActiveSessionId() {
      return activeSessionId;
    }

    public void setActiveSessionId(String activeSessionId) {
      this.activeSessionId = activeSessionId;
    }

    public Boolean getSessionOpenable() {
      return sessionOpenable;
    }

    public void setSessionOpenable(Boolean sessionOpenable) {
      this.sessionOpenable = sessionOpenable;
    }

    public String getSessionStatus() {
      return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
      this.sessionStatus = sessionStatus;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public String getClientAccessHint() {
      return clientAccessHint;
    }

    public void setClientAccessHint(String clientAccessHint) {
      this.clientAccessHint = clientAccessHint;
    }

    public String getBindingScope() {
      return bindingScope;
    }

    public void setBindingScope(String bindingScope) {
      this.bindingScope = bindingScope;
    }

    public String getTransportType() {
      return transportType;
    }

    public void setTransportType(String transportType) {
      this.transportType = transportType;
    }
  }

  public static class BackendApiCapabilityView {
    private Boolean enabled;
    private String strategy;
    private String activeSessionId;
    private Boolean sessionOpenable;
    private String sessionStatus;
    private String endpoint;
    private String clientAccessHint;
    private String bindingScope;
    private String transportType;

    public Boolean getEnabled() {
      return enabled;
    }

    public void setEnabled(Boolean enabled) {
      this.enabled = enabled;
    }

    public String getStrategy() {
      return strategy;
    }

    public void setStrategy(String strategy) {
      this.strategy = strategy;
    }

    public String getActiveSessionId() {
      return activeSessionId;
    }

    public void setActiveSessionId(String activeSessionId) {
      this.activeSessionId = activeSessionId;
    }

    public Boolean getSessionOpenable() {
      return sessionOpenable;
    }

    public void setSessionOpenable(Boolean sessionOpenable) {
      this.sessionOpenable = sessionOpenable;
    }

    public String getSessionStatus() {
      return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
      this.sessionStatus = sessionStatus;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public String getClientAccessHint() {
      return clientAccessHint;
    }

    public void setClientAccessHint(String clientAccessHint) {
      this.clientAccessHint = clientAccessHint;
    }

    public String getBindingScope() {
      return bindingScope;
    }

    public void setBindingScope(String bindingScope) {
      this.bindingScope = bindingScope;
    }

    public String getTransportType() {
      return transportType;
    }

    public void setTransportType(String transportType) {
      this.transportType = transportType;
    }
  }
}
