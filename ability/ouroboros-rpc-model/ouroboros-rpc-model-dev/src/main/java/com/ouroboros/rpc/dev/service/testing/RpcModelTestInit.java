package com.ouroboros.rpc.dev.service.testing;

import java.util.List;

public record RpcModelTestInit(Object model, String modelHash, String revision, String executionEnvironment,
                               List<RpcModelTestInputs.Variable> variables,
                               List<RpcModelTestInputs.ConfigItem> configItems,
                               boolean testable, boolean ready, List<String> missingConfigKeys,
                               String disabledReason, long maxDurationMs) { }
