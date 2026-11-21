package com.ouroboros.rpc.security;

import java.util.*;
import java.util.function.Function;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import io.vavr.control.Try;

import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.http.HttpRPCRequestParameterDefine;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.rpc.util.ExpressionUtils;

public abstract class AbstractAccessTokenRPCAuthCredential extends AbstractRPCAuthCredential<HttpRPCRequest> {
  private static final ThreadLocal<Set<String>> ACCESS_TOKEN_FETCHING = ThreadLocal.withInitial(HashSet::new);
  private final List<HttpRPCRequestParameterDefine> placementList;
  private final Function<String, String> configGetter;

  public AbstractAccessTokenRPCAuthCredential(RPCCredentialMetadata metadata, Function<String, String> configGetter) {
    super(metadata);
    this.placementList = getTokenPlacement();
    this.configGetter = configGetter;
  }

  private List<HttpRPCRequestParameterDefine> getTokenPlacement() {
    String placement = getExtraPropDeep(String.class, "accessToken", "tokenPlacement").orElse("Authorization");
    Object rawCustomPlacement = getExtraPropDeep("accessToken", "customPlacement").orElse(null);
    if ("Authorization".equalsIgnoreCase(placement) || ObjectUtils.isEmpty(rawCustomPlacement)) {
      HttpRPCRequestParameterDefine parameter = new HttpRPCRequestParameterDefine();
      parameter.setIn(HttpRPCRequestParameterDefine.InScope.HEADER.name());
      parameter.setName("Authorization");
      parameter.setValue("Bearer ${accessToken}");
      return Collections.singletonList(parameter);
    } else {
      List<Map<String, String>> customPlacement = new ArrayList<>();
      List<HttpRPCRequestParameterDefine> placementList = new ArrayList<>();
      if (rawCustomPlacement instanceof Map<?, ?> map) {
        customPlacement.add((Map) map);
      } else if (rawCustomPlacement instanceof List<?> list) {
        for (Object item : list) {
          if (item instanceof Map<?, ?> map) {
            customPlacement.add((Map) map);
          }
        }
      }
      for (Map<String, String> item : customPlacement) {
        String in = item.get("in");
        String name = item.get("name");
        String value = item.get("value");
        if (StringUtils.isAnyBlank(in, name)) {
          continue;
        }
        HttpRPCRequestParameterDefine parameter = new HttpRPCRequestParameterDefine();
        parameter.setIn(in);
        parameter.setName(name);
        parameter.setValue(value);
        placementList.add(parameter);
      }
      return placementList;
    }
  }

  protected abstract String getAccessToken();

  protected String getAccessToken(Map<String, Object> context) {
    return getAccessToken();
  }

  protected boolean enterAccessTokenFetch() {
    return ACCESS_TOKEN_FETCHING.get().add(getName());
  }

  protected void exitAccessTokenFetch() {
    Set<String> fetching = ACCESS_TOKEN_FETCHING.get();
    fetching.remove(getName());
    if (fetching.isEmpty()) {
      ACCESS_TOKEN_FETCHING.remove();
    }
  }

  @Override
  //TODO: 异常需要统一优化
  public Try<HttpRPCRequest> applyToRequest(HttpRPCRequest request, Map<String, Object> context) {
    String accessToken;
    try {
      accessToken = getAccessToken(context);
    } catch (AuthenticateException exception) {
      return Try.failure(exception);
    }

    if (StringUtils.isBlank(accessToken)) {
      return Try.failure(new AuthenticateException("认证凭据[" + getName() + "]获取令牌失败"));
    }

    return applyAccessToken(request, context, accessToken);
  }

  protected Try<HttpRPCRequest> applyAccessToken(HttpRPCRequest request, Map<String, Object> context, String accessToken) {

    Map<String, Object> contextMap = new HashMap<>(context);
    contextMap.put("accessToken", accessToken);

    for (HttpRPCRequestParameterDefine placement : placementList) {
      var valueTry = ExpressionUtils
          .eval(placement.getValue(), contextMap, configGetter)
          .map(Object::toString);
      if (valueTry.isLeft()) {
        return Try.failure(new AuthenticateException("认证凭据[" + getName() + "]生成令牌位置参数失败，参数名："
            + placement.getName() + "，参数位置：" + placement.getIn(), valueTry.getLeft()));
      }

      request.setToPart(placement.getIn(), placement.getName(), valueTry.get());
    }

    return Try.success(request);
  }
}
