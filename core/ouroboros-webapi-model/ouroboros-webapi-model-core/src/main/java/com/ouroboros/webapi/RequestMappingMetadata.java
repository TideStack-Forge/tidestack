package com.ouroboros.webapi;

import java.lang.reflect.Method;

/**
 * Declared request mapping metadata captured before route registration.
 */
public class RequestMappingMetadata {
  private final String api;
  private final Class<?> handlerType;
  private final Method method;

  public RequestMappingMetadata(String api, Class<?> handlerType, Method method) {
    this.api = api;
    this.handlerType = handlerType;
    this.method = method;
  }

  public String getApi() {
    return api;
  }

  public Class<?> getHandlerType() {
    return handlerType;
  }

  public Method getMethod() {
    return method;
  }
}
