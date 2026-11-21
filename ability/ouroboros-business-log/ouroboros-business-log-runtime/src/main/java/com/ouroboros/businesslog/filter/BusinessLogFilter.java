package com.ouroboros.businesslog.filter;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Either;

import com.ouroboros.businesslog.BusinessLogStorage;
import com.ouroboros.businesslog.BusinessOperationTemplate;
import com.ouroboros.businesslog.BusinessOperationTemplateCenter;
import com.ouroboros.businesslog.model.BusinessLogModel;
import com.ouroboros.businesslog.service.model.BusinessOperationTemplateSourceView;
import com.ouroboros.businesslog.util.BusinessLogExtractor;

/**
 * @author liansz
 **/
@Component
@Order(110)
public class BusinessLogFilter implements Filter {

  private static final Comparator<Tuple2<RequestMatcher, BusinessOperationTemplateSourceView>>
      MATCHER_PRECEDENCE = Comparator
          .comparing(
              (Tuple2<RequestMatcher, BusinessOperationTemplateSourceView> tuple) -> tuple._1.urlPattern,
              PathPattern.SPECIFICITY_COMPARATOR
          )
          .thenComparing(tuple -> "*".equals(tuple._1.httpMethod))
          .thenComparing(tuple -> tuple._2.getSource())
          .thenComparing(tuple -> tuple._2.getTemplateKey(), String.CASE_INSENSITIVE_ORDER);

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private BusinessLogStorage<BusinessLogModel> businessLogStorage;

  public BusinessLogFilter(BusinessLogStorage businessLogStorage) {
    this.businessLogStorage = businessLogStorage;
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
    HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
    HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;

    // 先匹配模版
    Optional<Tuple2<Map<String, String>, BusinessOperationTemplateSourceView>> matched =
        findMatchingTemplate(getLogTemplateMatchers(), httpServletRequest);

    // 没有匹配到直接放行，不重复包裹
    if (!matched.isPresent()) {
      filterChain.doFilter(httpServletRequest, httpServletResponse);
      return;
    }

    // 对请求和响应进行包裹，以便提取数据
    ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(httpServletRequest);
    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpServletResponse);

    try {
      filterChain.doFilter(requestWrapper, responseWrapper);
    } finally {
      try {
        // 提取日志
        Map<String, String> pathVariables = matched.get()._1();
        BusinessOperationTemplate template = matched.get()._2();
        BusinessLogModel log = BusinessLogExtractor.extract(template, pathVariables, requestWrapper, responseWrapper);

        // 保存日志
        businessLogStorage.save(log);
      } finally {
        // 将缓存的响应体内容复制回原始的 HttpServletResponse 对象
        responseWrapper.copyBodyToResponse();
      }
    }
  }

  private List<Tuple2<RequestMatcher, BusinessOperationTemplateSourceView>> getLogTemplateMatchers() {
    // TODO: 需要缓存，避免每次请求都要重建 matcher
    return buildLogTemplateMatchers(BusinessOperationTemplateCenter.getAllTemplates().values());
  }

  static List<Tuple2<RequestMatcher, BusinessOperationTemplateSourceView>> buildLogTemplateMatchers(
      Collection<BusinessOperationTemplateSourceView> templates
  ) {
    return templates.stream()
        .map(template -> Tuple.of(new RequestMatcher(template), template))
        .sorted(MATCHER_PRECEDENCE)
        .collect(Collectors.toList());
  }

  static Optional<Tuple2<Map<String, String>, BusinessOperationTemplateSourceView>> findMatchingTemplate(
      List<Tuple2<RequestMatcher, BusinessOperationTemplateSourceView>> matchers,
      HttpServletRequest request
  ) {
    return matchers.stream()
        .map(tuple -> tuple.map1(matcher -> matcher.matchAndExtract(request)))
        .filter(tuple -> tuple._1.isRight())
        .map(tuple -> Tuple.of(tuple._1.get(), tuple._2))
        .findFirst();
  }

  static class RequestMatcher {
    private final PathPattern urlPattern;
    private final String httpMethod;

    public RequestMatcher(BusinessOperationTemplate template) {
      this.urlPattern = new PathPatternParser().parse(template.getUrl());
      this.httpMethod = template.getHttpMethod();
    }

    Either<Void, Map<String, String>> matchAndExtract(HttpServletRequest request) {
      // 先检查方法
      if (!"*".equals(this.httpMethod) && !request.getMethod().equalsIgnoreCase(this.httpMethod)) {
        return Either.left(null);
      }

      // 再检查路径，并提取路径变量
      var pathContainer = PathContainer.parsePath(request.getRequestURI());
      var matchResult = Optional.ofNullable(this.urlPattern.matchAndExtract(pathContainer))
          .map(PathPattern.PathMatchInfo::getUriVariables);

      // 最后包装成 Either
      return matchResult
          .map(Either::<Void, Map<String, String>>right)
          .orElseGet(() -> Either.left(null));
    }
  }
}
