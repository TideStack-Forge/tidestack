package com.ouroboros.resource.dev.dto;

import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.resource.util.ResourcePathUtils;

public class ResourceContentUploadRequest {

  private String scope;
  private String key;
  private String commitMsg;

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getCommitMsg() {
    return commitMsg;
  }

  public void setCommitMsg(String commitMsg) {
    this.commitMsg = commitMsg;
  }

  public ResourceScope getResourceScope() {
    return ResourceScope.fromValue(scope);
  }

  public String getScopedPath() {
    return ResourcePathUtils.toScopedResourcePath(getResourceScope(), key);
  }
}
