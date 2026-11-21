package com.ouroboros.webapi;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.MediaTypeExpression;
import org.springframework.web.servlet.mvc.condition.NameValueExpression;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.util.WebAPIUtils;

/**
 * 拦截默认的 RequestMappingHandlerMapping 注解路由映射，将路由映射成WebApiModel，交由后续统一注册
 * <li>继承 RequestMappingHandlerMapping 用于拦截</li>
 * <li>实现 WebapiModelProvider 用于返回路由映射</li>
 * <li>实现 WebapiRouteRegistry 用于注入路由映射</li>
 */
public class RequestMappingIntegrationCenter extends RequestMappingHandlerMapping implements WebAPIRouteRegistry, RequestMappingMetadataProvider {
  final Set<String> API_PREFIXES = Arrays.stream(WebAPIScope.values()).map(WebAPIScope::getPrefix).collect(Collectors.toSet());
  // 缓存已注册的路由映射
  final List<RequestMappingInfo> registeredRequestMappingInfoList = new ArrayList<>();
  // 缓存未注册的路由映射
  final List<Tuple3<RequestMappingInfo, Class<?>, Method>> originalRequestMappingInfoList = new ArrayList<>();
  final List<RequestMappingInfo> downstreamLogicFlowRequestMappingInfoList = new ArrayList<>();

  @Autowired
  private BeanFactory beanFactory;
  private final RequestMappingOpenAPIExtractor openAPIExtractor = new RequestMappingOpenAPIExtractor();

  public RequestMappingIntegrationCenter() {
    Proxy.instance = this;
  }

  @Override
  public Map<RequestMappingInfo, HandlerMethod> getHandlerMethods() {
    var handlerMethods = new LinkedHashMap<RequestMappingInfo, HandlerMethod>();
    originalRequestMappingInfoList.forEach(tuple -> handlerMethods.put(
        tuple._1(),
        createHandlerMethod(beanFactory.getBean(tuple._2()), tuple._3())
    ));
    handlerMethods.putAll(super.getHandlerMethods());
    return Collections.unmodifiableMap(handlerMethods);
  }

  @Override
  public void register(Collection<WebAPIModel> webAPIModels) {
    downstreamLogicFlowRequestMappingInfoList.clear();
    webAPIModels.stream()
        .filter(model -> model.getHandlerType() == HandlerType.LOGIC_FLOW || model.getHandlerType() == HandlerType.BUSINESS_ACTION)
        .map(model -> rebuildRequestMappingInfo(null, model))
        .filter(Objects::nonNull)
        .forEach(downstreamLogicFlowRequestMappingInfoList::add);

    // 先反注册所有路由映射
    registeredRequestMappingInfoList.forEach(this::unregisterMapping);
    registeredRequestMappingInfoList.clear();

    // 根据WebApiModel构建路由映射
    final var requestMappingList = buildRequestMappingList(webAPIModels);

    // 注册路由映射
    requestMappingList.forEach(tuple -> {
      var bean = beanFactory.getBean(tuple._2());
      registerMapping(tuple._1(), bean, tuple._3());
      registeredRequestMappingInfoList.add(tuple._1());
    });
  }

  /**
   * 拦截路由映射，将路由映射成WebApiModel，并返回null，阻止默认的路由映射
   *
   * @param method      the method to provide a mapping for
   * @param handlerType the handler type, possibly a subtype of the method's
   *                    declaring class
   * @return null
   */
  @Override
  @Nullable
  protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
    var info = super.getMappingForMethod(method, handlerType);
    if (info != null) {
      saveRequestMapping(info, handlerType, method);
    }
    return null;
  }

  @Override
  @Nullable
  protected HandlerMethod handleNoMatch(Set<RequestMappingInfo> infos, String lookupPath, HttpServletRequest request) throws ServletException {
    if (hasDownstreamLogicFlowMatch(request)) {
      return null;
    }
    return super.handleNoMatch(infos, lookupPath, request);
  }

  private boolean hasDownstreamLogicFlowMatch(HttpServletRequest request) {
    return downstreamLogicFlowRequestMappingInfoList.stream()
        .anyMatch(mappingInfo -> mappingInfo.getMatchingCondition(request) != null);
  }

  private Map<String, WebAPIModel> getApiModelMap() {
    return originalRequestMappingInfoList.stream()
        .map(tuple3 -> tuple3.apply(this::buildWebApiModel))
        .collect(HashMap::new, (map, webapi) -> map.put(webapi.getApi(), webapi), HashMap::putAll);
  }

  @Override
  public Collection<RequestMappingMetadata> getRequestMappingMetadata() {
    return originalRequestMappingInfoList.stream()
        .map(tuple -> new RequestMappingMetadata(getRequestMappingMetadataApi(tuple._1()), tuple._2(), tuple._3()))
        .collect(Collectors.toList());
  }

  /**
   * 构建WebApiModel
   */
  private WebAPIModel buildWebApiModel(RequestMappingInfo requestMappingInfo, Class<?> handlerType, Method method) {
    var path = getFirstPath(requestMappingInfo);
    // 根据路径前缀判断接口范围
    var finalPrefix = API_PREFIXES.stream()
        .filter(path::startsWith)
        .findFirst()
        .orElse(WebAPIScope.INTERNAL.getPrefix());
    var finalScope = WebAPIScope.fromPrefix(finalPrefix);

    var api = getApiDescriptor(requestMappingInfo);
    var builder = SimpleWebAPIModel.builder()
        .api(api)
        .scope(finalScope)
        .handlerType(HandlerType.REQUEST_MAPPING)
        .extraProp("className", handlerType.getName())
        .extraProp("methodName", getMethodDescriptor(method));
    return openAPIExtractor.extract(builder, requestMappingInfo, handlerType, method).build();
  }

  /**
   * 保存路由映射，如果存在多路径匹配同一个方法，则拆分成多个路由映射
   */
  private void saveRequestMapping(RequestMappingInfo mappingInfo, Class<?> handlerType, Method method) {
    var pathStream = getPathStream(mappingInfo);
    var methods = mappingInfo.getMethodsCondition().getMethods();
    final var finalMethods = methods.size() == 0 ? new HashSet<>(Arrays.asList(RequestMethod.values())) : methods;

    pathStream
        .flatMap(path -> finalMethods.stream().map(httpMethod -> Tuple.of(httpMethod, path)))
        .forEach(tuple -> {
          var newInfo = rebuildRequestMappingInfo(mappingInfo, tuple._1(), tuple._2());
          if (originalRequestMappingInfoList.stream().noneMatch(tuple3 -> tuple3._1().equals(newInfo))) {
            originalRequestMappingInfoList.add(Tuple.of(newInfo, handlerType, method));
          }
        });
  }

  /**
   * 根据WebApiModel构建路由映射
   *
   * @param mappingInfo 原始路由映射
   * @param model       WebApiModel
   * @return 新的路由映射
   */
  private RequestMappingInfo rebuildRequestMappingInfo(RequestMappingInfo mappingInfo, WebAPIModel model) {
    var apiParts = Optional
        .ofNullable(model.getApi())
        .map(s -> s.split(" "))
        .map(parts -> parts.length == 2 ? parts : new String[]{"GET", parts[0]})
        .orElseGet(() -> new String[]{"GET", "/"});
    var httpMethod = apiParts[0].toUpperCase();
    var path = apiParts[1];

    // 判断httpMethod是否合法
    if (Arrays.stream(RequestMethod.values()).noneMatch(method -> method.name().equals(httpMethod))) {
      return null;
    }

    if (mappingInfo == null) {
      return RequestMappingInfo.paths(path)
          .methods(RequestMethod.valueOf(httpMethod))
          .options(getBuilderConfiguration())
          .build();
    }

    return rebuildRequestMappingInfo(mappingInfo, RequestMethod.valueOf(httpMethod), path);
  }

  /**
   * 根据 method 和 pattern 构建路由映射
   */
  private RequestMappingInfo rebuildRequestMappingInfo(RequestMappingInfo prototype, RequestMethod method, String pathString) {
    var builder = RequestMappingInfo
        .paths(WebAPIUtils.pathNormalizer(pathString))
        .methods(method)
        .params(getNameValueStrings(prototype.getParamsCondition().getExpressions()))
        .headers(getNameValueStrings(prototype.getHeadersCondition().getExpressions()))
        .consumes(getMediaTypeStrings(prototype.getConsumesCondition().getExpressions()))
        .produces(getMediaTypeStrings(prototype.getProducesCondition().getExpressions()));

    if (prototype.getName() != null) {
      builder.mappingName(prototype.getName());
    }
    if (prototype.getCustomCondition() != null) {
      builder.customCondition(prototype.getCustomCondition());
    }

    return builder.options(getBuilderConfiguration()).build();
  }

  /**
   * 根据WebApiModel构建路由映射
   *
   * @param webAPIModels WebApiModel列表
   * @return 路由映射列表
   */
  private List<Tuple3<RequestMappingInfo, Class<?>, Method>> buildRequestMappingList(Collection<WebAPIModel> webAPIModels) {
    // 先根据 WebApiModel 构建 Class 和 Method 映射
    final var modelMappingTupleList = webAPIModels.stream()
        .filter(model -> model.getHandlerType() == HandlerType.REQUEST_MAPPING)
        .map(model -> {
          var methodDescriptor = model.getExtraProp(String.class, "methodName").orElse(null);
          var handlerType = model.getExtraProp(String.class, "className").orElse(null);

          // 基本参数校验
          if (ObjectUtils.isEmpty(methodDescriptor) || ObjectUtils.isEmpty(handlerType)) {
            var exception = new PlatformException("method or handlerType is empty, WebApiMode: " + model.getApi());
            logger.error(exception.getMessage(), exception);
            return null;
          }

          // 尝试获取方法
          var clazz = Try.of(() -> Class.forName(handlerType));
          var method = clazz.map(c -> getMethodFromClass(c, methodDescriptor));

          // 尝试获取方法失败，记录错误日志
          if (method.isFailure()) {
            var exception = new PlatformException("can not find method: " + methodDescriptor + " in class: " + handlerType, method.getCause());
            logger.error(exception.getMessage(), exception);
            return null;
          }
          return Tuple.<WebAPIModel, Class<?>, Method>of(model, clazz.get(), method.get());
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    // 构建路由映射
    return modelMappingTupleList.stream()
        .map(tuple -> {
          // 先检查 WebApi 的 api 属性是否合法
          final var api = Optional.ofNullable(tuple._1().getApi()).orElse("");
          final var apiPattern = Pattern.compile("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS|TRACE) .*$");
          if (!apiPattern.matcher(api).matches()) {
            var exception = new PlatformException("api is not valid, WebApiModel: " + tuple._1().getApi());
            logger.error(exception.getMessage(), exception);
            return null;
          }
          var model = tuple._1();
          var clazz = tuple._2();
          var method = tuple._3();

          // 尝试获取原始路由映射
          var originalMappingInfo = getOriginalMappingInfo(clazz, method);
          // 根据原始路由映射和 WebApiModel 构建新的路由映射
          var newMappingInfo = rebuildRequestMappingInfo(originalMappingInfo, model);

          return Tuple.<RequestMappingInfo, Class<?>, Method>of(newMappingInfo, clazz, method);
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * 从方法中提取方法描述：
   * 例如：public void test(String arg1, int arg2) 的方法描述为：test(java.lang.String,int)
   */
  private String getMethodDescriptor(Method method) {
    var methodName = method.getName();
    var methodParams = Arrays.stream(method.getParameterTypes()).map(this::getClassName).collect(Collectors.joining(","));
    return methodName + "(" + methodParams + ")";
  }

  private String getApiDescriptor(RequestMappingInfo mappingInfo) {
    final var method = mappingInfo.getMethodsCondition().getMethods().stream().findFirst().orElse(RequestMethod.GET);
    return method.name() + " " + getFirstPath(mappingInfo);
  }

  private String getRequestMappingMetadataApi(RequestMappingInfo mappingInfo) {
    final var method = mappingInfo.getMethodsCondition().getMethods().stream().findFirst().orElse(RequestMethod.GET);
    return method.name() + " " + getFirstPath(mappingInfo);
  }

  /**
   * 从类中获取方法对象
   */
  @SuppressWarnings("java:S3338")
  private Method getMethodFromClass(Class<?> clazz, String methodDescriptor) {
    var method = Try.of(() -> {
      // 检查方法描述是否合法
      if (!methodDescriptor.contains("(") || !methodDescriptor.contains(")")) {
        throw new PlatformException("method descriptor is invalid: " + methodDescriptor
            + ", should be like: methodName(paramType1,paramType2,...)");
      }

      // 提取 方法名 和 参数类型
      var methodName = methodDescriptor.substring(0, methodDescriptor.indexOf("("));
      var methodParams = methodDescriptor
          .substring(methodDescriptor.indexOf("(") + 1, methodDescriptor.indexOf(")"))
          .split(",");

      // 尝试获取参数类型
      var paramTypes = Arrays.stream(methodParams)
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(this::getClassFromName)
          .collect(Collectors.toList());

      // 检查参数类型是否都获取成功
      if (paramTypes.stream().anyMatch(Objects::isNull)) {
        throw new PlatformException("method descriptor is invalid: " + methodDescriptor);
      }

      // 获取方法
      return clazz.getMethod(methodName, paramTypes.toArray(new Class[]{}));
    });

    // 检查方法是否获取成功
    return method.getOrElseThrow((e) -> new PlatformException("can not find method " + methodDescriptor + " in class " + clazz.getName(), e));
  }

  private String getClassName(Class<?> clazz) {
    return clazz.isArray() ? getClassName(clazz.getComponentType()) + "[]" : clazz.getName();
  }

  /**
   * 从类名中获取类对象（兼容原始类型）
   */
  private Class<?> getClassFromName(String className) {
    if (className == null) {
      return null;
    }

    // 尝试获取数组类型
    var clazz = getArrayClass(className);
    if (clazz != null) {
      return clazz;
    }

    // 尝试获取原始类型
    clazz = getPrimitiveClass(className);
    if (clazz != null) {
      return clazz;
    }

    // 尝试获取普通类型
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private Class<?> getArrayClass(String className) {
    if (!className.endsWith("[]")) {
      return null;
    }
    var componentClassName = className.substring(0, className.length() - 2);
    var componentClass = getClassFromName(componentClassName);
    if (componentClass == null) {
      return null;
    }
    return Array.newInstance(componentClass, 0).getClass();
  }

  /**
   * 获取原始类型的Class
   *
   * @param className 类名
   * @return 类对象
   */
  private Class<?> getPrimitiveClass(String className) {
    return switch (className) {
      case "int" -> int.class;
      case "long" -> long.class;
      case "short" -> short.class;
      case "byte" -> byte.class;
      case "float" -> float.class;
      case "double" -> double.class;
      case "char" -> char.class;
      case "boolean" -> boolean.class;
      default -> null;
    };
  }

  /**
   * 从未注册的RequestMappingInfo中获取原始的RequestMappingInfo
   */
  private RequestMappingInfo getOriginalMappingInfo(Class<?> clazz, Method method) {
    var found = originalRequestMappingInfoList.stream()
        .filter(tuple -> tuple._2().equals(clazz) && tuple._3().equals(method))
        .findFirst();
    return found.map(Tuple3::_1).orElse(null);
  }

  private Stream<String> getPathStream(RequestMappingInfo mappingInfo) {
    var pathPatterns = mappingInfo.getPathPatternsCondition();
    if (pathPatterns != null) {
      return pathPatterns.getPatterns().stream().map(PathPattern::getPatternString);
    }
    var patterns = mappingInfo.getPatternsCondition();
    if (patterns != null) {
      return patterns.getPatterns().stream();
    }
    return Stream.empty();
  }

  private String getFirstPath(RequestMappingInfo mappingInfo) {
    var pathPatterns = mappingInfo.getPathPatternsCondition();
    if (pathPatterns != null) {
      return pathPatterns.getPatterns().stream().findFirst().map(PathPattern::getPatternString).orElse("/");
    }
    var patterns = mappingInfo.getPatternsCondition();
    if (patterns != null) {
      return patterns.getPatterns().stream().findFirst().orElse("/");
    }
    return "/";
  }

  private String[] getNameValueStrings(Set<NameValueExpression<String>> expressions) {
    return expressions.stream()
        .map(NameValueExpression::toString)
        .toArray(String[]::new);
  }

  private String[] getMediaTypeStrings(Set<MediaTypeExpression> expressions) {
    return expressions.stream()
        .map(MediaTypeExpression::toString)
        .toArray(String[]::new);
  }

  @Priority(0)
  public static class Proxy implements WebAPIModelProvider {
    static RequestMappingIntegrationCenter instance;

    @Override
    public Map<String, WebAPIModel> getWebAPIModelMap() {
      return Optional.ofNullable(instance).map(
          RequestMappingIntegrationCenter::getApiModelMap
      ).orElse(Collections.emptyMap());
    }
  }
}
