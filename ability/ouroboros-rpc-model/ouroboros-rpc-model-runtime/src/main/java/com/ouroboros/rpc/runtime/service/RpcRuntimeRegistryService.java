package com.ouroboros.rpc.runtime.service;

import java.util.List;
import java.util.Optional;

public interface RpcRuntimeRegistryService {

  List<RpcRuntimeRegistryItem> listModels();

  Optional<RpcRuntimeRegistryItem> getModel(String modelName);
}
