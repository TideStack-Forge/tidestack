package com.ouroboros.dev.app.service.runtime;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.DebugKeyMaterial;

public interface DebugKeyMaterialProvider {

  Try<DebugKeyMaterial> read(String appName, String userIdentity);

  Try<DebugKeyMaterial> findByDevKey(String appName, String devKey);
}
