package com.ouroboros.app.error;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.vavr.Tuple;
import io.vavr.Tuple3;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.errorcode.ResolvedErrorDescriptor;
import com.ouroboros.errorcode.ThrowableErrorDescriptorMapper;
import com.ouroboros.errorcode.registry.ErrorCodeRegistry;
import com.ouroboros.i18n.error.ErrorMessageLocalizer;
import com.ouroboros.util.SPI;

@Component
public class ErrorResponseResolver {
  private static final Logger logger = LoggerFactory.getLogger(ErrorResponseResolver.class);
  private static final List<ThrowableErrorDescriptorMapper> DEFAULT_THROWABLE_ERROR_DESCRIPTOR_MAPPERS =
      SPI.getCachedServiceStream(ThrowableErrorDescriptorMapper.class).collect(Collectors.toList());
  private static final ErrorResponseResolver FALLBACK_RESOLVER = new ErrorResponseResolver(
      new RequestLocaleResolver(),
      null,
      null,
      DEFAULT_THROWABLE_ERROR_DESCRIPTOR_MAPPERS
  );

  private final RequestLocaleResolver requestLocaleResolver;
  private final ErrorMessageLocalizer errorMessageLocalizer;
  private final ErrorCodeRegistry errorCodeRegistry;
  private final List<ThrowableErrorDescriptorMapper> throwableErrorDescriptorMappers;
  private final DefaultThrowableErrorDescriptorMapper defaultThrowableErrorDescriptorMapper;

  @Autowired
  public ErrorResponseResolver(RequestLocaleResolver requestLocaleResolver,
                               ObjectProvider<ErrorMessageLocalizer> errorMessageLocalizerProvider,
                               ObjectProvider<ErrorCodeRegistry> errorCodeRegistryProvider) {
    this(
        requestLocaleResolver,
        errorMessageLocalizerProvider.getIfAvailable(),
        errorCodeRegistryProvider.getIfAvailable(),
        DEFAULT_THROWABLE_ERROR_DESCRIPTOR_MAPPERS
    );
  }

  public ErrorResponseResolver(RequestLocaleResolver requestLocaleResolver,
                               ErrorMessageLocalizer errorMessageLocalizer,
                               ErrorCodeRegistry errorCodeRegistry,
                               List<ThrowableErrorDescriptorMapper> throwableErrorDescriptorMappers) {
    this.requestLocaleResolver = requestLocaleResolver;
    this.errorMessageLocalizer = errorMessageLocalizer;
    this.errorCodeRegistry = errorCodeRegistry;
    this.throwableErrorDescriptorMappers = throwableErrorDescriptorMappers == null
        ? Collections.emptyList()
        : throwableErrorDescriptorMappers.stream()
            .sorted(((Comparator<ThrowableErrorDescriptorMapper>) SPI::sortByPriority).reversed())
            .collect(Collectors.toList());
    this.defaultThrowableErrorDescriptorMapper = new DefaultThrowableErrorDescriptorMapper();
  }

  public static ErrorResponseResolver current() {
    var applicationContext = SpringContextUtil.getApplicationContext();
    if (applicationContext == null) {
      return FALLBACK_RESOLVER;
    }

    var resolver = applicationContext.getBeanProvider(ErrorResponseResolver.class).getIfAvailable();
    if (resolver != null) {
      return resolver;
    }

    return new ErrorResponseResolver(
        applicationContext.getBeanProvider(RequestLocaleResolver.class).getIfAvailable(RequestLocaleResolver::new),
        applicationContext.getBeanProvider(ErrorMessageLocalizer.class).getIfAvailable(),
        applicationContext.getBeanProvider(ErrorCodeRegistry.class).getIfAvailable(),
        DEFAULT_THROWABLE_ERROR_DESCRIPTOR_MAPPERS
    );
  }

  public Tuple3<Integer, String, Object> resolve(Throwable throwable) {
    try {
      return resolve(throwable, requestLocaleResolver == null ? null : requestLocaleResolver.resolveCurrentLocale());
    } catch (RuntimeException renderingFailure) {
      return safeFallback(throwable, renderingFailure);
    }
  }

  public Tuple3<Integer, String, Object> resolve(Throwable throwable, String acceptLanguage) {
    try {
      var resolvedLocale = requestLocaleResolver == null ? null : requestLocaleResolver.resolve(acceptLanguage);
      var descriptor = enrichWithRegistry(resolveDescriptor(throwable));
      var message = resolveMessage(resolvedLocale, descriptor);
      return Tuple.of(resolveCode(descriptor), message, descriptor == null ? null : descriptor.getErrors());
    } catch (RuntimeException renderingFailure) {
      return safeFallback(throwable, renderingFailure);
    }
  }

  private Tuple3<Integer, String, Object> safeFallback(Throwable throwable, RuntimeException renderingFailure) {
    logger.error("错误响应解析失败，使用安全默认响应", renderingFailure);
    var descriptor = defaultThrowableErrorDescriptorMapper.map(throwable);
    return Tuple.of(resolveCode(descriptor),
        formatMessage(descriptor.getFallbackMessageTemplate(), descriptor.getMessageArgs()), descriptor.getErrors());
  }

  private ResolvedErrorDescriptor resolveDescriptor(Throwable throwable) {
    for (ThrowableErrorDescriptorMapper mapper : throwableErrorDescriptorMappers) {
      if (mapper.supports(throwable)) {
        var descriptor = mapper.map(throwable);
        return descriptor == null ? defaultThrowableErrorDescriptorMapper.map(throwable) : descriptor;
      }
    }
    return defaultThrowableErrorDescriptorMapper.map(throwable);
  }

  private ResolvedErrorDescriptor enrichWithRegistry(ResolvedErrorDescriptor descriptor) {
    if (descriptor == null || errorCodeRegistry == null || descriptor.getCode() == null) {
      return descriptor;
    }

    var definition = errorCodeRegistry.getDefinition(descriptor.getCode());
    if (!definition.isPresent()) {
      return descriptor;
    }

    var errorCodeDefinition = definition.get();
    return ResolvedErrorDescriptor.builder()
        .code(descriptor.getCode())
        .bizCode(errorCodeDefinition.getBizCode() != null ? errorCodeDefinition.getBizCode() : descriptor.getBizCode())
        .fallbackMessageTemplate(errorCodeDefinition.getDefaultMessageTemplate() != null
            ? errorCodeDefinition.getDefaultMessageTemplate()
            : descriptor.getFallbackMessageTemplate())
        .messageArgs(descriptor.getMessageArgs())
        .originalMessage(descriptor.getOriginalMessage())
        .errors(descriptor.getErrors())
        .build();
  }

  private String resolveMessage(String locale, ResolvedErrorDescriptor descriptor) {
    if (descriptor == null) {
      return DefaultThrowableErrorDescriptorMapper.DEFAULT_FALLBACK_MESSAGE;
    }

    if (errorMessageLocalizer != null) {
      var localized = errorMessageLocalizer.localize(locale, descriptor);
      if (localized != null && !localized.trim().isEmpty()) {
        return localized;
      }
    }

    if (descriptor.getFallbackMessageTemplate() != null) {
      return formatMessage(descriptor.getFallbackMessageTemplate(), descriptor.getMessageArgs());
    }

    if (descriptor.getOriginalMessage() != null && !descriptor.getOriginalMessage().trim().isEmpty()) {
      return descriptor.getOriginalMessage();
    }

    return "操作失败";
  }

  private static Integer resolveCode(ResolvedErrorDescriptor descriptor) {
    return descriptor == null || descriptor.getCode() == null ? -1 : descriptor.getCode();
  }

  private static String formatMessage(String template, Object[] messageArgs) {
    if (messageArgs == null || messageArgs.length == 0) {
      return template;
    }
    try {
      return MessageFormat.format(template, messageArgs);
    } catch (IllegalArgumentException formatFailure) {
      return template;
    }
  }
}
