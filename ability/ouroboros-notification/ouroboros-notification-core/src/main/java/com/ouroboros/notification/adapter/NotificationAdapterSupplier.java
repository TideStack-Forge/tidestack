package com.ouroboros.notification.adapter;

/**
 * @author liansz
 */
public interface NotificationAdapterSupplier {

  /** SPI descriptors remain stateless; execution resolves this type from the current container. */
  default java.util.Optional<Class<? extends NotificationAdapterSupplier>> managedSupplierType() {
    return java.util.Optional.empty();
  }

  default Class<? extends NotificationChannelOptions> getChannelOptionsClass() {
    var adapter = getAdapter();
    return adapter == null ? null : adapter.getChannelOptionsClass();
  }

  default Class<? extends NotificationChannelSchema> getChannelSchemaClass() {
    var adapter = getAdapter();
    return adapter == null ? null : adapter.getChannelSchemaClass();
  }

  default java.util.Optional<com.ouroboros.notification.employee.NotificationChannelRecipients> getEmployeeRecipients() {
    return java.util.Optional.empty();
  }

  default java.util.Optional<NotificationChannelManagement> getManagement() {
    return java.util.Optional.empty();
  }

  /** Pure checks only: implementations must not send, persist, or contact a provider. */
  default java.util.List<NotificationChannelManagement.Problem> validateDefinition(
      java.util.Map<String, Object> schema, java.util.Map<String, Object> options) {
    return java.util.List.of();
  }

  /**
   * 获取名称
   *
   * @return
   */
  String getName();

  /**
   * 获取适配器
   *
   * @param <D>
   * @param <O>
   * @return
   */
  <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter();
}
