package com.ouroboros.notification.call.center;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.notification.adapter.NotificationAdapterCenter;
import com.ouroboros.notification.call.adapter.AbstractCallNotificationAdapter;

/**
 * @author liansz
 */
public final class CallNotificationAdapterCenter {

  private CallNotificationAdapterCenter() {
  }

  public static Optional<AbstractCallNotificationAdapter<?, ?>> getAdapter(String provider) {
    return Optional.ofNullable(getAllAdapter().get(provider));
  }

  public static Optional<String> getProvider(AbstractCallNotificationAdapter<?, ?> adapter) {
    if (adapter == null) {
      return Optional.empty();
    }
    return getAllAdapter().entrySet().stream()
        .filter(entry -> Objects.equals(entry.getValue(), adapter))
        .map(Map.Entry::getKey)
        .findFirst();
  }

  public static Map<String, AbstractCallNotificationAdapter<?, ?>> getAllAdapter() {
    return NotificationAdapterCenter.getAllAdapter().entrySet().stream()
        .filter(entry -> entry.getValue() instanceof AbstractCallNotificationAdapter<?, ?>)
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> (AbstractCallNotificationAdapter<?, ?>) entry.getValue()));
  }
}
