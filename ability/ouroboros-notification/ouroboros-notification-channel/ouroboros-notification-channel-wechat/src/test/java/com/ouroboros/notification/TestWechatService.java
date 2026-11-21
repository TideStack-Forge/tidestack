package com.ouroboros.notification;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.wechat.adapter.WechatNotificationChannelOptions;
import com.ouroboros.notification.wechat.service.impl.WechatServiceImpl;

class TestWechatService {

  private WechatServiceImpl wechatService;

  @BeforeEach
  void setUp() {
    wechatService = new WechatServiceImpl();
    ReflectionTestUtils.setField(wechatService, "configurationService", new EmptyConfigurationService());
  }

  @Test
  void getWechatId_shouldRejectMissingAppIdConfiguration() {
    BusinessException exception = assertThrows(BusinessException.class,
        () -> wechatService.getWechatId("code", "openId"));

    assertTrue(exception.getMessage().contains("notification.wechat.appId"));
  }

  @Test
  void sendTemplateMessage_shouldRejectMissingAppIdConfigurationBeforeRemoteCall() {
    BusinessException exception = assertThrows(BusinessException.class,
        () -> wechatService.sendTemplateMessage(
            "openid",
            "template",
            Collections.singletonMap("first", "hello"),
            new WechatNotificationChannelOptions()
        ));

    assertTrue(exception.getMessage().contains("notification.wechat.appId"));
  }

  private static class EmptyConfigurationService implements ConfigurationService {
    @Override
    public <T> Optional<T> get(String key) {
      return Optional.empty();
    }

    @Override
    public Map<String, Object> getValues(ConfigScope scope) {
      return Collections.emptyMap();
    }
  }
}
