package com.ouroboros.app.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatus;
import com.ouroboros.web.WebRequestLogPath;

@RestController
public class OuroborosErrorController {
  private static final Logger logger = LoggerFactory.getLogger(OuroborosErrorController.class);

  @RequestMapping("/error")
  public WebApiResult<?> error(HttpServletRequest request, HttpServletResponse response) {
    Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int status = statusAttribute instanceof Integer value ? value : 500;
    Object exceptionAttribute = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
    Throwable failure = exceptionAttribute instanceof Throwable throwable ? throwable : null;
    if (status >= 400 && status < 500) {
      response.setStatus(status);
      HttpStatus httpStatus = HttpStatus.resolve(status);
      return WebApiResult.error(status, httpStatus == null ? "请求处理失败" : httpStatus.getReasonPhrase());
    }

    logger.error("接口 {}:{} 请求错误", request.getMethod(), WebRequestLogPath.resolve(request), failure);
    var tuple = ErrorResponseResolver.current().resolve(failure, request.getHeader("Accept-Language"));
    response.setStatus(WebApiResultHttpStatus.resolve(tuple._1));
    WebApiResult<Object> result = WebApiResult.error(tuple._1, tuple._2);
    if (tuple._3 != null) {
      result.setErrors(tuple._3);
    }
    return result;
  }
}
