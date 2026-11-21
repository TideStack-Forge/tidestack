package com.ouroboros.rpc.runtime.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ouroboros.rpc.RPCModelDefinition;
import com.ouroboros.rpc.RPCModelDefinitionCenter;
import com.ouroboros.rpc.runtime.service.RpcRuntimePolicyService;
import com.ouroboros.rpc.runtime.service.RpcRuntimeRegistryItem;
import com.ouroboros.rpc.runtime.service.RpcRuntimeRegistryService;
import com.ouroboros.rpc.runtime.service.RpcRuntimeStatusService;

@Service
public class RpcRuntimeRegistryServiceImpl implements RpcRuntimeRegistryService {
  private final RpcRuntimePolicyService policyService;
  private final RpcRuntimeStatusService statusService;

  public RpcRuntimeRegistryServiceImpl(
      RpcRuntimePolicyService policyService,
      RpcRuntimeStatusService statusService
  ) {
    this.policyService = policyService;
    this.statusService = statusService;
  }

  @Override
  public List<RpcRuntimeRegistryItem> listModels() {
    return RPCModelDefinitionCenter.getAllDefinitions().values().stream()
        .sorted(Comparator.comparing(RPCModelDefinition::getName, String.CASE_INSENSITIVE_ORDER))
        .map(this::toItem)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<RpcRuntimeRegistryItem> getModel(String modelName) {
    return RPCModelDefinitionCenter.getDefinition(modelName).map(this::toItem);
  }

  private RpcRuntimeRegistryItem toItem(RPCModelDefinition definition) {
    return new RpcRuntimeRegistryItem(
        definition.getName(),
        definition.getTitle(),
        definition.getGroup(),
        definition.getType(),
        definition.getAuthCredential(),
        definition.getExtraProps(),
        policyService.getPolicy(definition.getName()),
        statusService.getStatus(definition.getName())
    );
  }
}
