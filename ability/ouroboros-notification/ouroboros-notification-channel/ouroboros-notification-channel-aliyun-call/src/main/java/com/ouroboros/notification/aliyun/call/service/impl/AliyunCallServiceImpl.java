package com.ouroboros.notification.aliyun.call.service.impl;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aliyun.dyvmsapi20170525.Client;
import com.aliyun.dyvmsapi20170525.models.QueryCallDetailByCallIdRequest;
import com.aliyun.dyvmsapi20170525.models.QueryCallDetailByCallIdResponse;
import com.aliyun.dyvmsapi20170525.models.QueryCallDetailByCallIdResponseBody;
import com.aliyun.dyvmsapi20170525.models.SingleCallByTtsRequest;
import com.aliyun.dyvmsapi20170525.models.SingleCallByTtsResponse;
import com.aliyun.dyvmsapi20170525.models.SingleCallByTtsResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.aliyun.call.adapter.AliyunCallNotificationChannelOptions;
import com.ouroboros.notification.aliyun.call.service.AliyunCallService;
import com.ouroboros.notification.aliyun.call.service.model.AliyunCallResult;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
@Service
public class AliyunCallServiceImpl implements AliyunCallService {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  ConfigurationService configurationService;

  @Override
  public String sendCall(String mobile, String templateCode,
                         Map<String, Object> payload, AliyunCallNotificationChannelOptions channelOptions) {
    SingleCallByTtsRequest request = buildRequest(mobile, templateCode, payload, channelOptions);
    SingleCallByTtsResponse response = null;
    RuntimeException exception = null;
    try {
      response = buildClient().singleCallByTts(request);
    } catch (Exception e) {
      exception = e instanceof RuntimeException ? (RuntimeException) e : new BusinessException("阿里云语音通知发送失败", e);
      throw exception;
    } finally {
      logger.debug("send request {}, response {}",
          Json.toJsonString(request),
          exception != null ? exception.getMessage() : Json.toJsonString(response)
      );
    }

    SingleCallByTtsResponseBody body = response.getBody();
    if (body == null) {
      throw new BusinessException("响应为空");
    }

    String code = body.getCode();
    if (!Objects.equals(code, "OK")) {
      throw new BusinessException(body.getMessage());
    }
    if (body.getCallId() == null || body.getCallId().isBlank()) {
      throw new BusinessException("阿里云语音通知未返回 callId");
    }
    return body.getCallId();
  }

  @Override
  public AliyunCallResult queryState(String callId, long prodId, long queryDate) {
    if (callId == null || callId.isBlank()) {
      throw new BusinessException("callId 不能为空");
    }

    QueryCallDetailByCallIdRequest request = new QueryCallDetailByCallIdRequest()
        .setCallId(callId)
        .setProdId(prodId)
        .setQueryDate(queryDate);

    QueryCallDetailByCallIdResponse response;
    try {
      response = buildClient().queryCallDetailByCallId(request);
    } catch (Exception e) {
      throw e instanceof RuntimeException ? (RuntimeException) e : new BusinessException("阿里云语音通知状态查询失败", e);
    }

    QueryCallDetailByCallIdResponseBody body = response == null ? null : response.getBody();
    if (body == null) {
      throw new BusinessException("查询响应为空");
    }
    if (!Objects.equals(body.getCode(), "OK")) {
      throw new BusinessException(body.getMessage());
    }

    return body.getData() != null ? Json.toBean(AliyunCallResult.class, body.getData()) : null;
  }

  /**
   * 构建请求参数
   *
   * @param mobile
   * @param templateCode
   * @param payload
   * @param channelOptions
   */
  private SingleCallByTtsRequest buildRequest(String mobile, String templateCode,
                                              Map<String, Object> payload, AliyunCallNotificationChannelOptions channelOptions) {
    SingleCallByTtsRequest request = new SingleCallByTtsRequest();
    request.setCalledShowNumber(channelOptions.getCalledShowNumber());
    request.setCalledNumber(mobile);
    request.setTtsCode(templateCode);
    request.setTtsParam(payload != null ? Json.toJsonString(payload) : null);
    request.setPlayTimes(channelOptions.getPlayTimes());
    request.setVolume(channelOptions.getVolume());
    request.setSpeed(channelOptions.getSpeed());
    request.setOutId(channelOptions.getOutId());

    return request;
  }

  /**
   * 构建客户端
   *
   * @return
   */
  private Client buildClient() {
    Function<String, String> getConfiguration = key -> configurationService.get(String.class, key)
        .orElseThrow(() -> new BusinessException("阿里云语音通知配置错误: " + key + " 为空"));

    String accessKeyId = getConfiguration.apply("notification.aliyun.call.accessKeyId");
    String accessKeySecret = getConfiguration.apply("notification.aliyun.call.accessKeySecret");
    String endpoint = getConfiguration.apply("notification.aliyun.call.endpoint");

    try {
      Config config = new Config()
          .setAccessKeyId(accessKeyId)
          .setAccessKeySecret(accessKeySecret)
          .setEndpoint(endpoint);

      return new Client(config);
    } catch (Exception e) {
      throw new BusinessException("初始化阿里云客户端失败: " + e.getMessage(), e);
    }
  }
}
