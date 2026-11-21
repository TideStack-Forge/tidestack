package com.ouroboros.rpc.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlainRpcCredentialTest {
  private final Logger logger = (Logger) LoggerFactory.getLogger(PlainRpcCredential.class);
  private ConfigurationService configurationService;
  private Environment environment;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    configurationService = mock(ConfigurationService.class);
    environment = mock(Environment.class);
    new PlainRpcCredential(configurationService, environment);
    logAppender = attachLogAppender();
  }

  @AfterEach
  void tearDown() {
    if (logAppender != null) {
      logger.detachAppender(logAppender);
      logAppender.stop();
      logAppender = null;
    }
  }

  @Test
  void applyToRequest_acceptsSupportedInValuesCaseInsensitivelyAndNormalizesWritePath() {
    RPCAuthCredential<HttpRPCRequest> credential = credential(
        "caseScopeCredential",
        Arrays.asList(
            definition("name", "X-Header", "in", "hEaDeR", "value", "${headerValue}"),
            definition("name", "search", "in", "QuErY", "value", "${queryValue}"),
            definition("name", "enabled", "in", "BoDy", "value", "${bodyValue}"),
            definition("name", "session", "in", "cOoKiE", "value", "${cookieValue}"),
            definition("name", "tenantId", "in", "PaTh", "value", "${pathValue}")
        ));

    HttpRPCRequest request = new HttpRPCRequest();
    Try<HttpRPCRequest> result = credential.applyToRequest(request, context(
        "headerValue", "header-1",
        "queryValue", "query-1",
        "bodyValue", "body-1",
        "cookieValue", "cookie-1",
        "pathValue", "path-1"
    ));

    assertTrue(result.isSuccess());
    assertAll(
        () -> assertEquals("header-1", request.getHeaders().get("X-Header")),
        () -> assertEquals("query-1", request.getQuery().get("search")),
        () -> assertEquals("body-1", request.getBody().get("enabled")),
        () -> assertEquals("cookie-1", request.getCookies().get("session")),
        () -> assertEquals("path-1", request.getPathVariables().get("tenantId")),
        () -> assertTrue(warningEvents().isEmpty())
    );
  }

  @Test
  void applyToRequest_warnsAndSkipsInvalidDefinitionsWhileApplyingValidOnes() {
    RPCAuthCredential<HttpRPCRequest> credential = credential(
        "warnSkipCredential",
        Arrays.asList(
            definition("in", "header", "value", "secret-missing-name"),
            "not-a-map-definition",
            definition("name", "missing-in", "value", "secret-missing-in"),
            definition("name", "missing-value", "in", "header"),
            definition("name", "   ", "in", "query", "value", "secret-blank-name"),
            definition("name", "blank-in", "in", "   ", "value", "secret-blank-in"),
            definition("name", "blank-value", "in", "cookie", "value", "   "),
            definition("name", "unsupported", "in", "matrix", "value", "secret-unsupported"),
            definition("name", "X-Valid", "in", "header", "value", "${validValue}")
        ));

    HttpRPCRequest request = new HttpRPCRequest();
    Try<HttpRPCRequest> result = credential.applyToRequest(request, context("validValue", "applied"));

    assertTrue(result.isSuccess());
    assertAll(
        () -> assertEquals("applied", request.getHeaders().get("X-Valid")),
        () -> assertNull(request.getHeaders().get("unsupported")),
        () -> assertTrue(request.getQuery().isEmpty()),
        () -> assertTrue(request.getCookies().isEmpty())
    );

    List<ILoggingEvent> warnings = warningEvents();
    assertEquals(8, warnings.size());
    assertWarningContainsContextButNotPayload(warnings.get(0), "warnSkipCredential", 0, "secret-missing-name");
    assertWarningContainsContextButNotPayload(warnings.get(1), "warnSkipCredential", 1);
    assertWarningContainsContextButNotPayload(warnings.get(2), "warnSkipCredential", 2, "secret-missing-in");
    assertWarningContainsContextButNotPayload(warnings.get(3), "warnSkipCredential", 3);
    assertWarningContainsContextButNotPayload(warnings.get(4), "warnSkipCredential", 4, "secret-blank-name");
    assertWarningContainsContextButNotPayload(warnings.get(5), "warnSkipCredential", 5, "secret-blank-in");
    assertWarningContainsContextButNotPayload(warnings.get(6), "warnSkipCredential", 6);
    assertWarningContainsContextButNotPayload(warnings.get(7), "warnSkipCredential", 7, "secret-unsupported");
  }

  @Test
  void applyToRequest_failsWhenAllDefinitionsAreInvalid() {
    RPCAuthCredential<HttpRPCRequest> credential = credential(
        "allInvalidCredential",
        Arrays.asList(
            definition("in", "header", "value", "secret-missing-name"),
            definition("name", "blank-value", "in", "query", "value", "   ")
        ));

    AuthenticateException exception = assertAuthenticateFailure(
        credential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));

    assertAll(
        () -> assertTrue(exception.getMessage().contains("allInvalidCredential")),
        () -> assertTrue(exception.getMessage().contains("没有有效"))
    );
  }

  @Test
  void applyToRequest_preservesConfigAndEnvironmentPlaceholderResolution() {
    when(configurationService.get(String.class, "plain.config")).thenReturn(java.util.Optional.of("config-value"));
    when(configurationService.get(String.class, "plain.env")).thenReturn(java.util.Optional.empty());
    when(environment.getProperty("plain.env")).thenReturn("env-value");

    RPCAuthCredential<HttpRPCRequest> credential = credential(
        "configLookupCredential",
        Arrays.asList(
            definition("name", "X-Config", "in", "header", "value", "#{plain.config}"),
            definition("name", "X-Env", "in", "header", "value", "#{plain.env}")
        ));

    HttpRPCRequest request = new HttpRPCRequest();
    Try<HttpRPCRequest> result = credential.applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isSuccess());
    assertAll(
        () -> assertEquals("config-value", request.getHeaders().get("X-Config")),
        () -> assertEquals("env-value", request.getHeaders().get("X-Env")),
        () -> assertTrue(warningEvents().isEmpty())
    );
  }

  @Test
  void applyToRequest_wrapsMalformedExpressionFailuresWithCredentialAndParameterContext() {
    RPCAuthCredential<HttpRPCRequest> credential = credential(
        "malformedExpressionCredential",
        Collections.singletonList(definition("name", "X-Trace", "in", "header", "value", "${traceId + }")));

    AuthenticateException exception = assertAuthenticateFailure(
        credential.applyToRequest(new HttpRPCRequest(), Collections.emptyMap()));

    assertAll(
        () -> assertTrue(exception.getMessage().contains("malformedExpressionCredential")),
        () -> assertTrue(exception.getMessage().contains("X-Trace")),
        () -> assertTrue(exception.getMessage().contains("HEADER")),
        () -> assertNotNull(exception.getCause()),
        () -> assertTrue(exception.getCause().getMessage().contains("计算表达式失败"))
    );
  }

  @Test
  void applyToRequest_preservesDuplicateOverwriteSemanticsWithoutValidationErrors() {
    RPCAuthCredential<HttpRPCRequest> credential = credential(
        "duplicateCredential",
        Arrays.asList(
            definition("name", "X-Duplicate", "in", "header", "value", "${firstValue}"),
            definition("name", "X-Duplicate", "in", "HEADER", "value", "${secondValue}")
        ));

    HttpRPCRequest request = new HttpRPCRequest();
    Try<HttpRPCRequest> result = credential.applyToRequest(request, context(
        "firstValue", "first",
        "secondValue", "second"
    ));

    assertTrue(result.isSuccess());
    assertEquals("second", request.getHeaders().get("X-Duplicate"));
    assertTrue(warningEvents().isEmpty());
  }

  private RPCAuthCredential<HttpRPCRequest> credential(String credentialName, List<?> definitions) {
    return new PlainRpcCredential.Builder().build(metadata(credentialName, definitions));
  }

  private RPCCredentialMetadata metadata(String credentialName, List<?> definitions) {
    RPCCredentialMetadata metadata = new RPCCredentialMetadata();
    metadata.setName(credentialName);
    metadata.setType("PlainCredentials");
    metadata.setEnabled(true);

    Map<String, Object> plainCredentials = new HashMap<>();
    plainCredentials.put("credentials", definitions);
    metadata.setExtraProps(Collections.singletonMap("plainCredentials", plainCredentials));
    return metadata;
  }

  private AuthenticateException assertAuthenticateFailure(Try<HttpRPCRequest> result) {
    assertTrue(result.isFailure());
    assertTrue(result.getCause() instanceof AuthenticateException);
    return (AuthenticateException) result.getCause();
  }

  private ListAppender<ILoggingEvent> attachLogAppender() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private List<ILoggingEvent> warningEvents() {
    if (logAppender == null) {
      return Collections.emptyList();
    }
    List<ILoggingEvent> warnings = new ArrayList<>();
    for (ILoggingEvent event : logAppender.list) {
      if (event.getLevel() == Level.WARN) {
        warnings.add(event);
      }
    }
    return warnings;
  }

  private void assertWarningContainsContextButNotPayload(
      ILoggingEvent warning,
      String credentialName,
      int parameterIndex,
      String... rawPayloadTokens
  ) {
    assertAll(
        () -> assertTrue(warning.getFormattedMessage().contains(credentialName)),
        () -> assertTrue(warning.getFormattedMessage().contains("参数索引[" + parameterIndex + "]"))
    );
    for (String rawPayloadToken : rawPayloadTokens) {
      assertFalse(warning.getFormattedMessage().contains(rawPayloadToken));
    }
  }

  private Map<String, Object> definition(Object... entries) {
    Map<String, Object> definition = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      definition.put((String) entries[i], entries[i + 1]);
    }
    return definition;
  }

  private Map<String, Object> context(Object... entries) {
    Map<String, Object> context = new HashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      context.put((String) entries[i], entries[i + 1]);
    }
    return context;
  }
}
