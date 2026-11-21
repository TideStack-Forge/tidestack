package com.ouroboros.notification.aliyun.sms.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.aliyun.sms.adapter.AliyunSmsNotificationChannelOptions;
import com.ouroboros.notification.aliyun.sms.service.AliyunSmsService;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
@Service
public class AliyunSmsServiceImpl implements AliyunSmsService {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  ConfigurationService configurationService;

  @Override
  public void sendSms(List<String> mobiles, String templateCode,
                      Map<String, Object> payload, AliyunSmsNotificationChannelOptions channelOptions) {
    SendSmsRequest request = buildRequest(mobiles, templateCode, payload, channelOptions);
    SendSmsResponse response = null;
    RuntimeException exception = null;
    try {
      response = buildClient().sendSms(request);
    } catch (Exception e) {
      exception = e instanceof RuntimeException ? (RuntimeException) e : new BusinessException("阿里云短信发送失败", e);
      throw exception;
    } finally {
      if (!(payload instanceof com.ouroboros.notification.SensitiveNotificationPayload)) { logger.debug("send request {}, response {}",
          Json.toJsonString(request),
          exception != null ? exception.getMessage() : Json.toJsonString(response)
      );
      }
    }

    SendSmsResponseBody body = response.getBody();
    if (body == null) {
      throw new BusinessException("响应为空");
    }

    String code = body.getCode();
    if (!Objects.equals(code, "OK")) {
      throw new BusinessException(body.getMessage());
    }
  }

  /**
   * 构建请求参数
   *
   * @param mobiles
   * @param templateCode
   * @param payload
   * @param channelOptions
   */
  private SendSmsRequest buildRequest(List<String> mobiles, String templateCode,
                                      Map<String, Object> payload, AliyunSmsNotificationChannelOptions channelOptions) {
    if (ObjectUtils.isEmpty(channelOptions.getSignName())) {
      throw new BusinessException("签名未定义");
    }

    String mobileStr = mobiles.stream().map(mobile -> mobile.startsWith("+") ? mobile : "+86" + mobile).collect(Collectors.joining(","));

    SendSmsRequest request = new SendSmsRequest();
    request.setPhoneNumbers(mobileStr);
    request.setSignName(channelOptions.getSignName());
    request.setTemplateCode(templateCode);
    request.setTemplateParam(payload != null ? Json.toJsonString(payload) : null);
    request.setSmsUpExtendCode(channelOptions.getSmsUpExtendCode());
    request.setOutId(channelOptions.getOutId());
    return request;
  }

  /**
   * 构建客户端
   */
  public Client buildClient() {
    Function<String, String> getConfiguration = key -> configurationService.get(String.class, key)
        .orElseThrow(() -> new BusinessException("阿里云短信通知配置错误: " + key + " 为空"));

    String accessKeyId = getConfiguration.apply("notification.aliyun.sms.accessKeyId");
    String accessKeySecret = getConfiguration.apply("notification.aliyun.sms.accessKeySecret");
    String endpoint = getConfiguration.apply("notification.aliyun.sms.endpoint");

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
