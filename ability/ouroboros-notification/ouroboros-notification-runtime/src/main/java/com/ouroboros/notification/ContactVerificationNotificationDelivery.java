package com.ouroboros.notification;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.adapter.NotificationAdapterCenter;
import com.ouroboros.notification.template.NotificationTemplateCenter;
import com.ouroboros.security.profile.ContactVerificationDelivery;

/** Reuses the normal template, adapter and dispatch result path without employee address lookup. */
@Component
public final class ContactVerificationNotificationDelivery implements ContactVerificationDelivery {
  private final AddressNotificationDispatcher dispatcher;
  private final String emailTemplate;
  private final String phoneTemplate;
  private final String channel;
  private final String publicOrigin;

  public ContactVerificationNotificationDelivery(AddressNotificationDispatcher dispatcher,
      @Value("${ouroboros.security.profile.email-template:}") String emailTemplate,
      @Value("${ouroboros.security.profile.phone-template:}") String phoneTemplate,
      @Value("${ouroboros.security.profile.phone-channel:aliyun-sms}") String channel,
      @Value("${ouroboros.security.profile.public-origin:}") String publicOrigin) {
    this.dispatcher = dispatcher; this.emailTemplate = emailTemplate; this.phoneTemplate = phoneTemplate;
    this.channel = channel; this.publicOrigin = publicOrigin;
  }

  public boolean supports(String kind) {
    if (!kind.equals("email") && !kind.equals("phone")) { return false; }
    if (kind.equals("email") && !validOrigin()) { return false; }
    String template = kind.equals("email") ? emailTemplate : phoneTemplate;
    String name = kind.equals("email") ? "email" : channel;
    return NotificationAdapterCenter.getAllAdapter().containsKey(name)
        && NotificationTemplateCenter.getTemplate(template).filter(item -> item.isEnabled() && item.getChannel() != null
            && item.getChannel().containsKey(name)).isPresent();
  }

  private boolean validOrigin() {
    try {
      URI origin = URI.create(publicOrigin);
      return origin.getHost() != null && origin.getQuery() == null && origin.getFragment() == null
          && origin.getUserInfo() == null && (origin.getPath().isEmpty() || origin.getPath().equals("/"))
          && (origin.getScheme().equals("https") || origin.getScheme().equals("http")
              && List.of("localhost", "127.0.0.1", "[::1]").contains(origin.getHost()));
    } catch (IllegalArgumentException error) { return false; }
  }

  public void deliver(Message message) {
    if (!supports(message.kind())) { throw new BusinessException(503, "联系方式验证投递能力不可用"); }
    boolean email = message.kind().equals("email");
    String link = publicOrigin.replaceAll("/$", "") + "/personal-settings/profile#contactRequestId=" + message.requestId()
        + "&ticket=" + message.proof();
    var payload = new SensitiveNotificationPayload(email ? Map.of("confirmationUrl", link) : Map.of("code", message.proof()));
    var options = new DispatchOptions(); options.setChannels(List.of(email ? "email" : channel));
    var result = dispatcher.sendWithResult(Map.of(email ? "email" : channel, List.of(message.address())),
        email ? emailTemplate : phoneTemplate, payload, options);
    if (!result.success()) { throw new BusinessException(503, "联系方式验证消息投递失败"); }
  }
}
