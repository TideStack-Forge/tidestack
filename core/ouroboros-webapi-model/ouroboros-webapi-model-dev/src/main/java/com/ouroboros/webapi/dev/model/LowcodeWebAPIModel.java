package com.ouroboros.webapi.dev.model;

import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIScope;

/**
 * @author liansz
 **/
public class LowcodeWebAPIModel implements WebAPIModel {

  private String method;
  private String path;
  private WebAPIScope scope;
  private HandlerType handlerType;
  private String summary;
  private Map<String, Object> extraProps = new HashMap<>();

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static LowcodeWebAPIModel fromDetails(Map<String, Object> details) {
    LowcodeWebAPIModel model = new LowcodeWebAPIModel();
    if (details == null || details.isEmpty()) {
      return model;
    }

    model.setMethod(asString(details.get("method")));
    model.setPath(asString(details.get("path")));
    model.setScope(asScope(details.get("scope")));
    model.setHandlerType(asHandlerType(details.get("handlerType")));
    model.setSummary(asString(details.get("summary")));

    Map<String, Object> extraProps = new LinkedHashMap<>();
    details.forEach((key, value) -> {
      if (!Objects.equals(key, "method")
          && !Objects.equals(key, "path")
          && !Objects.equals(key, "scope")
          && !Objects.equals(key, "handlerType")
          && !Objects.equals(key, "summary")) {
        if (Objects.equals(key, "extraProps") && value instanceof Map) {
          mergeExtraProps(extraProps, (Map<?, ?>) value);
        } else {
          extraProps.put(key, value);
        }
      }
    });
    model.setExtraProps(extraProps);
    return model;
  }

  private static String asString(Object value) {
    return value instanceof String ? (String) value : null;
  }

  private static WebAPIScope asScope(Object value) {
    if (value instanceof WebAPIScope) {
      return (WebAPIScope) value;
    }

    if (value instanceof String) {
      String text = (String) value;
      WebAPIScope scope = WebAPIScope.fromValue(text);
      if (scope != null) {
        return scope;
      }

      try {
        return WebAPIScope.valueOf(text);
      } catch (IllegalArgumentException exception) {
        return null;
      }
    }

    if (value instanceof Map) {
      Object nested = ((Map<?, ?>) value).get("value");
      return asScope(nested);
    }

    return null;
  }

  private static HandlerType asHandlerType(Object value) {
    if (value instanceof HandlerType) {
      return (HandlerType) value;
    }

    if (value instanceof String) {
      String text = (String) value;
      HandlerType handlerType = HandlerType.fromValue(text);
      if (handlerType != null) {
        return handlerType;
      }

      try {
        return HandlerType.valueOf(text);
      } catch (IllegalArgumentException exception) {
        return null;
      }
    }

    if (value instanceof Map) {
      Object nested = ((Map<?, ?>) value).get("value");
      return asHandlerType(nested);
    }

    return null;
  }

  private static void mergeExtraProps(Map<String, Object> target, Map<?, ?> source) {
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      if (entry.getKey() instanceof String) {
        target.put((String) entry.getKey(), entry.getValue());
      }
    }
  }

  public String getFullName() {
    if (StringUtils.isEmpty(getMethod()) || StringUtils.isEmpty(getPath())) {
      return null;
    }
    String pathPart = Arrays.stream(getPath().split("/")).filter(StringUtils::isNotEmpty)
        .collect(Collectors.joining("."));
    return pathPart + "." + getMethod();
  }

  @Override
  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  @Override
  public String getPath() {
    return path != null
        ? path.startsWith("/") ? path : "/" + path
        : null;
  }

  public void setPath(String path) {
    this.path = path;
  }

  @Override
  public String getApi() {
    return getMethod() + " " + getPath();
  }

  @Override
  public WebAPIScope getScope() {
    return scope;
  }

  public void setScope(WebAPIScope scope) {
    this.scope = scope;
  }

  @Override
  public HandlerType getHandlerType() {
    return handlerType;
  }

  public void setHandlerType(HandlerType handlerType) {
    this.handlerType = handlerType;
  }

  @Override
  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  @Override
  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  @Override
  public Optional<Object> getExtraProp(String key) {
    return Optional.ofNullable(extraProps.get(key));
  }

  public void setExtraProps(Map<String, Object> extraProps) {
    this.extraProps = extraProps;
  }

  @JsonAnySetter
  public void setExtraProp(String key, Object value) {
    if (Objects.equals(key, "fullName")) {
      return;
    }

    extraProps.put(key, value);
  }
}
