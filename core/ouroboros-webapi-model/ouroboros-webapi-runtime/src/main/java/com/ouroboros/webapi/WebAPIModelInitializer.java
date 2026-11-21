package com.ouroboros.webapi;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.util.Maps;
import com.ouroboros.util.SPI;
import com.ouroboros.webapi.openapi.ExternalDocsModel;
import com.ouroboros.webapi.openapi.ParameterModel;
import com.ouroboros.webapi.openapi.RequestBodyModel;
import com.ouroboros.webapi.openapi.ResponseModel;
import com.ouroboros.webapi.openapi.SecurityRequirementModel;
import com.ouroboros.webapi.openapi.ServerModel;
import com.ouroboros.webapi.util.WebAPIUtils;

@Component
public class WebAPIModelInitializer {

  @Autowired
  ListableBeanFactory beanFactory;

  private List<WebAPIRouteRegistry> getAllRegistry() {
    return new ArrayList<>(beanFactory.getBeansOfType(WebAPIRouteRegistry.class, true, false).values());
  }

  private List<WebAPIModelProvider> getAllProviders() {
    return SPI.getCachedReversedServiceStream(WebAPIModelProvider.class).collect(Collectors.toList());
  }

  @EventListener
  @Order(0)
  public void onOuroborosContextRefreshed(OuroborosContextRefreshedEvent event) {
    Map<String, WebAPIModel> webapiModels = getAllProviders().stream().map(provider ->
        provider.getWebAPIModelMap().entrySet().stream().collect(
            Collectors.toMap(
                entry -> getSimplifiedApiDescriptor(entry.getKey()),
                entry -> (WebAPIModel) new NormalizerWebAPIModel(entry.getValue()),
                (v1, v2) -> v2
            )
        )
    ).reduce(new HashMap<>(), (a, b) -> Maps.merge(() -> new HashMap<>(), a, b));

    getAllRegistry().forEach(injector -> injector.register(new ArrayList<>(webapiModels.values())));
  }

  /**
   * 把 api描述 转化为简化的 api 描述，
   * 例如：GET /test<br>
   * 如果存在模式匹配，则去掉模式匹配的参数名，例如：GET /test/{id} -> GET /test/{} <br>
   * GET /test/{id}/{*path} -> GET /test/{}/{*} <br>
   *
   * @param api API地址
   * @return api 描述
   */
  private String getSimplifiedApiDescriptor(String api) {
    String[] apis = api.split(" ");
    String method = apis[0];
    String path = apis[1];

    path = WebAPIUtils.pathNormalizer(path);

    api = method + " " + path;

    // 去掉模式匹配的参数名
    final var variblePrefixPattern = Pattern.compile("(\\{\\*?)\\w+}");
    final var matcher = variblePrefixPattern.matcher(api);

    // 拼接方法名和路径
    return matcher.replaceAll("$1}");
  }

  private static class NormalizerWebAPIModel implements WebAPIModel {
    private final WebAPIModel model;

    public NormalizerWebAPIModel(WebAPIModel model) {
      this.model = model;
    }

    @Override
    public String getMethod() {
      return model.getMethod();
    }

    @Override
    public String getPath() {
      return WebAPIUtils.pathNormalizer(model.getPath());
    }

    @Override
    public String getApi() {
      return getMethod() + " " + getPath();
    }

    @Override
    public WebAPIScope getScope() {
      return model.getScope();
    }

    @Override
    public HandlerType getHandlerType() {
      return model.getHandlerType();
    }

    @Override
    public String getSummary() {
      return model.getSummary();
    }

    @Override
    public String getDescription() {
      return model.getDescription();
    }

    @Override
    public String getOperationId() {
      return model.getOperationId();
    }

    @Override
    public List<String> getTags() {
      return model.getTags();
    }

    @Override
    public List<ParameterModel> getParameters() {
      return model.getParameters();
    }

    @Override
    public RequestBodyModel getRequestBody() {
      return model.getRequestBody();
    }

    @Override
    public Map<String, ResponseModel> getResponses() {
      return model.getResponses();
    }

    @Override
    public Boolean getDeprecated() {
      return model.getDeprecated();
    }

    @Override
    public List<SecurityRequirementModel> getSecurity() {
      return model.getSecurity();
    }

    @Override
    public List<ServerModel> getServers() {
      return model.getServers();
    }

    @Override
    public ExternalDocsModel getExternalDocs() {
      return model.getExternalDocs();
    }

    @Override
    public Map<String, Object> getExtraProps() {
      return model.getExtraProps();
    }

    @Override
    public Optional<Object> getExtraProp(String key) {
      return model.getExtraProp(key);
    }
  }
}
