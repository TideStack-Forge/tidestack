package com.ouroboros.rpc.runtime.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.model.RpcRuntimeStatusModel;

public class RpcRuntimeRegistryItem {
  private final String modelName;
  private final String title;
  private final String group;
  private final String type;
  private final String authCredential;
  private final Map<String, Object> definitionExtraProps;
  private final RpcRuntimePolicyModel policy;
  private final RpcRuntimeStatusModel status;

  public RpcRuntimeRegistryItem(
      String modelName,
      String title,
      String type,
      String authCredential,
      Map<String, Object> definitionExtraProps,
      RpcRuntimePolicyModel policy,
      RpcRuntimeStatusModel status
  ) {
    this(modelName, title, null, type, authCredential, definitionExtraProps, policy, status);
  }

  public RpcRuntimeRegistryItem(
      String modelName,
      String title,
      String group,
      String type,
      String authCredential,
      Map<String, Object> definitionExtraProps,
      RpcRuntimePolicyModel policy,
      RpcRuntimeStatusModel status
  ) {
    this.modelName = modelName;
    this.title = title;
    this.group = group;
    this.type = type;
    this.authCredential = authCredential;
    this.definitionExtraProps = definitionExtraProps == null
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(new LinkedHashMap<>(definitionExtraProps));
    this.policy = policy;
    this.status = status;
  }

  public String getModelName() {
    return modelName;
  }

  public String getTitle() {
    return title;
  }

  public String getGroup() {
    return group;
  }

  public String getType() {
    return type;
  }

  public String getAuthCredential() {
    return authCredential;
  }

  public Map<String, Object> getDefinitionExtraProps() {
    return definitionExtraProps;
  }

  public RpcRuntimePolicyModel getPolicy() {
    return policy;
  }

  public RpcRuntimeStatusModel getStatus() {
    return status;
  }
}
