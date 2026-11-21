package com.ouroboros.i18n.runtime.extractor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.vavr.control.Either;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.errorcode.registry.ErrorCodeRegistry;
import com.ouroboros.i18n.extractor.TextExtractor;
import com.ouroboros.i18n.model.I18nEntry;
import com.ouroboros.i18n.runtime.model.I18nEntryImpl;

public class ErrorCodeTextExtractor implements TextExtractor {
  private static final String ERROR_NAMESPACE = "errors";
  private final ErrorCodeRegistry errorCodeRegistry;

  public ErrorCodeTextExtractor() {
    this(resolveRegistryFromSpring());
  }

  public ErrorCodeTextExtractor(ErrorCodeRegistry errorCodeRegistry) {
    this.errorCodeRegistry = errorCodeRegistry;
  }

  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    if (errorCodeRegistry == null) {
      return Either.right(Collections.emptyList());
    }

    var entries = errorCodeRegistry.getAllDefinitions().stream()
        .filter(Objects::nonNull)
        .filter(definition -> definition.getCode() != null)
        .map(definition -> (I18nEntry) new I18nEntryImpl(
            ERROR_NAMESPACE,
            String.valueOf(definition.getCode()),
            definition.getDefaultMessageTemplate(),
            "ErrorCodeDefinition:" + definition.getCode(),
            "defaultMessageTemplate"
        ))
        .collect(Collectors.toList());

    return Either.right(entries);
  }

  private static ErrorCodeRegistry resolveRegistryFromSpring() {
    try {
      if (SpringContextUtil.getApplicationContext() == null) {
        return null;
      }
      return SpringContextUtil.getBean(ErrorCodeRegistry.class);
    } catch (Exception registryLookupFailure) {
      return null;
    }
  }
}
