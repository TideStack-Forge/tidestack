package com.ouroboros.rpc.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.http.HttpRPCRequestParameterDefine;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.rpc.util.ExpressionUtils;

/**
 * @author liansz
 **/
@Component
public class PlainRpcCredential {
  private static final Logger logger = LoggerFactory.getLogger(PlainRpcCredential.class);

  public PlainRpcCredential(ConfigurationService configurationService, Environment environment) {
    Implementation.configurationService = configurationService;
    Implementation.environment = environment;
  }

  public static class Builder implements RPCAuthCredentialBuilder<HttpRPCRequest> {
    @Override public java.util.Optional<CredentialType> credentialType() {
      return java.util.Optional.of(new CredentialType("PlainCredentials", "简单明文凭据"));
    }
    @Override
    public boolean support(RPCCredentialMetadata metadata) {
      return metadata != null && "PlainCredentials".equalsIgnoreCase(metadata.getType());
    }

    @Override
    public RPCAuthCredential<HttpRPCRequest> build(RPCCredentialMetadata metadata) {
      return new Implementation(com.ouroboros.rpc.util.RpcConfigurationTemplates.credential(metadata, key -> Implementation.configurationService.get(String.class, key).orElseGet(() -> Implementation.environment.getProperty(key))));
    }

    @Override
    public RPCAuthCredential<HttpRPCRequest> build(RPCCredentialMetadata metadata,
        com.ouroboros.rpc.execution.RpcExecutionResources resources) {
      return new Implementation(com.ouroboros.rpc.util.RpcConfigurationTemplates.credential(metadata, resources::getConfig), resources::getConfig);
    }
  }

  private static class Implementation extends AbstractRPCAuthCredential<HttpRPCRequest> {
    private static ConfigurationService configurationService;
    private static Environment environment;

    private final List<HttpRPCRequestParameterDefine> credentialParameters;
    private final Function<String, String> configGetter;

    public Implementation(RPCCredentialMetadata metadata) {
      this(metadata, key -> {
        var configured = configurationService == null ? java.util.Optional.<String>empty()
            : configurationService.get(String.class, key);
        return configured.orElseGet(() -> environment == null ? null : environment.getProperty(key));
      });
    }

    Implementation(RPCCredentialMetadata metadata, Function<String, String> configGetter) {
      super(metadata);
      this.configGetter = configGetter;
      this.credentialParameters = buildCredentialParameters();
    }

    private List<HttpRPCRequestParameterDefine> buildCredentialParameters() {
      Object rawCredentials = getExtraPropDeep(Object.class, "plainCredentials", "credentials").orElse(null);
      List<Object> credentialDefinitions = new ArrayList<>();

      // 稍微做一下兼容性处理
      if (rawCredentials instanceof Map<?, ?> singleCredential) {
        credentialDefinitions.add(singleCredential);
      }

      if (rawCredentials instanceof List<?> list) {
        credentialDefinitions.addAll(list);
      }

      List<HttpRPCRequestParameterDefine> parameters = new ArrayList<>();
      for (int index = 0; index < credentialDefinitions.size(); index++) {
        HttpRPCRequestParameterDefine parameter = buildCredentialParameter(credentialDefinitions.get(index), index);
        if (parameter != null) {
          parameters.add(parameter);
        }
      }
      return parameters;
    }

    private HttpRPCRequestParameterDefine buildCredentialParameter(
        Object credentialDefinition,
        int parameterIndex
    ) {
      if (!(credentialDefinition instanceof Map<?, ?> rawMap)) {
        warnInvalidDefinition(parameterIndex, "参数定义格式不正确");
        return null;
      }
      Map<String, Object> credentialParamMap = (Map<String, Object>) rawMap;

      var name = credentialParamMap.get("name");
      if (!(name instanceof String nameStr) || StringUtils.isBlank(nameStr)) {
        warnInvalidDefinition(parameterIndex, "参数名缺失或为空");
        return null;
      }

      var in = credentialParamMap.get("in");
      if (!(in instanceof String inStr) || StringUtils.isBlank(inStr)) {
        warnInvalidDefinition(parameterIndex, "参数位置缺失或为空");
        return null;
      }

      HttpRPCRequestParameterDefine.InScope inScope = HttpRPCRequestParameterDefine.InScope.fromValue(inStr);
      if (inScope == null) {
        warnInvalidDefinition(parameterIndex, "参数位置不受支持");
        return null;
      }

      var value = credentialParamMap.get("value");
      if (!(value instanceof String valueStr) || StringUtils.isBlank(valueStr)) {
        warnInvalidDefinition(parameterIndex, "参数值缺失或为空");
        return null;
      }

      HttpRPCRequestParameterDefine parameter = new HttpRPCRequestParameterDefine();
      parameter.setName(nameStr);
      parameter.setIn(inScope.name());
      parameter.setValue(valueStr);
      return parameter;
    }

    private void warnInvalidDefinition(int parameterIndex, String reason) {
      logger.warn("认证凭据[{}]明文认证参数定义无效，已跳过：参数索引[{}]，原因：{}",
          getName(), parameterIndex, reason);
    }

    private Either<Throwable, Object> evalValue(String valueTemplate, Map<String, Object> context) {
      return ExpressionUtils.eval(valueTemplate, context, configGetter);
    }

    @Override
    public Try<HttpRPCRequest> applyToRequest(HttpRPCRequest request, Map<String, Object> context) {
      if (credentialParameters.isEmpty()) {
        return Try.failure(new AuthenticateException("认证凭据[" + getName() + "]没有有效的明文认证参数定义"));
      }

      // 解析参数
      for (HttpRPCRequestParameterDefine credentialParameter : credentialParameters) {
        var value = evalValue(credentialParameter.getValue(), context);
        if (value.isLeft()) {
          return Try.failure(new AuthenticateException("认证凭据[" + getName() + "]生成明文认证参数失败，参数名："
              + credentialParameter.getName() + "，参数位置：" + credentialParameter.getIn(), value.getLeft()));
        }
        request.setToPart(credentialParameter.getIn(), credentialParameter.getName(), value.get());
      }
      return Try.success(request);
    }
  }
}
