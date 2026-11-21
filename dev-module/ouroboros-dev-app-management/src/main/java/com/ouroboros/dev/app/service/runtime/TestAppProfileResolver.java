package com.ouroboros.dev.app.service.runtime;

import com.ouroboros.dev.app.model.AppContainerRequest;

public interface TestAppProfileResolver {

  String resolveForCreate(AppContainerRequest request);
}
