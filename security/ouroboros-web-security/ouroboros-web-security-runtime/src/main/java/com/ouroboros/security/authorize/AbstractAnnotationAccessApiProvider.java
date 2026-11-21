package com.ouroboros.security.authorize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * @author liansz
 */
public abstract class AbstractAnnotationAccessApiProvider {

  @Autowired
  ApplicationContext applicationContext;

  /**
   * 通过注解获取接口列表
   *
   * @param filter
   * @return
   */
  protected List<String> getAccessApis(Predicate<HandlerMethod> filter) {
    Map<RequestMappingInfo, HandlerMethod> handlerMethods = getHandlerMethodMap();

    return handlerMethods.keySet().stream()
        .filter(info -> ObjectUtils.isNotEmpty(info.getPatternValues()))
        .filter(info -> filter.test(handlerMethods.get(info)))
        .flatMap(info -> buildAccessApisByRequestMappingInfo(info).stream())
        .collect(Collectors.toList());
  }

  /**
   * 获取处理方法map
   *
   * @return
   */
  protected Map<RequestMappingInfo, HandlerMethod> getHandlerMethodMap() {
    return applicationContext.getBeansOfType(RequestMappingHandlerMapping.class)
        .values()
        .stream()
        .flatMap(mapping -> mapping.getHandlerMethods().entrySet().stream())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,  (v1, v2) -> v2));
  }

  /**
   * 通过 RequestMappingInfo 构建访问接口列表
   *
   * @param info
   * @return
   */
  protected List<String> buildAccessApisByRequestMappingInfo(RequestMappingInfo info) {
    // 方法名称
    List<String> methodsNames = info.getMethodsCondition().getMethods().stream()
        .map(RequestMethod::name)
        .collect(Collectors.toList());

    // 如果没有方法名称，则取所有方法名称
    if (ObjectUtils.isEmpty(methodsNames)) {
      methodsNames.add("*");
    }

    // 路径
    List<String> patternPaths = new ArrayList<>(info.getPatternValues());

    // 取 方法名称 与 路径 笛卡尔积
    return methodsNames.stream()
        .flatMap(methodsName ->
            patternPaths.stream().map(patternPath -> Stream.of(methodsName, patternPath).collect(Collectors.toList()))
        )
        .collect(
            ArrayList::new,
            (list, path) -> list.add(String.join(" ", path)),
            List::addAll
        );
  }
}
