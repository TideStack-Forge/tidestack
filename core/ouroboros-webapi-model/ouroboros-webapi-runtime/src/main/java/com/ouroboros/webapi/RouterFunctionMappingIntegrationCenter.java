package com.ouroboros.webapi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.function.support.RouterFunctionMapping;
import org.springframework.web.servlet.handler.ConversionServiceExposingInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.resource.ResourceUrlProvider;
import org.springframework.web.servlet.resource.ResourceUrlProviderExposingInterceptor;
import org.springframework.web.util.pattern.PathPatternParser;

import com.ouroboros.logicflow.adapter.HandlerFunctionAdapter;
import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.util.SPI;
import com.ouroboros.webapi.config.CustomCorsRegistry;
import com.ouroboros.webapi.util.PathComparator;

/**
 * 拦截默认的RouterFunction路由映射，将路由映射成WebApiModel，交由后续统一注册
 * <li>继承 RouterFunctionMapping 用于拦截</li>
 * <li>实现 WebapiRouteRegistry 用于注入路由映射</li>
 */
public class RouterFunctionMappingIntegrationCenter extends RouterFunctionMapping implements WebAPIRouteRegistry {
  private final List<RouterFunction<?>> proCodeRouterFunctions = new ArrayList<>();
  @Autowired
  PlatformTransactionManager platformTransactionManager;
  @Autowired
  LogicFlowExecutionFacade logicFlowExecutionFacade;
  @Autowired
  ObjectProvider<BusinessActionRuntime> businessActionRuntimeProvider;

  public RouterFunctionMappingIntegrationCenter(
      FormattingConversionService conversionService,
      ResourceUrlProvider resourceUrlProvider,
      PathPatternParser patternParser,
      RequestMappingHandlerAdapter requestMappingHandlerAdapter,
      List<WebMvcConfigurer> webMvcConfigurers
  ) {
    var corsConfigurations = CustomCorsRegistry.getCorsConfigurations(webMvcConfigurers);
    setOrder(3);
    setInterceptors(new ConversionServiceExposingInterceptor(conversionService), new ResourceUrlProviderExposingInterceptor(resourceUrlProvider));
    setCorsConfigurations(corsConfigurations);
    setMessageConverters(requestMappingHandlerAdapter.getMessageConverters());

    if (patternParser != null) {
      setPatternParser(patternParser);
    }
  }

  @Override
  public void register(Collection<WebAPIModel> webAPIModels) {
    var logicFlowRouterFunctions = buildLogicFlowRouterFunctions(webAPIModels);
    var filteredProCodeRouterFunctions = getProCodeRouterFunctions();
    final var routerFunction = Stream.concat(filteredProCodeRouterFunctions.stream(), logicFlowRouterFunctions.stream())
        .reduce(RouterFunction::andOther);
    setRouterFunction(routerFunction.orElse(null));
  }

  @Override
  public void afterPropertiesSet() {
    super.afterPropertiesSet();
    var routerFunction = getRouterFunction();
    if (routerFunction != null) {
      saveAggregatedFunction(routerFunction);
    }
    this.setRouterFunction(null);
  }

  private void saveAggregatedFunction(RouterFunction<?> aggregatedFunction) {
    proCodeRouterFunctions.clear();
    // TODO: 要把聚合的路由函数拆分成单个的路由函数
    proCodeRouterFunctions.add(aggregatedFunction);
  }

  private List<RouterFunction<?>> buildLogicFlowRouterFunctions(Collection<WebAPIModel> models) {
    return models.stream()
        .filter(model -> model.getHandlerType() == HandlerType.LOGIC_FLOW || model.getHandlerType() == HandlerType.BUSINESS_ACTION)
        .filter(model -> ObjectUtils.isNotEmpty(model.getApi()))
        .sorted(new PathComparator())
        .map(this::buildLogicFlowRouterFunction)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * 获取代码注册的路由
   * TODO: 根据 WebApiModel 过滤代码注册的路由
   *
   * @return RouterFunction 列表
   */
  private List<RouterFunction<?>> getProCodeRouterFunctions() {
    return proCodeRouterFunctions;
  }

  private RouterFunction<ServerResponse> buildLogicFlowRouterFunction(WebAPIModel model) {
    var methodName = model.getMethod();
    var path = model.getPath();
    var builderFn = getBuilderFunction(methodName);
    if (builderFn == null) {
      return null;
    }
    var builder = builderFn.apply(path, buildHandlerFunction(model));
    return builder.build();
  }

  private BiFunction<String, HandlerFunction<ServerResponse>, RouterFunctions.Builder> getBuilderFunction(String methodName) {
    return switch (methodName) {
      case "GET" -> RouterFunctions.route()::GET;
      case "POST" -> RouterFunctions.route()::POST;
      case "PUT" -> RouterFunctions.route()::PUT;
      case "DELETE" -> RouterFunctions.route()::DELETE;
      case "PATCH" -> RouterFunctions.route()::PATCH;
      case "HEAD" -> RouterFunctions.route()::HEAD;
      case "OPTIONS" -> RouterFunctions.route()::OPTIONS;
      default -> null;
    };
  }

  private HandlerFunction<ServerResponse> buildHandlerFunction(WebAPIModel model) {
    BusinessActionWebApiContract.validate(model);
    if (model.getHandlerType() == HandlerType.BUSINESS_ACTION) {
      BusinessActionRuntime runtime = businessActionRuntimeProvider.getIfAvailable();
      if (runtime == null) {
        throw new IllegalStateException("Business Action WebAPI routes require BusinessActionRuntime");
      }
      BusinessModelBehaviorProvider behaviors = SPI.getSortedServiceStream(BusinessModelBehaviorProvider.class)
          .findFirst().orElseThrow(() -> new IllegalStateException("BusinessModelBehaviorProvider is not registered"));
      return BusinessActionWebApiAdapter.buildHandler(model, runtime, behaviors);
    }
    // TODO: 根据WebApiModel构建HandlerFunction
    var flowName = model.getExtraProp(String.class, "logicFlow").orElse(null);
    return HandlerFunctionAdapter.buildHandler(flowName, platformTransactionManager, logicFlowExecutionFacade);
  }
}
