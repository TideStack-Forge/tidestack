package com.ouroboros.dev.app.collaboration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.websocket.StompCompensationRegistrar;
import com.ouroboros.websocket.StompDestinationAuthorizer;
import com.ouroboros.dev.app.collaboration.CollaborativeDocumentService.Document;

@Component
public class CollaborationGateway implements StompDestinationAuthorizer {
  public static final String DESTINATION = "/user/queue/app/{appName}/collaboration/{type}";
  private static final Pattern ROUTE = Pattern.compile(
      "^(?:/app)?(?:/queue/init)?/user/queue/app/([^/]+)/collaboration/([^/]+)(?:/[^/]+)?$");
  private static final Logger logger = LoggerFactory.getLogger(CollaborationGateway.class);
  private final CollaborativeDocumentService documents;
  private final AppEditAccessEvaluator access;
  private final StompCompensationRegistrar registrar;
  private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
  private StompCompensationRegistrar.UserTopicChannel channel;

  private record Subscription(String app, String type, String user) {}
  public record Bootstrap(String kind, boolean writable, List<Document> documents) {}
  public record Changed(String kind, Document document) {}

  public CollaborationGateway(CollaborativeDocumentService documents, AppEditAccessEvaluator access,
      StompCompensationRegistrar registrar) {
    this.documents = documents;
    this.access = access;
    this.registrar = registrar;
  }

  @PostConstruct
  public void register() {
    channel = registrar.userTopic(DESTINATION).initialData(context -> {
      var app = context.pathVariable("appName");
      var type = context.pathVariable("type");
      requireView(app, context.getUser());
      return List.of(new Bootstrap("bootstrap", access.canEdit(app, context.getUser()), documents.list(app, type)));
    }).done();
  }

  @Override
  public void authorize(StompHeaderAccessor frame) {
    if (frame != null && frame.getCommand() == StompCommand.UNSUBSCRIBE) {
      subscriptions.remove(frame.getSessionId() + ":" + frame.getSubscriptionId());
    }
    if (frame == null || frame.getDestination() == null) return;
    var matcher = ROUTE.matcher(frame.getDestination());
    if (!matcher.matches()) return;
    var principal = frame.getUser();
    requireView(matcher.group(1), principal == null ? null : principal.getName());
    if (frame.getCommand() == StompCommand.SUBSCRIBE && frame.getDestination().equals(
        "/user/queue/app/" + matcher.group(1) + "/collaboration/" + matcher.group(2))) {
      subscriptions.put(frame.getSessionId() + ":" + frame.getSubscriptionId(),
          new Subscription(matcher.group(1), matcher.group(2), principal.getName()));
    }
    if (frame.getCommand() == StompCommand.SEND && !frame.getDestination().endsWith("/init")) {
      throw new BusinessException("协同变更须通过已授权的文档操作入口提交");
    }
  }

  public void requireView(String app, String user) {
    if (user == null || !access.canView(app, user)) throw new BusinessException("无权查看应用协同文档");
  }

  public void requireEdit(String app, String user) {
    if (user == null || !access.canEdit(app, user)) throw new BusinessException("无权编辑应用协同文档");
  }

  public boolean writable(String app, String user) { return access.canEdit(app, user); }

  public void publish(String app, String type, Document document) {
    subscriptions.values().stream().filter(subscription -> subscription.app().equals(app) && subscription.type().equals(type))
        .map(Subscription::user).distinct().filter(user -> access.canView(app, user)).forEach(user -> {
          try {
            channel.sendToUser(user, Map.of("appName", app, "type", type), new Changed("document", document));
          } catch (RuntimeException failure) {
            // Persistence has succeeded. Readers also reconcile on reconnect and periodically.
            logger.warn("协同通知发送失败，文档将在重新同步时恢复 app={} type={} id={}", app, type, document.id(), failure);
          }
        });
  }

  @EventListener
  public void disconnected(SessionDisconnectEvent event) {
    subscriptions.keySet().removeIf(key -> key.startsWith(event.getSessionId() + ":"));
  }
}
