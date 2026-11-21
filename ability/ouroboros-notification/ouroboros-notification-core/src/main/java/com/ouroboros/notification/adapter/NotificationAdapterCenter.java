package com.ouroboros.notification.adapter;

import java.util.Map;
import java.util.Optional;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import com.ouroboros.util.SPI;

/**
 * @author liansz
 */
public class NotificationAdapterCenter {

  public static <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter(String channel) {
    return findSupplier(channel)
        .orElseThrow(() -> new RuntimeException("未找到渠道：" + channel))
        .getAdapter();
  }

  public static Optional<NotificationAdapterSupplier> findSupplier(String channel) {
    return Optional.ofNullable(getAllSuppliers().get(channel));
  }

  public static Optional<NotificationAdapterSupplier> findSupplier(String channel, Collection<NotificationAdapterSupplier> managed) {
    return Optional.ofNullable(getAllSuppliers(managed).get(channel));
  }

  public static Map<String, NotificationAdapterSupplier> getAllSuppliers() {
    return SPI.getCachedSortedServiceStream(NotificationAdapterSupplier.class).collect(
        Collectors.toMap(NotificationAdapterSupplier::getName, supplier -> supplier, (first, next) -> first));
  }

  /** Binds only declared managed descriptors, retaining the original SPI priority for all other suppliers. */
  public static Map<String, NotificationAdapterSupplier> getAllSuppliers(Collection<NotificationAdapterSupplier> managed) {
    Map<String, NotificationAdapterSupplier> suppliers = new LinkedHashMap<>(getAllSuppliers());
    suppliers.replaceAll((name, descriptor) -> {
      var type = descriptor.managedSupplierType();
      if (type.isEmpty()) { return descriptor; }
      var candidates = managed.stream().filter(type.get()::isInstance).filter(supplier -> name.equals(supplier.getName())).toList();
      if (candidates.size() != 1) { throw new com.ouroboros.exception.ValidationException("通知渠道托管装配缺失或重复: " + name); }
      return candidates.getFirst();
    });
    return Map.copyOf(suppliers);
  }

  public static <D extends NotificationChannelSchema, O extends NotificationChannelOptions> Map<String, NotificationAdapter<D, O>> getAllAdapter() {
    return getAllSuppliers().entrySet().stream().filter(entry -> entry.getValue().managedSupplierType().isEmpty()).collect(
        Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().<D, O>getAdapter()));
  }

  public static <D extends NotificationChannelSchema, O extends NotificationChannelOptions> Map<String, NotificationAdapter<D, O>> getAllAdapter(
      Collection<NotificationAdapterSupplier> managed) {
    return getAllSuppliers(managed).entrySet().stream().collect(
        Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().<D, O>getAdapter()));
  }
}
