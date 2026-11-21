package com.ouroboros.app.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ouroboros.web.WebApiResult;

class OuroborosErrorControllerTest {

  @Test
  void errorReturnsSafeRuntimeFailureWithoutStartupDiagnostics() {
    OuroborosErrorController controller = new OuroborosErrorController();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.INTERNAL_SERVER_ERROR.value());
    request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/sys/web-app-info");
    request.setAttribute(
        RequestDispatcher.ERROR_EXCEPTION,
        new IllegalStateException("jdbc:mysql://127.0.0.1/ouroboros password=secret")
    );

    WebApiResult<?> result = controller.error(request, response);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatus());
    assertEquals(50000, result.getCode());
    assertEquals("服务暂时不可用，请稍后重试", result.getMsg());
    assertNull(result.getExtra().get("startupState"));
    assertNull(result.getExtra().get("failureType"));
    assertNull(result.getExtra().get("failureMessage"));
    assertFalse(result.getExtra().toString().contains("jdbc:mysql"));
    assertFalse(result.getExtra().toString().contains("password=secret"));
  }

  @Test
  void errorDoesNotMarkClientErrorsAsStartupFailure() {
    OuroborosErrorController controller = new OuroborosErrorController();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());

    WebApiResult<?> result = controller.error(request, response);

    assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus());
    assertEquals(HttpStatus.NOT_FOUND.value(), result.getCode());
    assertNull(result.getExtra().get("startupState"));
    assertNull(result.getExtra().get("failureSource"));
  }
}
