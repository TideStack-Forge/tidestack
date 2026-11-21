package com.ouroboros.app.error;

import java.util.List;
import java.util.Optional;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import com.ouroboros.app.controller.ExceptionControllerAdvice;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;
import com.ouroboros.errorcode.registry.ErrorCodeRegistry;
import com.ouroboros.errorcode.model.ErrorCodeDefinition;
import io.vavr.control.Try;
import io.vavr.control.Either;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;

import com.ouroboros.errorcode.SimpleErrorCodeReference;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.web.DefaultWebApiResultExceptionMapper;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class InternalErrorResponseTest {
  private static final String DIAGNOSTIC = "PRIVATE_SQL_DIAGNOSTIC";
  private static final String PUBLIC_MESSAGE = "服务暂时不可用，请稍后重试";

  @Test
  void internalErrorsAreSafeWithAndWithoutRuntimeServices() {
    var resolver = new ErrorResponseResolver(new RequestLocaleResolver(), null, null, List.of());
    for (Throwable failure : List.of(new InternalException(DIAGNOSTIC),
        new PlatformException(DIAGNOSTIC), new IllegalStateException(DIAGNOSTIC))) {
      var basic = new DefaultWebApiResultExceptionMapper().map(failure);
      var runtime = resolver.resolve(failure, "zh-CN");
      assertEquals(50000, basic._1);
      assertEquals(PUBLIC_MESSAGE, basic._2);
      assertNull(basic._3);
      assertEquals(basic, runtime);
      assertEquals(DIAGNOSTIC, failure.getMessage());
      for (var result : List.of(WebApiResult.of(failure), WebApiResult.of(Try.failure(failure)),
          WebApiResult.of(Either.left(failure)))) {
        assertEquals(50000, result.getCode());
        assertFalse(Json.toJsonString(result).contains(DIAGNOSTIC));
      }
    }
  }

  @Test
  void internalMessageArgumentsNeverReachLocalizer() {
    var failure = new InternalException(new SimpleErrorCodeReference(50001, "INTERNAL", "failed {0}"), DIAGNOSTIC);
    var resolver = new ErrorResponseResolver(new RequestLocaleResolver(), (locale, descriptor) -> {
      assertNull(descriptor.getOriginalMessage());
      assertTrue(descriptor.getMessageArgs() == null || descriptor.getMessageArgs().length == 0);
      assertNull(descriptor.getErrors());
      return descriptor.getFallbackMessageTemplate();
    }, null, List.of());

    assertEquals(PUBLIC_MESSAGE, resolver.resolve(failure, "zh-CN")._2);
  }

  @Test
  void brokenLocalizationCannotReplaceTheOriginalFailureOrExposeDetails() {
    var resolver = new ErrorResponseResolver(new RequestLocaleResolver(), (locale, descriptor) -> {
      throw new IllegalStateException(DIAGNOSTIC);
    }, null, List.of());

    var result = resolver.resolve(new InternalException(DIAGNOSTIC), "zh-CN");
    assertEquals(50000, result._1);
    assertEquals(PUBLIC_MESSAGE, result._2);
    assertNull(result._3);
  }
  @Test
  void registryFailureAlsoFallsBackToSafeInternalResponse() {
    var registry = new ErrorCodeRegistry() {
      public Optional<ErrorCodeDefinition> getDefinition(Integer code) {
        throw new IllegalStateException(DIAGNOSTIC);
      }
      public List<ErrorCodeDefinition> getAllDefinitions() {
        return List.of();
      }
    };
    var resolver = new ErrorResponseResolver(new RequestLocaleResolver(), null, registry, List.of());
    assertEquals(PUBLIC_MESSAGE, resolver.resolve(new InternalException(DIAGNOSTIC), "zh-CN")._2);
  }

  @Test
  void mvcReturns500WithSafeBodyWhileLogsRetainCauseAndMaskTokenPath() throws Exception {
    var mvc = MockMvcBuilders.standaloneSetup(new FailingController())
        .setControllerAdvice(new ExceptionControllerAdvice(), new WebApiResultHttpStatusAdvice()).build();
    var logger = (Logger) LoggerFactory.getLogger(ExceptionControllerAdvice.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var response = mvc.perform(get("/api/auth/refresh-token/SECRET_TOKEN")).andReturn().getResponse();
      assertEquals(500, response.getStatus());
      String json = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
      assertTrue(json.contains("50000"));
      assertTrue(json.contains(PUBLIC_MESSAGE));
      assertFalse(json.contains(DIAGNOSTIC));
      assertTrue(appender.list.stream().anyMatch(event -> event.getThrowableProxy() != null
          && DIAGNOSTIC.equals(event.getThrowableProxy().getMessage())
          && "read timed out".equals(event.getThrowableProxy().getCause().getMessage())));
      assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("{token}")));
      assertFalse(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("SECRET_TOKEN")));
      assertEquals(405, mvc.perform(post("/api/auth/refresh-token/SECRET_TOKEN")).andReturn().getResponse().getStatus());
      assertEquals(400, mvc.perform(get("/number").param("value", "invalid")).andReturn().getResponse().getStatus());
      assertEquals(400, mvc.perform(get("/number")).andReturn().getResponse().getStatus());
      assertEquals(400, mvc.perform(post("/body").contentType("application/json").content("invalid"))
          .andReturn().getResponse().getStatus());
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @RestController
  static class FailingController {
    @GetMapping("/api/auth/refresh-token/{token}")
    public WebApiResult<?> failure() {
      throw new InternalException(DIAGNOSTIC, new IllegalStateException("read timed out"));
    }
    @PostMapping("/body")
    public WebApiResult<?> body(@RequestBody java.util.Map<String, Object> value) {
      return WebApiResult.ok(value);
    }
    @GetMapping("/number")
    public WebApiResult<Integer> number(@RequestParam("value") int value) {
      return WebApiResult.ok(value);
    }
  }

}
