package com.ouroboros.businesslog.util;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.ouroboros.businesslog.BusinessOperationTemplate;
import com.ouroboros.businesslog.constant.OperationResultEnum;
import com.ouroboros.businesslog.model.BusinessLogModel;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.context.OrganizationContext;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.util.JsonBag;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 **/
public class BusinessLogExtractor {

  private static StringTemplateEngine templateEngine;

  public static BusinessLogModel extract(BusinessOperationTemplate template,
                                         Map<String, String> pathVariables,
                                         ContentCachingRequestWrapper request,
                                         ContentCachingResponseWrapper response) {
    String operatorUserIdentity = CurrentSecurityContextHolder.findIdentity()
        .map(identity -> identity.getUserIdentity())
        .orElse(null);
    Employee operatorEmployee = OrganizationContextHolder.getContext()
        .flatMap(OrganizationContext::getEmployee)
        .orElse(null);

    Map<String, Object> dataModel = createContextMap(pathVariables, request, response);

    String summary = getTemplateEngine().generate(template.getSummaryTemplate(), dataModel);

    // 请求信息脱敏
    Map<String, Object> payload = maskData((Map<String, Object>) dataModel.get("Request"), template.getSensitiveFields());

    // 获取响应体
    Map<String, Object> responseBody = ((Map<String, Map<String, Object>>) dataModel.get("Response")).get("body");

    // 操作结果
    OperationResultEnum operationResult = getOperationResult(response.getStatus(), responseBody);

    // 响应信息脱敏
    Map<String, Object> saveResponse = template.isShouldSaveResponseBody() ? maskData(responseBody, template.getSensitiveFields()) : null;

    // 业务键
    String businessKey = Optional.ofNullable(dataModel.get(template.getBusinessKeyName()))
        .map(Object::toString)
        .orElse(null);

    String ipAddress = Optional.ofNullable(dataModel.get("IpAddress")).map(Object::toString).orElse(null);

    BusinessLogModel model = new BusinessLogModel();
    model.setModuleName(template.getModuleName());
    model.setOperation(template.getOperation());
    model.setLevel(template.getLevel());
    model.setOperator(operatorUserIdentity);
    model.setEmployee(operatorEmployee);
    model.setOperationTime(LocalDateTime.now());
    model.setBusinessKey(businessKey);
    model.setSummary(summary);
    model.setPayload(JsonBag.of(payload));
    model.setOperationResult(operationResult.getValue());
    model.setResponseBody(JsonBag.of(saveResponse));
    model.setUserAgent(request.getHeader("User-Agent"));
    model.setIpAddress(ipAddress);

    return model;
  }

  private static StringTemplateEngine getTemplateEngine() {
    if (templateEngine == null) {
      synchronized (BusinessLogExtractor.class) {
        if (templateEngine == null) {
          templateEngine = new StringTemplateEngine();
        }
      }
    }

    return templateEngine;
  }

  private static OperationResultEnum getOperationResult(int httpStatus, Map<String, Object> responseBody) {
    if (httpStatus == 200 && responseBody.containsKey("code")) {
      return Objects.equals(responseBody.get("code"), 0)
          ? OperationResultEnum.SUCCESS
          : OperationResultEnum.FAILURE;
    }
    if (httpStatus > 200 && httpStatus < 400) {
      return OperationResultEnum.SUCCESS;
    }
    return OperationResultEnum.FAILURE;
  }

  // 数据脱敏
  private static Map<String, Object> maskData(Map<String, Object> data, List<String> sensitiveFields) {
    if (ObjectUtils.isEmpty(data) || ObjectUtils.isEmpty(sensitiveFields)) {
      return data;
    }

    Map<String, Object> finalData = Maps.deepClone(data);
    finalData.entrySet().stream()
        .flatMap(entry -> {
          if (entry.getValue() instanceof Map<?, ?> mapValue) {
            return mapValue.entrySet().stream();
          }
          if (entry.getValue() instanceof List<?> listValue
              && listValue.stream().allMatch(Map.class::isInstance)) {
            return listValue.stream().map(Map.class::cast)
                .flatMap(map -> ((Map<?, ?>) map).entrySet().stream());
          }
          return Stream.of(entry);
        })
        .filter(entry -> sensitiveFields.contains(entry.getKey()))
        .forEach(entry -> ((Map.Entry<String, Object>) entry).setValue("************"));
    return finalData;
  }

  // 创建上下文对象
  private static Map<String, Object> createContextMap(Map<String, String> pathVariables,
                                                      ContentCachingRequestWrapper request,
                                                      ContentCachingResponseWrapper response) {

    LazyRequestMap requestInfoMap = new LazyRequestMap(request);
    LazyResponseMap responseMap = new LazyResponseMap(response);
    Optional<OrganizationContext> organizationContext = OrganizationContextHolder.getContext();

    LazyMap context = new LazyMap()
        // 这些固定的 KeyValue 值优先级最高
        .addLazyKV("Request", () -> requestInfoMap)
        .addLazyKV("Response", () -> responseMap)
        .addLazyKV("Auth", () -> CurrentSecurityContextHolder.findAuthentication().orElse(null))
        .addLazyKV("Employee", () -> organizationContext.map(OrganizationContext::getEmployee).orElse(null))
        .addLazyKV("Department", () -> organizationContext.map(OrganizationContext::getDepartment).orElse(null))
        .addLazyKV("Company", () -> organizationContext.map(OrganizationContext::getCompany).orElse(null))
        .addLazyKV("IpAddress", () -> requestInfoMap.get("ipAddress"))

        // 添加动态的，优先级为 路径变量 > QueryString > 请求体 > 响应体
        // 路径变量
        .addDynamicKVResolver(pathVariables::get)
        // QueryString
        .addDynamicKVResolver(k -> {
          if (requestInfoMap.get("params") instanceof Map paramsMap) {
            return paramsMap.get(k);
          }
          return null;
        })
        // 请求体
        .addDynamicKVResolver(k -> {
          if (requestInfoMap.get("body") instanceof Map bodyMap) {
            return bodyMap.get(k);
          }
          return null;
        })
        // 响应体（解开包裹后）
        .addDynamicKVResolver(k -> {
          if (responseMap.get("data") instanceof Map dataMap) {
            return dataMap.get(k);
          }
          return null;
        });
    return context;
  }
}
