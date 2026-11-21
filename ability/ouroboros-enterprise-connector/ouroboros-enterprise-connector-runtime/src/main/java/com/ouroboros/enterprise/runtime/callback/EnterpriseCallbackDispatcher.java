package com.ouroboros.enterprise.runtime.callback;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.enterprise.callback.EnterpriseCallbackProtocol;
import com.ouroboros.enterprise.callback.EnterpriseCallbackRequest;
import com.ouroboros.enterprise.callback.EnterpriseCallbackSubscriber;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.ValidationException;

/** Only indexes installed protocol/subscriber contributions. Durable state stays in each domain. */
public final class EnterpriseCallbackDispatcher {
  private static final Logger logger = LoggerFactory.getLogger(EnterpriseCallbackDispatcher.class);
  private final EnterpriseConnectionCatalog connections;
  private final Map<String, EnterpriseCallbackProtocol> protocols;
  private final List<EnterpriseCallbackSubscriber> subscribers;

  public EnterpriseCallbackDispatcher(EnterpriseConnectionCatalog connections, List<EnterpriseCallbackProtocol> protocols,
      List<EnterpriseCallbackSubscriber> subscribers) {
    this.connections = connections;
    if (protocols.stream().anyMatch(protocol -> protocol.provider() == null || protocol.provider().isBlank())) {
      throw new ValidationException("企业回调协议声明无效");
    }
    this.protocols = protocols.stream().collect(Collectors.toUnmodifiableMap(EnterpriseCallbackProtocol::provider,
        Function.identity(), (first, second) -> { throw new ValidationException("企业回调协议重复注册: " + first.provider()); }));
    Set<String> identities = new HashSet<>();
    for (var subscriber : subscribers) {
      if (subscriber.subscriberId() == null || subscriber.subscriberId().isBlank()
          || !identities.add(subscriber.subscriberId()) || subscriber.provider() == null
          || subscriber.provider().isBlank() || subscriber.eventTypes() == null || subscriber.eventTypes().isEmpty()
          || subscriber.eventTypes().stream().anyMatch(type -> type == null || type.isBlank() || type.contains("*"))) {
        throw new ValidationException("企业回调订阅声明无效或重复");
      }
      if (!this.protocols.containsKey(subscriber.provider())) { throw new ValidationException("企业回调订阅缺少协议插件"); }
    }
    this.subscribers = List.copyOf(subscribers);
  }

  public EnterpriseCallbackProtocol.Response receive(String connectionId, EnterpriseCallbackRequest request) {
    var connection = connections.resolve(connectionId).orElseThrow(() -> new ValidationException("企业连接不存在"));
    var protocol = protocols.get(connection.provider());
    if (protocol == null) { throw new ValidationException("企业回调协议未安装"); }
    var callback = protocol.verify(connection, request);
    var current = connections.requireActive(connectionId);
    if (!Objects.equals(current.revision(), connection.revision())) {
      throw new BusinessException("企业连接已变化，请重投回调");
    }
    for (var event : callback.events()) {
      if (!connectionId.equals(event.connectionId()) || !connection.provider().equals(event.provider())
          || event.eventId() == null || event.eventId().isBlank() || event.eventType() == null || event.eventType().isBlank()
          || !com.ouroboros.enterprise.callback.VerifiedEnterpriseEvent.hash(event.payload()).equals(event.payloadHash())) {
        throw new ValidationException("回调协议返回无效事件");
      }
    }
    for (var event : callback.events()) {
      var matching = subscribers.stream().filter(subscriber -> subscriber.provider().equals(event.provider())
          && subscriber.eventTypes().contains(event.eventType())).toList();
      if (matching.isEmpty()) {
        logger.info("Verified enterprise callback ignored: provider={}, connectionId={}, eventType={}",
            event.provider(), connectionId, event.eventType());
      }
      for (var subscriber : matching) {
        if (Thread.currentThread().isInterrupted()) { throw new BusinessException("企业回调受理已中断"); }
        subscriber.receive(event);
      }
    }
    return callback.response();
  }
}
