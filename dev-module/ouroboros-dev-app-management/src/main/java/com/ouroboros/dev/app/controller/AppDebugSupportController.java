package com.ouroboros.dev.app.controller;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.dev.app.config.DevAppStompCompensationRegistrar;
import com.ouroboros.dev.app.service.AppLogCacheService;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.MQConsumer;

@RequestMapping("/api/debug-support")
@RestController
public class AppDebugSupportController {
  private final DevAppStompCompensationRegistrar stompCompensationRegistrar;
  private final AppLogCacheService appLogCacheService;

  private static final String APP_ID_HEADER = "amqp_appId";
  private static final String RECEIVED_ROUTING_KEY_HEADER = "amqp_receivedRoutingKey";
  private static final String LOG_BINDING_ROUTING_KEY = "app.#.profile.#.log";
  private static final String LOG_ROUTING_KEY_PREFIX = "app.";
  private static final String PROFILE_ROUTING_KEY_MARKER = ".profile.";
  private static final String LOG_ROUTING_KEY_SUFFIX = ".log";

  private MQConsumer logChannel;

  private final Map<String, String> colorMap = new HashMap<>() {{
    put("[ERROR]", "\u001B[31m");
    put("[WARN ]", "\u001B[33m");
    put("[INFO ]", "");
    put("[DEBUG]", "\u001B[36m");
    put("[TRACE]", "\u001B[90m");
  }};

  public AppDebugSupportController(
      DevAppStompCompensationRegistrar stompCompensationRegistrar,
      AppLogCacheService appLogCacheService) {
    this.stompCompensationRegistrar = stompCompensationRegistrar;
    this.appLogCacheService = appLogCacheService;
  }

  @Autowired
  void initMq(MQBridge bridge) {
    logChannel = bridge.bindingConsumer("app-debug-log")
        .destination("ouroboros.app.log")
        .extraProp("bindingRoutingKey", LOG_BINDING_ROUTING_KEY)
        .extraProp("declareExchange", true)
        .extraProp("exchangeType", "topic")
        .build();
    logChannel.subscribe(this::writeLog);
  }

  void writeLog(Message<?> message) {
    var appName = headerValue(message, APP_ID_HEADER);
    var routingKey = headerValue(message, RECEIVED_ROUTING_KEY_HEADER);
    var username = resolveUsername(routingKey, appName);
    if (StringUtils.isBlank(appName) || StringUtils.isBlank(username)) {
      return;
    }
    
    var msg = new String((byte[]) message.getPayload(), StandardCharsets.UTF_8);
    var prefix = msg.length() >= 7 ? msg.substring(0, 7) : msg;
    var color = colorMap.getOrDefault(prefix, "");
    var finalMsg = color.isEmpty() ? msg : Stream.of(msg.split("\n"))
        .map(line -> color + line)
        .collect(Collectors.joining("\n")) + "\n";
    
    appLogCacheService.addLog(username, appName, finalMsg);
    stompCompensationRegistrar.appLogsChannel().sendToUser(
        username,
        Collections.singletonMap("appName", appName),
        finalMsg);
  }

  private String headerValue(Message<?> message, String headerName) {
    Object value = message.getHeaders().get(headerName);
    return value == null ? null : value.toString();
  }

  private String resolveUsername(String routingKey, String appName) {
    if (StringUtils.isBlank(routingKey) || StringUtils.isBlank(appName)) {
      return null;
    }
    String profilePrefix = LOG_ROUTING_KEY_PREFIX + appName + PROFILE_ROUTING_KEY_MARKER;
    if (routingKey.startsWith(profilePrefix) && routingKey.endsWith(LOG_ROUTING_KEY_SUFFIX)) {
      var username = routingKey.substring(profilePrefix.length(), routingKey.length() - LOG_ROUTING_KEY_SUFFIX.length());
      return StringUtils.isBlank(username) ? null : username;
    }
    return null;
  }
}
