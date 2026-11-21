package com.ouroboros.mq.runtime;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.mq.binding.MQBindingProperties;
import com.ouroboros.mq.binding.MQBindingProvider;
import com.ouroboros.util.Json;

@Component
public class LowCodeMQBindingProvider {
  private static final Logger logger = LoggerFactory.getLogger(LowCodeMQBindingProvider.class);
  private static final String MQ_BINDING_DIRECTORY = "mq-binding";
  private static final String MQ_BINDING_SUFFIX = ".mq.json";

  @Autowired
  AppContext appContext;
  List<MQBindingProperties<?>> bindings;

  public LowCodeMQBindingProvider() {
    Proxy.delegate = this;
  }

  @EventListener(OuroborosMetadataRefreshingEvent.class)
  private void refresh() {
    List<MQBindingProperties<?>> result = new ArrayList<>();
    var walkPath = Paths.get(appContext.getHomePath(), MQ_BINDING_DIRECTORY);
    if (!Files.exists(walkPath)) {
      bindings = result;
      return;
    }
    try (var steam = Files.walk(walkPath)) {
      // 获取所有文件路径
      var files = steam.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(MQ_BINDING_SUFFIX))
          .collect(Collectors.toList());

      // 解析文件
      files.stream()
          .map(path -> {
            return Try.of(() -> String.join("\n", Files.readAllLines(path)))
                .toEither()
                .mapLeft(e -> new RuntimeException("读取消息队列绑定定义 " + path + " 失败", e))
                .flatMap(fileContent -> {
                  var content = fileContent.trim();
                  if (content.isEmpty()) {
                    return Either.<RuntimeException, MQBindingProperties<?>>left(new RuntimeException("消息队列绑定定义 " + path + " 为空"));
                  }
                  return Json.tryToBean(MQBindingProperties.class, content)
                      .toEither()
                      .mapLeft(e -> new RuntimeException("解析消息队列绑定定义 " + path + " 失败", e));
                });
          })
          .forEach(either -> either
              .peek(result::add)
              .peekLeft(e -> logger.error(e.getMessage(), e)));
    } catch (Exception e) {
      logger.error("刷新消息队列绑定定义时发生错误", e);
    }
    bindings = result;
  }

  public List<MQBindingProperties<?>> getAllBindings() {
    if (bindings == null) {
      refresh();
    }
    return bindings;
  }

  public static class Proxy implements MQBindingProvider {
    private static LowCodeMQBindingProvider delegate;

    @Override
    public List<MQBindingProperties<?>> getAllBindings() {
      if (delegate == null) {
        return Collections.emptyList();
      }
      return delegate.getAllBindings();
    }
  }
}
