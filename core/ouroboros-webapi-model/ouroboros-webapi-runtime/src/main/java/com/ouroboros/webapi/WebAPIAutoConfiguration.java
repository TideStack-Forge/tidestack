package com.ouroboros.webapi;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(value = "com.ouroboros.webapi.**")
public class WebAPIAutoConfiguration {
  static Logger logger = LoggerFactory.getLogger(WebAPIAutoConfiguration.class);

  @org.springframework.context.annotation.Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(com.ouroboros.businessmodel.action.BusinessActionRequestProvider.class)
  public com.ouroboros.businessmodel.action.BusinessActionRequestProvider businessActionRequestProvider(
      org.springframework.beans.factory.ObjectProvider<org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter> adapters) {
    return () -> {
      var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
      if (!(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet)) {
        return null;
      }
      var adapter = adapters.getIfAvailable();
      java.util.List<org.springframework.http.converter.HttpMessageConverter<?>> converters = adapter == null
          ? java.util.List.of(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(com.ouroboros.util.Json.mapperCopy()))
          : adapter.getMessageConverters();
      var request = org.springframework.web.servlet.function.ServerRequest.create(servlet.getRequest(), converters);
      return com.ouroboros.logicflow.adapter.RequestInfoMap.forRequest(request);
    };
  }

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Webapi Auto Configuration loaded");
  }
}
