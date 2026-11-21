package com.ouroboros.dev.app.service.runtime;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AuthorizeDebugProfileDTO;

public interface DebugProfileAuthorizationService {

  Try<AuthorizeDebugProfileDTO> authorize(String appName, String devKey);
}
