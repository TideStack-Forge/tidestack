package com.ouroboros.notification.wechat.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.wechat.adapter.WechatNotificationChannelOptions;
import com.ouroboros.notification.wechat.pojo.vo.MessageBody;
import com.ouroboros.notification.wechat.service.WechatService;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
@Service
public class WechatServiceImpl implements WechatService {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  /** 微信开放平台端点（厂商固定地址；占位符在调用处替换）。 */
  private static final String OAUTH_ACCESS_TOKEN_URL =
      "https://api.weixin.qq.com/sns/oauth2/access_token?appid=${APP_ID}&secret=${SECRET}&code=${CODE}&grant_type=authorization_code";

  private static final String SEND_TEMPLATE_URL =
      "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=${ACCESS_TOKEN}";

  private static final String GET_TOKEN_URL =
      "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=${APP_ID}&secret=${SECRET}";

  @Autowired
  ConfigurationService configurationService;

  private String accessToken;

  @Override
  public String getWechatId(String code, String type) {
    String appId = getConfiguration("notification.wechat.appId");
    String secret = getConfiguration("notification.wechat.secret");

    String url = OAUTH_ACCESS_TOKEN_URL
        .replace("${APP_ID}", appId)
        .replace("${SECRET}", secret)
        .replace("${CODE}", code);

    Map<String, Object> response = null;
    RuntimeException exception = null;
    try {
      response = Json.toMap(new RestTemplate().getForObject(url, String.class));
      Integer errCode = (Integer) response.get("errcode");
      if (errCode == null || errCode != 0) {
        throw new BusinessException((String) response.get("errmsg"));
      }

      return (String) response.get(type);
    } catch (Exception e) {
      exception = e instanceof RuntimeException ? (RuntimeException) e : new BusinessException("微信用户标识获取失败", e);
      throw exception;
    } finally {
      logger.debug("get wechatId url {}, response {}",
          url,
          exception != null ? exception.getMessage() : Json.toJsonString(response)
      );
    }
  }

  @Override
  public void sendTemplateMessage(String wechatId, String templateId,
                                  Map<String, Object> payload, WechatNotificationChannelOptions channelOptions) {
    if (accessToken == null) {
      accessToken = getAccessToken();
    }

    String url = SEND_TEMPLATE_URL
        .replace("${ACCESS_TOKEN}", accessToken);

    MessageBody body = buildRequest(wechatId, templateId, payload, channelOptions);
    Map<String, Object> response = null;
    RuntimeException exception = null;
    try {
      response = Json.toMap(new RestTemplate().postForObject(url, body, String.class));
      Integer errCode = (Integer) response.get("errcode");
      if (errCode == null || errCode != 0) {
        throw new BusinessException((String) response.get("errmsg"));
      }
    } catch (Exception e) {
      exception = e instanceof RuntimeException ? (RuntimeException) e : new BusinessException("微信模板消息发送失败", e);
      throw exception;
    } finally {
      logger.error("send url {}, request {}, response: {}",
          url,
          Json.toJsonString(body),
          exception != null ? exception.getMessage() : Json.toJsonString(response)
      );
    }
  }

  @Scheduled(fixedDelay = 30 * 60 * 1000)
  public void refreshAccessToken() {
    if (accessToken == null) {
      return;
    }

    accessToken = getAccessToken();
  }

  /**
   * 获取access-token
   *
   * @return
   */
  protected String getAccessToken() {
    String appId = getConfiguration("notification.wechat.appId");
    String secret = getConfiguration("notification.wechat.secret");

    String url = GET_TOKEN_URL
        .replace("${APP_ID}", appId)
        .replace("${SECRET}", secret);

    Map<String, Object> response = null;
    RuntimeException exception = null;
    try {
      response = Json.toMap(new RestTemplate().getForObject(url, String.class));
      Integer errCode = (Integer) response.get("errcode");
      if (errCode == null || errCode != 0) {
        throw new BusinessException((String) response.get("errmsg"));
      }

      return (String) response.get("access_token");
    } catch (Exception e) {
      exception = e instanceof RuntimeException ? (RuntimeException) e : new BusinessException("微信 access-token 获取失败", e);
      throw exception;
    } finally {
      logger.debug("get access-token url {}, response {}",
          url,
          exception != null ? exception.getMessage() : Json.toJsonString(response)
      );
    }
  }

  /**
   * 获取配置
   *
   * @param key
   * @return
   */
  private String getConfiguration(String key) {
    return configurationService.get(String.class, key)
        .orElseThrow(() -> new BusinessException("微信模版通知配置错误: " + key + " 为空"));
  }

  /**
   * 构建请求参数
   *
   * @param toUser
   * @param templateId
   * @param payload
   * @return
   */
  private MessageBody buildRequest(String toUser, String templateId,
                                   Map<String, Object> payload, WechatNotificationChannelOptions channelOptions) {
    Map<String, String> payloadColor = channelOptions.getPayloadColor();
    Map<String, Map<String, Object>> data = payload.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                  Map<String, Object> element = new HashMap<>();
                  element.put("value", entry.getValue());
                  if (!CollectionUtils.isEmpty(payloadColor) && payloadColor.containsKey(entry.getKey())) {
                    element.put("color", payloadColor.get(entry.getKey()));
                  }

                  return element;
                },
                (v1, v2) -> v2
            )
        );

    MessageBody body = new MessageBody();
    body.setTouser(toUser);
    body.setTemplate_id(templateId);
    body.setUrl(channelOptions.getUrl());
    body.setMiniprogram(channelOptions.getMiniprogram());
    body.setData(data);
    body.setClient_msg_id(channelOptions.getClientMsgId());
    return body;
  }
}
