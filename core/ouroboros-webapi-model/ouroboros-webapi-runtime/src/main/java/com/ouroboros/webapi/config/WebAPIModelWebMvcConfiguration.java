package com.ouroboros.webapi.config;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.web.filter.UrlHandlerFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.support.RouterFunctionMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.resource.ResourceUrlProvider;
import org.springframework.web.util.pattern.PathPatternParser;

import com.ouroboros.webapi.RequestMappingIntegrationCenter;
import com.ouroboros.webapi.RouterFunctionMappingIntegrationCenter;

@Configuration
public class WebAPIModelWebMvcConfiguration implements WebMvcRegistrations {
  @Autowired
  AutowireCapableBeanFactory beanFactory;

  public WebAPIModelWebMvcConfiguration(AutowireCapableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
    var requestMappingHandlerMapping = new RequestMappingIntegrationCenter();
    beanFactory.autowireBean(requestMappingHandlerMapping);
    return requestMappingHandlerMapping;
  }

  @Bean
  public FilterRegistrationBean<UrlHandlerFilter> apiTrailingSlashUrlHandlerFilter() {
    var filter = UrlHandlerFilter
        .trailingSlashHandler("/api/**")
        .wrapRequest()
        .build();
    var registration = new FilterRegistrationBean<>(filter);
    registration.setName("ouroborosApiTrailingSlashUrlHandlerFilter");
    registration.addUrlPatterns("/api/*");
    registration.setDispatcherTypes(DispatcherType.REQUEST);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }

  /**
   * 配置自定义 routerFunctionMapping
   *
   * @return RouterFunctionMapping
   */
  @Bean
  public RouterFunctionMapping routerFunctionMapping(
      @Qualifier("mvcConversionService") FormattingConversionService conversionService,
      @Qualifier("mvcResourceUrlProvider") ResourceUrlProvider resourceUrlProvider,
      PathPatternParser patternParser,
      RequestMappingHandlerAdapter requestMappingHandlerAdapter,
      List<WebMvcConfigurer> webMvcConfigurers
  ) {
    var mapping = new RouterFunctionMappingIntegrationCenter(conversionService, resourceUrlProvider, patternParser, requestMappingHandlerAdapter, webMvcConfigurers);
    beanFactory.autowireBean(mapping);
    return mapping;
  }
}
