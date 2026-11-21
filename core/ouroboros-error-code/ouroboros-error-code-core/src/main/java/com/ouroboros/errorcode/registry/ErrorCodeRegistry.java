package com.ouroboros.errorcode.registry;

import java.util.List;
import java.util.Optional;

import com.ouroboros.errorcode.model.ErrorCodeDefinition;

public interface ErrorCodeRegistry {

  Optional<ErrorCodeDefinition> getDefinition(Integer code);

  List<ErrorCodeDefinition> getAllDefinitions();
}
