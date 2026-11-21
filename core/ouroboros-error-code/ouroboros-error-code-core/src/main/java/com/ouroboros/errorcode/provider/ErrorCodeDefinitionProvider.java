package com.ouroboros.errorcode.provider;

import java.util.List;

import com.ouroboros.errorcode.model.ErrorCodeDefinition;

public interface ErrorCodeDefinitionProvider {

  List<ErrorCodeDefinition> getDefinitions();

  default int getOrder() {
    return 0;
  }
}
