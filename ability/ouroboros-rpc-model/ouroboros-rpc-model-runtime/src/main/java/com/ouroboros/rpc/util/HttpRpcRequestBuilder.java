package com.ouroboros.rpc.util;

import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.http.HttpRPCRequestDefine;

/**
 * @author liansz
 **/
@Component
public class HttpRpcRequestBuilder {


  private static ConfigurationService defaultConfigurationService;
  private static Environment defaultEnvironment;
  private final ConfigurationService configurationService;
  private final Environment environment;

  public HttpRpcRequestBuilder(ConfigurationService configurationService, Environment environment) {
    this.configurationService = configurationService;
    this.environment = environment;
    HttpRpcRequestBuilder.defaultConfigurationService = configurationService;
    HttpRpcRequestBuilder.defaultEnvironment = environment;
  }

  private static String configGetter(String name) {
    return configGetter(defaultConfigurationService, defaultEnvironment, name);
  }

  private String instanceConfigGetter(String name) {
    return configGetter(configurationService, environment, name);
  }

  private static String configGetter(ConfigurationService configurationService, Environment environment, String name) {
    if (configurationService == null && environment == null) {
      return null;
    }
    if (configurationService == null) {
      return environment.getProperty(name);
    }
    return configurationService.get(String.class, name)
        .orElseGet(() -> environment != null ? environment.getProperty(name) : null);
  }

  /**
   * 构建HTTP请求
   *
   * @param requestDefine
   * @param context
   * @return
   */
  public static Try<HttpRPCRequest> build(HttpRPCRequestDefine requestDefine, Map<String, Object> context) {
    return buildConfiguredRequest(com.ouroboros.util.Maps.fromBean(requestDefine), context, HttpRpcRequestBuilder::configGetter);
  }

  public Try<HttpRPCRequest> buildRequest(HttpRPCRequestDefine requestDefine, Map<String, Object> context) {
    return buildConfiguredRequest(com.ouroboros.util.Maps.fromBean(requestDefine), context, this::instanceConfigGetter);
  }

  public static Try<HttpRPCRequest> buildRequest(HttpRPCRequestDefine definition, Map<String, Object> context,
      Function<String, String> getter) {
    return buildConfiguredRequest(com.ouroboros.util.Maps.fromBean(definition), context, getter);
  }

  public String configurationValue(String source) {
    return RpcConfigurationTemplates.resolve(source, this::instanceConfigGetter);
  }

  public Try<HttpRPCRequest> buildConfiguredRequest(Map<String, Object> definition, Map<String, Object> context) {
    return buildConfiguredRequest(definition, context, this::instanceConfigGetter);
  }

  public static Try<HttpRPCRequest> buildConfiguredRequest(Map<String, Object> definition, Map<String, Object> context,
      Function<String, String> getter) {
    return Try.of(() -> {
      var resolved = RpcConfigurationTemplates.resolveFields(definition, "rpc-model", "", "/request", getter);
      var requestDefine = com.ouroboros.util.Maps.tryToBean(HttpRPCRequestDefine.class, resolved).get();
      var urlResult = ExpressionUtils.eval(requestDefine.getUrl(), context, getter);
      if (urlResult.isLeft()) throw new IllegalArgumentException("解析RPC URL失败", urlResult.getLeft());
      var url = urlResult.get();
      if (ObjectUtils.isEmpty(url)) throw new IllegalArgumentException("URL不能为空");
      var request = new HttpRPCRequest();
      request.setUrl(url.toString());
      request.setMethod(requestDefine.getMethod());
      for (var parameter : requestDefine.getParameters()) {
        if (parameter.getIn() == null || parameter.getName() == null || parameter.getName().isBlank()) {
          throw new IllegalArgumentException("RPC参数名称或位置无效");
        }
        var result = ExpressionUtils.eval(parameter.getValue(), context, getter);
        if (result.isLeft()) throw new IllegalArgumentException("解析RPC参数失败: " + parameter.getName(), result.getLeft());
        if (parameter.isRequired() && ObjectUtils.isEmpty(result.get())) {
          throw new IllegalArgumentException("参数: " + parameter.getName() + " 不能为空");
        }
        request.setToPart(parameter.getIn(), parameter.getName(), result.get());
      }
      return request;
    });
  }
}
