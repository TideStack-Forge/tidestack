package com.ouroboros.webapi;

import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import com.ouroboros.businessmodel.action.BusinessActionInputValues;
import com.ouroboros.logicflow.adapter.RequestInfoMap;

/** Reuses MVC's parsed body when a Controller invokes a business Action synchronously. */
@ControllerAdvice
public class BusinessActionRequestBodyAdvice extends RequestBodyAdviceAdapter {
  @Override
  public boolean supports(MethodParameter parameter, Type type,
                          Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object afterBodyRead(Object body, HttpInputMessage message, MethodParameter parameter, Type type,
                              Class<? extends HttpMessageConverter<?>> converterType) {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
      RequestInfoMap.cacheParsedBody(attributes.getRequest(), () -> BusinessActionInputValues.snapshot(body));
    }
    return body;
  }
}
